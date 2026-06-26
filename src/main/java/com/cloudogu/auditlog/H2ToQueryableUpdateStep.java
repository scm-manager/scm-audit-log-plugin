/*
 * Copyright (c) 2020 - present Cloudogu GmbH
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/.
 */

package com.cloudogu.auditlog;

import com.google.common.annotations.VisibleForTesting;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.h2.jdbcx.JdbcConnectionPool;
import sonia.scm.SCMContextProvider;
import sonia.scm.migration.UpdateException;
import sonia.scm.migration.UpdateStep;
import sonia.scm.plugin.Extension;
import sonia.scm.store.QueryableMutableStore;
import sonia.scm.store.QueryableStore;
import sonia.scm.util.IOUtil;
import sonia.scm.version.Version;

import java.io.File;
import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Extension
@Slf4j
public class H2ToQueryableUpdateStep implements UpdateStep {

  private final String h2DbPath;
  private final AuditLogDaoStoreFactory auditLogDaoStoreFactory;
  private final LabelDaoStoreFactory labelDaoStoreFactory;

  private final Set<String> foundLabels = new HashSet<>();

  @Inject
  H2ToQueryableUpdateStep(SCMContextProvider contextProvider,
                          AuditLogDaoStoreFactory auditLogDaoStoreFactory,
                          LabelDaoStoreFactory labelDaoStoreFactory) {
    this(contextProvider.getBaseDirectory() + "/audit-log", auditLogDaoStoreFactory, labelDaoStoreFactory);
  }

  H2ToQueryableUpdateStep(String h2DbPath,
                          AuditLogDaoStoreFactory auditLogDaoStoreFactory,
                          LabelDaoStoreFactory labelDaoStoreFactory) {
    if (new File(h2DbPath).exists() && new File(h2DbPath).isDirectory()) {
      this.h2DbPath = h2DbPath;
    } else {
      this.h2DbPath = null;
    }
    this.auditLogDaoStoreFactory = auditLogDaoStoreFactory;
    this.labelDaoStoreFactory = labelDaoStoreFactory;
  }

  @Override
  public void doUpdate() throws Exception {
    if (h2DbPath == null) {
      // no database to migrate
      return;
    }

    JdbcConnectionPool pool = JdbcConnectionPool.create("jdbc:h2:" + h2DbPath + "/audit-log", null, null);
    try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
      migrateLogEntries(statement);
      writeLabels();
      verifyMigration(statement);
    }
    pool.dispose();

    IOUtil.deleteSilently(new File(h2DbPath));
  }

  private void verifyMigration(Statement statement) throws SQLException {
    ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM AUDITLOG");
    resultSet.next();
    long oldCount = resultSet.getLong(1);
    try (QueryableStore<AuditLogDao> store = auditLogDaoStoreFactory.get()) {
      long newCount = store.query().count();
      if (oldCount != newCount) {
        throw new UpdateException(
          String.format("Expected to migrate %d audit log entries, but found %d in the new queryable store.", oldCount, newCount)
        );
      } else {
        log.info("Successfully migrated {} audit log entries from H2 database to queryable store.", oldCount);
      }
    }
  }

  private void writeLabels() {
    try (QueryableMutableStore<LabelDao> labelDaoStore = labelDaoStoreFactory.getMutable()) {
      foundLabels.forEach(
        label -> labelDaoStore.put(label, new LabelDao())
      );
    }
  }

  private void migrateLogEntries(Statement statement) throws SQLException {
    log.debug("creating index on old audit log database");
    statement.execute("create index on LABELS (audit);");
    log.debug("index on old audit log database created; reading data");
    ResultSet resultSet = statement.executeQuery(createEntriesQuery());

    log.debug("writing data to new database");
    try (QueryableMutableStore<AuditLogDao> auditLogDaoStore = auditLogDaoStoreFactory.getMutable()) {
      clearExistingData(auditLogDaoStore);
      int batchSize = 5000;
      AtomicBoolean hasMoreRecords = new AtomicBoolean(true);

      while (hasMoreRecords.get()) {
        auditLogDaoStore.transactional(() -> {
          try {
            int count = 0;
            while (count < batchSize) {
              // If there are no more entries in the ResultSet, signal the outer loop to stop
              if (!resultSet.next()) {
                hasMoreRecords.set(false);
                log.debug("finished writing data to new database");
                return true;
              }

              AuditLogDao auditLogDao = readLogEntry(resultSet);
              foundLabels.addAll(auditLogDao.getLabels());
              auditLogDaoStore.put(auditLogDao);
              count++;
            }
          } catch (SQLException e) {
            throw new UpdateException("Failed to migrate audit log entries from H2 database", e);
          }
          log.debug("wrote batch of {} entries to new database", batchSize);
          return true;
        });
      }
    }
  }

  @VisibleForTesting
  void clearExistingData(QueryableMutableStore<AuditLogDao> auditLogDaoStore) {
    auditLogDaoStore.clear();
  }

  private AuditLogDao readLogEntry(ResultSet resultSet) throws SQLException {
    LogEntry entry = createSingleEntry(resultSet);
    AuditLogDao auditLogDao = new AuditLogDao();
    auditLogDao.setTimestamp(entry.getTimestamp());
    auditLogDao.setEntityName(entry.getEntity());
    auditLogDao.setAction(entry.getAction());
    auditLogDao.setUsername(entry.getUser());
    auditLogDao.setEntry(entry.getEntry());

    List<String> labels = readLabels(resultSet);
    auditLogDao.setLabels(labels);
    return auditLogDao;
  }

  private List<String> readLabels(ResultSet resultSet) throws SQLException {
    Array labelsSqlArray = resultSet.getArray("LABELS");
    List<String> labels;
    if (labelsSqlArray != null) {
      labels = new ArrayList<>();
      Object[] labelsArray = (Object[]) labelsSqlArray.getArray();
      for (Object label : labelsArray) {
        if (label != null) {
          labels.add(label.toString());
        }
      }
    } else {
      labels = List.of();
    }
    return labels;
  }

  @Override
  public Version getTargetVersion() {
    return Version.parse("3.0.0");
  }

  @Override
  public String getAffectedDataType() {
    return "com.cloudogu.auditlog.AuditLogEntry";
  }


  private String createEntriesQuery() {
    return
      "SELECT ID, TIMESTAMP_, ENTITY, USERNAME, ACTION_, ENTRY, " +
      "    ARRAY_AGG(l.label) AS labels " +
      "FROM " +
      "    AUDITLOG a " +
      "LEFT JOIN " +
      "    LABELS l ON a.id = l.audit " +
      "GROUP BY " +
      "    a.id, a.TIMESTAMP_, a.ENTITY, a.USERNAME, a.ACTION_, a.ENTRY " +
      "ORDER BY " +
      "    a.id;";
  }

  private LogEntry createSingleEntry(ResultSet resultSet) throws SQLException {
    LogEntry logEntry = new LogEntry();
    logEntry.setEntity(resultSet.getString("ENTITY"));
    logEntry.setUser(resultSet.getString("USERNAME"));
    logEntry.setAction(resultSet.getString("ACTION_"));
    logEntry.setEntry(resultSet.getString("ENTRY"));
    logEntry.setTimestamp(resultSet.getTimestamp("TIMESTAMP_").toInstant());
    return logEntry;
  }
}
