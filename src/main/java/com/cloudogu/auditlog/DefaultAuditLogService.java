/*
 * MIT License
 *
 * Copyright (c) 2020-present Cloudogu GmbH and Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.cloudogu.auditlog;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import sonia.scm.auditlog.EntryCreationContext;
import sonia.scm.plugin.Extension;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.cloudogu.auditlog.EntryContextResolver.resolveAction;
import static com.cloudogu.auditlog.EntryContextResolver.resolveEntityName;
import static com.cloudogu.auditlog.EntryContextResolver.resolveLabels;
import static com.cloudogu.auditlog.Filters.resolveAppliedFilters;
import static com.cloudogu.auditlog.Filters.setFilterValues;
import static com.cloudogu.auditlog.SqlQueryGenerator.createCountQuery;
import static com.cloudogu.auditlog.SqlQueryGenerator.createEntriesQuery;
import static com.cloudogu.auditlog.SqlQueryGenerator.createLabelsQuery;

@Slf4j
@Extension
@Singleton
public class DefaultAuditLogService implements AuditLogService {

  private final AuditLogDatabase database;
  private final Executor executor;
  private final AuditEntryGenerator entryGenerator = new AuditEntryGenerator();


  @Inject
  public DefaultAuditLogService(AuditLogDatabase database) {
    this(
      database,
      // Since h2 is single threaded, we use the executor to serialize the write requests
      Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder()
          .setNameFormat("AuditLogAsyncExecutor-%d")
          .build()
      )
    );
  }

  @VisibleForTesting
  @SuppressWarnings("java:S2115")
    // We don't need a password here. This database contains no secrets.
  DefaultAuditLogService(AuditLogDatabase database, Executor executor) {
    this.database = database;
    this.executor = executor;
  }

  @Override
  public void createEntry(EntryCreationContext<?> context) {
    String username = getUsername();
    executor.execute(() -> createDBEntry(username, context));
  }

  private void createDBEntry(String username, EntryCreationContext<?> context) {
    Instant timestamp = Instant.now();
    String entityName = resolveEntityName(context);
    String action = resolveAction(context);
    String[] labels = resolveLabels(context);
    String entry = entryGenerator.generate(context, timestamp, username, action, entityName, labels);
    try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(
      "INSERT INTO AUDITLOG(TIMESTAMP_, ENTITY, USERNAME, ACTION_, ENTRY) VALUES (?, ?, ?, ?, ?)",
      Statement.RETURN_GENERATED_KEYS)
    ) {
      statement.setTimestamp(1, new Timestamp(timestamp.toEpochMilli()));
      statement.setString(2, entityName);
      statement.setString(3, username);
      statement.setString(4, action);
      if (!Strings.isNullOrEmpty(entry)) {
        statement.setString(5, entry);
        statement.executeUpdate();

        statement.getGeneratedKeys().next();
        createLabelsForNewEntry(statement.getGeneratedKeys().getInt(1), labels);
      }
    } catch (Exception e) {
      log.error("Could not create new entry for audit log for entity '{}' with action {}: {}", entityName, action, entry, e);
    }
  }

  public Collection<LogEntry> getEntries(AuditLogFilterContext filterContext) {
    PermissionChecker.checkReadAuditLog();
    return getLogEntries(filterContext);
  }

  @VisibleForTesting
  List<LogEntry> getLogEntries(AuditLogFilterContext filterContext) {
    List<Filters.AppliedFilter> appliedFilters = resolveAppliedFilters(filterContext);
    String query = createEntriesQuery(filterContext, appliedFilters);
    try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
      setFilterValues(statement, appliedFilters);
      ResultSet resultSet = statement.executeQuery();
      List<LogEntry> entries = new ArrayList<>();
      while (resultSet.next()) {
        addSingleEntry(entries, resultSet);
      }
      return entries;
    } catch (SQLException e) {
      throw new AuditLogException("Failed to read audit log", e);
    }
  }

  @Override
  public int getTotalEntries(AuditLogFilterContext filterContext) {
    PermissionChecker.checkReadAuditLog();
    List<Filters.AppliedFilter> appliedFilters = resolveAppliedFilters(filterContext);
    String query = createCountQuery(filterContext, appliedFilters);
    try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
      setFilterValues(statement, appliedFilters);
      ResultSet resultSet = statement.executeQuery();
      resultSet.next();
      return resultSet.getInt("total");
    } catch (SQLException e) {
      throw new AuditLogException("Failed to count audit log entries", e);
    }
  }

  @Override
  public Set<String> getLabels() {
    try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
      String query = createLabelsQuery();
      ResultSet resultSet = statement.executeQuery(query);

      Set<String> labels = new HashSet<>();
      while (resultSet.next()) {
        labels.add(resultSet.getString("LABEL"));
      }
      return labels;
    } catch (SQLException e) {
      throw new AuditLogException("Failed to collect audit log labels", e);
    }
  }

  private static String getUsername() {
    try {
      Object principal = SecurityUtils.getSubject().getPrincipal();
      return principal == null ? null : principal.toString();
    } catch (UnavailableSecurityManagerException e) {
      return null;
    }
  }

  private void createLabelsForNewEntry(int id, String[] labels) throws SQLException {
    try (Connection connection = database.getConnection();
         PreparedStatement statement = connection.prepareStatement("INSERT INTO LABELS(AUDIT, LABEL) VALUES (?, ?)")) {
      for (String label : labels) {
        statement.setInt(1, id);
        statement.setString(2, label);
        statement.executeUpdate();
      }
    }
  }

  private void addSingleEntry(List<LogEntry> entries, ResultSet resultSet) throws SQLException {
    LogEntry logEntry = new LogEntry();
    logEntry.setEntity(resultSet.getString("ENTITY"));
    logEntry.setUser(resultSet.getString("USERNAME"));
    logEntry.setAction(resultSet.getString("ACTION_"));
    logEntry.setEntry(resultSet.getString("ENTRY"));
    logEntry.setTimestamp(resultSet.getTimestamp("TIMESTAMP_").toInstant());

    entries.add(logEntry);
  }
}
