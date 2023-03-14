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
import sonia.scm.auditlog.AuditEntry;
import sonia.scm.auditlog.AuditLogEntity;
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
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static java.util.Arrays.stream;
import static java.util.stream.Stream.concat;

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
  @SuppressWarnings("java:S2115") // We don't need a password here. This database contains no secrets.
  DefaultAuditLogService(AuditLogDatabase database, Executor executor) {
    this.database = database;
    this.executor = executor;
  }

  @Override
  public void createEntry(EntryCreationContext<?> context) {
    executor.execute(() -> createDBEntry(context));
  }

  @VisibleForTesting
  <T> void createDBEntry(EntryCreationContext<T> context) {
    Instant timestamp = Instant.now();
    String entityName = resolveEntityName(context);
    String username = SecurityUtils.getSubject().getPrincipal().toString();
    String action = resolveAction(context);
    String[] labels = resolveLabels(context);
    String entry = entryGenerator.generate(context, timestamp, username, action, entityName, labels);
    try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(
      "INSERT INTO AUDITLOG(TIMESTAMP_, ENTITY, USERNAME, ACTION_, ENTRY) VALUES (?, ?, ?, ?, ?)",
      Statement.RETURN_GENERATED_KEYS)
    ) {
      statement.setTimestamp(1, new Timestamp(timestamp.getEpochSecond()));
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
    try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
      List<LogEntry> entries = new ArrayList<>();
      String query = createEntriesQuery(filterContext);
      ResultSet resultSet = statement.executeQuery(query);
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
    try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
      String query = createCountQuery(filterContext);
      ResultSet resultSet = statement.executeQuery(query);
      resultSet.next();
      return resultSet.getInt("total");
    } catch (SQLException e) {
      throw new AuditLogException("Failed to count audit log entries", e);
    }
  }

  private <T> String[] resolveLabels(EntryCreationContext<T> context) {
    T object = getObject(context);
    String[] auditEntryAnnotationLabels;
    AuditEntry annotation = object.getClass().getAnnotation(AuditEntry.class);
    if (annotation != null) {
      auditEntryAnnotationLabels = annotation.labels();
    } else {
      auditEntryAnnotationLabels = new String[]{};
    }
    return concat(stream(auditEntryAnnotationLabels), context.getAdditionalLabels().stream())
      .distinct()
      .toArray(String[]::new);
  }

  <T> T getObject(EntryCreationContext<T> context) {
    return context.getObject() != null ? context.getObject() : context.getOldObject();
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

  private <T> String resolveEntityName(EntryCreationContext<T> context) {
    if (!Strings.isNullOrEmpty(context.getEntity())) {
      return Strings.nullToEmpty(context.getEntity());
    }

    T object = getObject(context);
    if (object instanceof AuditLogEntity) {
      return ((AuditLogEntity) object).getEntityName();
    }
    return "";
  }

  static <T> String resolveAction(EntryCreationContext<T> context) {
    if (context.getOldObject() == null) {
      return "created";
    }
    if (context.getObject() == null) {
      return "deleted";
    }
    return "modified";
  }

  private String createEntriesQuery(AuditLogFilterContext filterContext) {
    return "SELECT * FROM AUDITLOG " +
      //TODO join tables and filter for labels
      "ORDER BY ID DESC " +
      "LIMIT " + filterContext.getLimit() + " " +
      "OFFSET " + (filterContext.getPageNumber() - 1) * filterContext.getLimit() + ";";
  }

  private String createCountQuery(AuditLogFilterContext filterContext) {
    return "SELECT COUNT(*) AS total FROM AUDITLOG;";
    //TODO join tables and filter for labels
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
