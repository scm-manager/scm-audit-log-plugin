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
import lombok.Getter;
import org.junit.jupiter.api.Test;
import sonia.scm.auditlog.AuditEntry;
import sonia.scm.auditlog.AuditLogEntity;
import sonia.scm.auditlog.EntryCreationContext;
import sonia.scm.group.Group;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryTestData;
import sonia.scm.user.User;
import sonia.scm.xml.XmlCipherStringAdapter;
import sonia.scm.xml.XmlEncryptionAdapter;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEntryGeneratorTest {

  AuditEntryGenerator generator = new AuditEntryGenerator();

  @Test
  void shouldCreateEntryForDeletedRepository() {
    Repository oldObject = RepositoryTestData.create42Puzzle();
    String entry = generator.generate(
      new EntryCreationContext<>(null, oldObject),
      Instant.ofEpochSecond(1700000000),
      "trillian",
      "deleted",
      oldObject.getNamespaceAndName().toString(),
      new String[]{"repository"}
    );

    assertThat(entry).isEqualTo("2023-11-14T22:13:20Z [DELETED] 'trillian' deleted repository 'hitchhiker/42Puzzle'");
  }

  @Test
  void shouldCreateEntryForDeletedUser() {
    String entry = generator.generate(
      new EntryCreationContext<>(null, new User()),
      Instant.ofEpochSecond(1700000000),
      "trillian",
      "deleted",
      "dent",
      new String[]{"user"}
    );

    assertThat(entry).isEqualTo("2023-11-14T22:13:20Z [DELETED] 'trillian' deleted user 'dent'");
  }

  @Test
  void shouldCreateEntryForCreatedUser() {
    String entry = generator.generate(
      new EntryCreationContext<>(new User("dent", "Arthur Dent", "", "secret", "internal", true), null),
      Instant.ofEpochSecond(1700000000),
      "trillian",
      "created",
      "dent",
      new String[]{"user"}
    );

    assertThat(entry).isEqualTo("2023-11-14T22:13:20Z [CREATED] 'trillian' created user 'dent'\n" +
      "Diff:\n" +
      "  - 'active' = 'true'\n" +
      "  - 'displayName' = 'Arthur Dent'\n" +
      "  - 'mail' = ''\n" +
      "  - 'name' = 'dent'\n" +
      "  - 'password' = ********\n" +
      "  - 'type' = 'internal'\n");
  }

  @Test
  void shouldCreateEntryForModifiedRepository() {
    Repository puzzle = RepositoryTestData.create42Puzzle();
    puzzle.setId("1");
    Repository heartOfGold = RepositoryTestData.createHeartOfGold();
    heartOfGold.setId("2");
    String entry = generator.generate(
      new EntryCreationContext<>(puzzle, heartOfGold),
      Instant.ofEpochSecond(1700000000),
      "trillian",
      "modified",
      "HeartOfGold",
      new String[]{"repository"}
    );

    assertThat(entry).isEqualTo("2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified repository 'HeartOfGold'\n" +
      "Diff:\n" +
      "  - 'contact' changed: 'zaphod.beeblebrox@hitchhiker.com' -> 'douglas.adams@hitchhiker.com'\n" +
      "  - 'description' changed: 'Heart of Gold is the first prototype ship to successfully utilise the revolutionary Infinite Improbability Drive' -> 'The 42 Puzzle'\n" +
      "  - 'id' changed: '2' -> '1'\n" +
      "  - 'name' changed: 'HeartOfGold' -> '42Puzzle'\n");
  }

  @Test
  void shouldCreateEntryForModifiedGroup() {
    String entry = generator.generate(
      new EntryCreationContext<>(new Group("internal", "devs", "trillian", "zaphod"), new Group("external", "admins", "dent")),
      Instant.ofEpochSecond(1700000000),
      "trillian",
      "modified",
      "admins",
      new String[]{"group"}
    );

    assertThat(entry).isEqualTo("2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified group 'admins'\n" +
      "Diff:\n" +
      "  - 'members' collection changes :\n" +
      "     0. 'dent' changed to 'trillian'\n" +
      "     1. 'zaphod' added\n" +
      "  - 'name' changed: 'admins' -> 'devs'\n" +
      "  - 'type' changed: 'external' -> 'internal'\n");
  }

  @Test
  void shouldCreateEntryForModifiedRedmineRepositoryConfig() {
    Repository repo = RepositoryTestData.create42Puzzle();
    String entry = generator.generate(
      new EntryCreationContext<>(new RedmineConfig("test", "123", "secret"), new RedmineConfig("test", "1234", "secret!!")),
      Instant.ofEpochSecond(1700000000),
      "trillian",
      "modified",
      repo.getNamespaceAndName().toString(),
      new String[]{"redmine", "configuration", "repository"}
    );

    assertThat(entry).isEqualTo("2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified redmine configuration for repository 'hitchhiker/42Puzzle'\n" +
      "Diff:\n" +
      "  - 'token' changed: ********\n" +
      "  - 'url' changed: '1234' -> '123'\n");
  }

  @Test
  void shouldIgnoreDiffIfOnlyIgnoredFieldsChanged() {
    String entry = generator.generate(
      new EntryCreationContext<>(new TopLevel("test", true), new TopLevel("test2", false)),
      Instant.ofEpochSecond(1700000000),
      "trillian",
      "modified",
      "top",
      new String[]{"top", "level"});

    assertThat(entry).isEmpty();
  }

  @Test
  void shouldMaskFieldsWithEncryptionAutomatically() {
    String entry = generator.generate(
      new EntryCreationContext<>(new SecretConfig("test", "secret", "token"), null),
      Instant.ofEpochSecond(1700000000),
      "trillian",
      "modified",
      "",
      new String[]{});

    assertThat(entry).isEqualTo("2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified \n" +
      "Diff:\n" +
      "  - 'name' = 'test'\n" +
      "  - 'password' = ********\n" +
      "  - 'token' = ********\n");
  }

  @Test
  void shouldMaskFieldsWithEncryptionAutomaticallyOnDerivedClass() {
    String entry = generator.generate(
      new EntryCreationContext<>(new DerivativeSecretConfig("test", "secret", "token"), null),
      Instant.ofEpochSecond(1700000000),
      "trillian",
      "modified",
      "",
      new String[]{});

    assertThat(entry).contains("'token' = ********");
  }

}

@AllArgsConstructor
@Getter
@AuditEntry(maskedFields = "token")
class RedmineConfig {
  private String name;
  private String url;
  private String token;
}

@AllArgsConstructor
@Getter
@AuditEntry(ignoredFields = {"name", "active"})
class TopLevel implements AuditLogEntity {
  private String name;
  private boolean active;

  @Override
  public String getEntityName() {
    return name;
  }
}

class DerivativeSecretConfig extends SecretConfig {
  public DerivativeSecretConfig(String name, String password, String token) {
    super(name, password, token);
  }
}

@AllArgsConstructor
@Getter
@AuditEntry
class SecretConfig implements AuditLogEntity {
  private String name;
  @XmlJavaTypeAdapter(XmlCipherStringAdapter.class)
  private String password;

  @XmlJavaTypeAdapter(XmlEncryptionAdapter.class)
  private String token;

  @Override
  public String getEntityName() {
    return name;
  }
}
