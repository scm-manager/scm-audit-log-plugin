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

import lombok.AllArgsConstructor;
import org.apache.shiro.authz.AuthorizationException;
import org.github.sdorra.jse.ShiroExtension;
import org.github.sdorra.jse.SubjectAware;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import sonia.scm.auditlog.AuditEntry;
import sonia.scm.auditlog.AuditLogEntity;
import sonia.scm.auditlog.EntryCreationContext;
import sonia.scm.repository.Repository;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Set;
import java.util.TimeZone;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static sonia.scm.repository.RepositoryTestData.create42Puzzle;
import static sonia.scm.repository.RepositoryTestData.createHeartOfGold;

@ExtendWith({MockitoExtension.class, ShiroExtension.class})
class DefaultAuditLogServiceTest {

  private Connection connection;
  private DefaultAuditLogService service;
  private final TimeZone defaultTimeZone = TimeZone.getDefault();

  @BeforeEach
  void initTestDB() throws SQLException {
    String connectionUrl = "jdbc:h2:mem:unit-tests;TIME ZONE=ECT";
    connection = DriverManager.getConnection(connectionUrl);
    service = new DefaultAuditLogService(new AuditLogDatabase(connectionUrl), Runnable::run);
    TimeZone.setDefault(TimeZone.getTimeZone("ECT"));
  }

  @AfterEach
  void clearDB() throws SQLException {
    connection.createStatement().executeUpdate("DROP TABLE AUDITLOG");
    connection.createStatement().executeUpdate("DROP TABLE LABELS");
    TimeZone.setDefault(defaultTimeZone);
  }

  @Test
  void shouldNotReadAuditLogWithoutPermission() {
    assertThrows(AuthorizationException.class, () -> service.getTotalEntries(new AuditLogFilterContext()));
    assertThrows(AuthorizationException.class, () -> service.getEntries(new AuditLogFilterContext()));
  }

