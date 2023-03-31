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
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Set;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static sonia.scm.repository.RepositoryTestData.create42Puzzle;
import static sonia.scm.repository.RepositoryTestData.createHeartOfGold;

@ExtendWith({MockitoExtension.class, ShiroExtension.class})
class DefaultAuditLogServiceTest {

  private Connection connection;
  private DefaultAuditLogService service;

  @BeforeEach
  void initTestDB() throws SQLException {
    connection = DriverManager.getConnection("jdbc:h2:mem:unit-tests");
    service = new DefaultAuditLogService(new AuditLogDatabase("jdbc:h2:mem:unit-tests"), Runnable::run);
  }

  @AfterEach
  void clearDB() throws SQLException {
    connection.createStatement().executeUpdate("DROP TABLE AUDITLOG");
    connection.createStatement().executeUpdate("DROP TABLE LABELS");
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
