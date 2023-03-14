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
import org.github.sdorra.jse.ShiroExtension;
import org.github.sdorra.jse.SubjectAware;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import sonia.scm.auditlog.AuditEntry;
import sonia.scm.auditlog.AuditLogEntity;
import sonia.scm.auditlog.EntryCreationContext;
import sonia.scm.repository.Repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collection;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
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
  void shouldGetEmptyStringIfNoEntries() {
    Collection<LogEntry> entries = service.getEntries(new AuditLogFilterContext());

    assertThat(entries).isEmpty();
  }

  @Test
  @SubjectAware(value = "trillian")
  void shouldCreateNewEntryForModified() {
    EntryCreationContext<Repository> creationContext = new EntryCreationContext<>(create42Puzzle(), createHeartOfGold());
    service.createDBEntry(creationContext);

    Collection<LogEntry> entries = service.getEntries(new AuditLogFilterContext());

    assertThat(entries).hasSize(1);
    LogEntry entry = entries.iterator().next();
    assertThat(entry.getAction()).isEqualTo("modified");
    assertThat(entry.getUser()).isEqualTo("trillian");
    assertThat(entry.getEntity()).isEqualTo("hitchhiker/42Puzzle");
    assertThat(entry.getEntry()).contains("[MODIFIED] 'trillian' modified repository 'hitchhiker/42Puzzle'");
  }

  @Test
  @SubjectAware(value = "trillian")
  void shouldCreateNewEntryForCreated() {
    EntryCreationContext<Repository> creationContext = new EntryCreationContext<>(create42Puzzle(), null);
    service.createDBEntry(creationContext);

    Collection<LogEntry> entries = service.getEntries(new AuditLogFilterContext());

    assertThat(entries).hasSize(1);
    LogEntry entry = entries.iterator().next();
    assertThat(entry.getAction()).isEqualTo("created");
    assertThat(entry.getUser()).isEqualTo("trillian");
    assertThat(entry.getEntity()).isEqualTo("hitchhiker/42Puzzle");
    assertThat(entry.getEntry()).contains("[CREATED] 'trillian' created repository 'hitchhiker/42Puzzle'");
  }

  @Test
  @SubjectAware(value = "trillian")
  void shouldCreateNewEntryForDeleted() {
    EntryCreationContext<Repository> creationContext = new EntryCreationContext<>(null, create42Puzzle());
    service.createDBEntry(creationContext);

    Collection<LogEntry> entries = service.getEntries(new AuditLogFilterContext());

    assertThat(entries).hasSize(1);
    LogEntry entry = entries.iterator().next();
    assertThat(entry.getAction()).isEqualTo("deleted");
    assertThat(entry.getUser()).isEqualTo("trillian");
    assertThat(entry.getEntity()).isEqualTo("hitchhiker/42Puzzle");
    assertThat(entry.getEntry()).contains("[DELETED] 'trillian' deleted repository 'hitchhiker/42Puzzle'");
  }

  @Test
  @SubjectAware(value = "trillian")
  void shouldGetTotalEntries() {
    EntryCreationContext<Repository> creationContext = new EntryCreationContext<>(null, create42Puzzle());
    service.createDBEntry(creationContext);
    service.createDBEntry(creationContext);
    service.createDBEntry(creationContext);
    service.createDBEntry(creationContext);
    service.createDBEntry(creationContext);
    service.createDBEntry(creationContext);

    int totalEntries = service.getTotalEntries(new AuditLogFilterContext());

    assertThat(totalEntries).isEqualTo(6);
  }

  @Test
  @SubjectAware(value = "trillian")
  void shouldCreateEntryWithCorrectEntityName() {
    EntryCreationContext<TestEntity> creationContext = new EntryCreationContext<>(new TestEntity("secret_name"), null);

    service.createDBEntry(creationContext);

    Collection<LogEntry> entries = service.getEntries(new AuditLogFilterContext());

    LogEntry entry = entries.iterator().next();
    assertThat(entry.getEntity()).isEqualTo("secret_name");
  }

  @Test
  @SubjectAware(value = "trillian")
  void shouldCreateEntryWithoutEntityName() {
    EntryCreationContext<WithoutAnnotation> creationContext = new EntryCreationContext<>(new WithoutAnnotation("secret_name"), null);

    service.createDBEntry(creationContext);

    Collection<LogEntry> entries = service.getEntries(new AuditLogFilterContext());

    LogEntry entry = entries.iterator().next();
    assertThat(entry.getEntity()).isEmpty();
  }

  @Test
  @SubjectAware(value = "trillian")
  void shouldCreateEntryWithExplicitEntityName() {
    EntryCreationContext<WithoutAnnotation> creationContext = new EntryCreationContext<>(new WithoutAnnotation("secret_name"), null, "TRILLIAN", emptySet());

    service.createDBEntry(creationContext);

    Collection<LogEntry> entries = service.getEntries(new AuditLogFilterContext());

    LogEntry entry = entries.iterator().next();
    assertThat(entry.getEntity()).isEqualTo("TRILLIAN");
  }

  @AllArgsConstructor
  @AuditEntry(labels = {"test", "object"})
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