  @Nested
  @SubjectAware(permissions = "auditLog:read")
  class WithReadPermission {
    @Test
    @SubjectAware("trillian")
    void shouldGetEmptyStringIfNoEntries() {
      Collection<LogEntry> entries = service.getEntries(new AuditLogFilterContext());

      assertThat(entries).isEmpty();
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldCreateNewEntryForModified() {
      EntryCreationContext<Repository> creationContext = new EntryCreationContext<>(create42Puzzle(), createHeartOfGold());
      service.createEntry(creationContext);

      Collection<LogEntry> entries = service.getEntries(new AuditLogFilterContext());

      assertThat(entries).hasSize(1);
      LogEntry entry = entries.iterator().next();
      assertThat(entry.getAction()).isEqualTo("modified");
      assertThat(entry.getUser()).isEqualTo("trillian");
      assertThat(entry.getEntity()).isEqualTo("hitchhiker/42puzzle");
      assertThat(entry.getEntry()).contains("[MODIFIED] 'trillian' modified repository 'hitchhiker/42Puzzle'");
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldCreateNewEntryForCreated() {
      EntryCreationContext<Repository> creationContext = new EntryCreationContext<>(create42Puzzle(), null);
      service.createEntry(creationContext);

      Collection<LogEntry> entries = service.getEntries(new AuditLogFilterContext());

      assertThat(entries).hasSize(1);
      LogEntry entry = entries.iterator().next();
      assertThat(entry.getAction()).isEqualTo("created");
      assertThat(entry.getUser()).isEqualTo("trillian");
      assertThat(entry.getEntity()).isEqualTo("hitchhiker/42puzzle");
      assertThat(entry.getEntry()).contains("[CREATED] 'trillian' created repository 'hitchhiker/42Puzzle'");
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldCreateNewEntryForDeleted() {
      EntryCreationContext<Repository> creationContext = new EntryCreationContext<>(null, create42Puzzle());
      service.createEntry(creationContext);

      Collection<LogEntry> entries = service.getEntries(new AuditLogFilterContext());

      assertThat(entries).hasSize(1);
      LogEntry entry = entries.iterator().next();
      assertThat(entry.getAction()).isEqualTo("deleted");
      assertThat(entry.getUser()).isEqualTo("trillian");
      assertThat(entry.getEntity()).isEqualTo("hitchhiker/42puzzle");
      assertThat(entry.getEntry()).contains("[DELETED] 'trillian' deleted repository 'hitchhiker/42Puzzle'");
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldGetTotalEntries() {
      EntryCreationContext<Repository> creationContext = new EntryCreationContext<>(null, create42Puzzle());
      service.createEntry(creationContext);
      service.createEntry(creationContext);
      service.createEntry(creationContext);
      service.createEntry(creationContext);
      service.createEntry(creationContext);
      service.createEntry(creationContext);

      int totalEntries = service.getTotalEntries(new AuditLogFilterContext());

      assertThat(totalEntries).isEqualTo(6);
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldCreateEntryWithCorrectEntityName() {
      EntryCreationContext<TestEntity> creationContext = new EntryCreationContext<>(new TestEntity("secret_name"), null);

      service.createEntry(creationContext);

      Collection<LogEntry> entries = service.getEntries(new AuditLogFilterContext());

      LogEntry entry = entries.iterator().next();
      assertThat(entry.getEntity()).isEqualTo("secret_name");
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldCreateEntryWithoutEntityName() {
      EntryCreationContext<WithoutAnnotation> creationContext = new EntryCreationContext<>(new WithoutAnnotation("secret_name"), null);

      service.createEntry(creationContext);

      Collection<LogEntry> entries = service.getEntries(new AuditLogFilterContext());

      LogEntry entry = entries.iterator().next();
      assertThat(entry.getEntity()).isEmpty();
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldCreateEntryWithExplicitEntityName() {
      EntryCreationContext<WithoutAnnotation> creationContext = new EntryCreationContext<>(new WithoutAnnotation("secret_name"), null, "TRILLIAN", emptySet());

      service.createEntry(creationContext);

      Collection<LogEntry> entries = service.getEntries(new AuditLogFilterContext());

      LogEntry entry = entries.iterator().next();
      assertThat(entry.getEntity()).isEqualTo("trillian");
    }

    @Test
    @SubjectAware
    void shouldCreateEntryForServerContext() {
      EntryCreationContext<TestEntity> creationContext = new EntryCreationContext<>(new TestEntity("secret_name"), null);

      service.createEntry(creationContext);

      Collection<LogEntry> entries = service.getLogEntries(new AuditLogFilterContext());

      LogEntry entry = entries.iterator().next();
      assertThat(entry.getUser()).isNull();
    }

    @Test
    @SubjectAware("TrillIAN")
    void shouldCreateFilterableFieldsForNewEntriesAllLowerCase() {
      EntryCreationContext<TestEntity> creationContext = new EntryCreationContext<>(new TestEntity("SEcreT"), null, Set.of("ONlyLowerCase"));

      service.createEntry(creationContext);

      Collection<LogEntry> entries = service.getLogEntries(new AuditLogFilterContext());

      LogEntry entry = entries.iterator().next();
      assertThat(entry.getUser()).isEqualTo("trillian");
      assertThat(entry.getEntity()).isEqualTo("secret");
      assertThat(entry.getAction()).isEqualTo("created");

      assertThat(service.getLabels()).contains("onlylowercase");
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldGetEntriesByEntityFilter() {
      prepareDbEntries();

      AuditLogFilterContext filter = new AuditLogFilterContext();
      filter.setEntity("TRILLIAN");
      Collection<LogEntry> entries = service.getEntries(filter);

      assertThat(entries).hasSize(1);
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldGetEntriesByLabelFilter() {
      prepareDbEntries();

      AuditLogFilterContext filter = new AuditLogFilterContext();
      filter.setLabel("object");
      Collection<LogEntry> entries = service.getEntries(filter);

      assertThat(entries).hasSize(1);
      LogEntry entry = entries.iterator().next();
      assertThat(entry.getEntity()).isEqualTo("trillian");
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldGetEntriesByUsernameFilter() {
      EntryCreationContext<?> creationContext = new EntryCreationContext<>(new TestEntity("entity"), null, "TRILLIAN", emptySet());
      service.createEntry(creationContext);

      creationContext = new EntryCreationContext<>(new WithoutAnnotation("anno"), null, "DENT", emptySet());
      service.createEntry(creationContext);

      AuditLogFilterContext filter = new AuditLogFilterContext();
      filter.setUsername("trillian");
      Collection<LogEntry> entries = service.getEntries(filter);

      assertThat(entries).hasSize(2);
      LogEntry entry = entries.iterator().next();
      assertThat(entry.getUser()).isEqualTo("trillian");
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldGetEntriesByFromDateFilter() {
      prepareDbEntries();

      AuditLogFilterContext filter = new AuditLogFilterContext();
      filter.setFrom(new Date(Instant.now().minus(5, ChronoUnit.DAYS).toEpochMilli()));
      Collection<LogEntry> entries = service.getEntries(filter);

      assertThat(entries).hasSize(2);
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldGetEntriesByToDateFilter() {
      prepareDbEntries();

      AuditLogFilterContext filter = new AuditLogFilterContext();
      filter.setTo(new Date(Instant.now().plus(5, ChronoUnit.DAYS).toEpochMilli()));
      Collection<LogEntry> entries = service.getEntries(filter);

      assertThat(entries).hasSize(2);
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldGetEntriesByBothDateFilters() {
      prepareDbEntries();

      AuditLogFilterContext filter = new AuditLogFilterContext();
      filter.setFrom(new Date(Instant.now().minus(5, ChronoUnit.DAYS).toEpochMilli()));
      filter.setTo(new Date(Instant.now().plus(5, ChronoUnit.DAYS).toEpochMilli()));
      Collection<LogEntry> entries = service.getEntries(filter);

      assertThat(entries).hasSize(2);
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldGetEntriesWithActionFilter() {
      prepareDbEntries();

      AuditLogFilterContext filter = new AuditLogFilterContext();
      filter.setAction("modified");
      Collection<LogEntry> entries = service.getEntries(filter);

      assertThat(entries).hasSize(1);
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldGetEntriesWithAllFilters() {
      prepareDbEntries();

      AuditLogFilterContext filter = new AuditLogFilterContext();
      filter.setFrom(new Date(Instant.now().minus(5, ChronoUnit.DAYS).toEpochMilli()));
      filter.setTo(new Date(Instant.now().plus(5, ChronoUnit.DAYS).toEpochMilli()));
      filter.setUsername("trillian");
      filter.setLabel("test");
      filter.setEntity("TRILLIAN");
      filter.setAction("modified");
      Collection<LogEntry> entries = service.getEntries(filter);

      assertThat(entries).hasSize(1);
      LogEntry entry = entries.iterator().next();

      assertThat(entry.getEntry()).contains("[MODIFIED] 'trillian' modified test object more \n" +
        "Diff:\n" +
        "  - 'name' changed: 'oldEntity' -> 'entity'");
    }

    @Test
    @SubjectAware(value = "trillian")
    void shouldFilterBasedOnSystemTimeZone() throws SQLException {
      //01.01.2024 23:00:00 UTC
      createTimeZoneDependentEntries(1704150000000L, "Too late");
      //01.01.2024 22:59:59 UTC
      createTimeZoneDependentEntries(1704149999000L, "within upper limit");
      //31.12.2023 23:00:00 UTC
      createTimeZoneDependentEntries(1704063600000L, "within lower limit");
      //31.12.2023 22:59:59 UTC
      createTimeZoneDependentEntries(1704063599000L, "Too soon");

      AuditLogFilterContext filter = new AuditLogFilterContext();
      filter.setFrom(Date.valueOf("2024-01-01"));
      filter.setTo(Date.valueOf("2024-01-02"));
      Collection<LogEntry> entries = service.getEntries(filter);

      assertThat(entries).hasSize(2);
      assertThat(entries.stream().map(LogEntry::getEntity)).containsOnly("within upper limit", "within lower limit");
    }
  }

  private void createTimeZoneDependentEntries(long timestamp, String entity) throws SQLException {
    PreparedStatement statement = connection.prepareStatement("INSERT INTO AUDITLOG(TIMESTAMP_, ENTITY, USERNAME, ACTION_, ENTRY) VALUES (?, ?, ?, ?, ?)");
    //01.01.2024 23:00 UTC
    statement.setTimestamp(1, new Timestamp(timestamp));
    statement.setString(2, entity);
    statement.setString(3, "user");
    statement.setString(4, "created");
    statement.setString(5, "Diff");
    statement.executeUpdate();
  }

  private void prepareDbEntries() {
    EntryCreationContext<?> first = new EntryCreationContext<>(new TestEntity("entity"), new TestEntity("oldEntity"), "TRILLIAN", emptySet());
    service.createEntry(first);

    EntryCreationContext<?> second = new EntryCreationContext<>(new WithoutAnnotation("anno"), null, "DENT", emptySet());
    service.createEntry(second);
  }

  @AllArgsConstructor
  @AuditEntry(labels = {"test", "object", "more"})
  static class TestEntity implements AuditLogEntity {
    private String name;

    @Override
    public String getEntityName() {
      return name;
    }
  }

  @AllArgsConstructor
  static class WithoutAnnotation {
    private String name;
  }
}
