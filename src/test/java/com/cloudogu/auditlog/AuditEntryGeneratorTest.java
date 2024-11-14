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

import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
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

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEntryGeneratorTest {

  AuditEntryGenerator generator = new AuditEntryGenerator(ZoneId.of("UTC"));

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

    assertThat(entry).isEqualTo("2023-11-14T22:13:20Z[UTC] [DELETED] 'trillian' deleted repository 'hitchhiker/42Puzzle'");
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

    assertThat(entry).isEqualTo("2023-11-14T22:13:20Z[UTC] [DELETED] 'trillian' deleted user 'dent'");
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

    assertThat(entry).isEqualTo("""
      2023-11-14T22:13:20Z[UTC] [CREATED] 'trillian' created user 'dent'
      Diff:
        - 'active' = 'true'
        - 'displayName' = 'Arthur Dent'
        - 'mail' = ''
        - 'name' = 'dent'
        - 'password' = ********
        - 'type' = 'internal'
      """);
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

    assertThat(entry).isEqualTo("""
      2023-11-14T22:13:20Z[UTC] [MODIFIED] 'trillian' modified repository 'HeartOfGold'
      Diff:
        - 'contact' changed: 'zaphod.beeblebrox@hitchhiker.com' -> 'douglas.adams@hitchhiker.com'
        - 'description' changed: 'Heart of Gold is the first prototype ship to successfully utilise the revolutionary Infinite Improbability Drive' -> 'The 42 Puzzle'
        - 'id' changed: '2' -> '1'
        - 'name' changed: 'HeartOfGold' -> '42Puzzle'
      """);
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

    assertThat(entry).isEqualTo("""
      2023-11-14T22:13:20Z[UTC] [MODIFIED] 'trillian' modified group 'admins'
      Diff:
        - 'members' collection changes :
           0. 'dent' changed to 'trillian'
           1. 'zaphod' added
        - 'name' changed: 'admins' -> 'devs'
        - 'type' changed: 'external' -> 'internal'
      """);
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

    assertThat(entry).isEqualTo("""
      2023-11-14T22:13:20Z[UTC] [MODIFIED] 'trillian' modified redmine configuration for repository 'hitchhiker/42Puzzle'
      Diff:
        - 'token' changed: ********
        - 'url' changed: '1234' -> '123'
      """);
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

    assertThat(entry).isEqualTo("""
      2023-11-14T22:13:20Z[UTC] [MODIFIED] 'trillian' modified\s
      Diff:
        - 'name' = 'test'
        - 'password' = ********
        - 'token' = ********
      """);
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

  @Test
  void shouldNotAutoMaskFields() {
    String entry = generator.generate(
      new EntryCreationContext<>(new DoNotAutoMaskFields("UserPassword", "pW", "adminPWD", "apiTokens", "otherSeCrEtLocal", "apiKey", "privateRSA"), null),
      Instant.ofEpochSecond(1700000000),
      "trillian",
      "modified",
      "",
      new String[]{});

    assertThat(entry).contains("'UserPassword' = 'UserPassword'");
    assertThat(entry).contains("'pW' = 'pW'");
    assertThat(entry).contains("'adminPWD' = 'adminPWD'");
    assertThat(entry).contains("'apiTokens' = 'apiTokens'");
    assertThat(entry).contains("'otherSeCrEtLocal' = 'otherSeCrEtLocal'");
    assertThat(entry).contains("'apiKey' = 'apiKey'");
    assertThat(entry).contains("'privateRSA' = 'privateRSA'");
  }

  @Test
  void shouldAutoMaskFields() {
    String entry = generator.generate(
      new EntryCreationContext<>(new DoAutoMaskFields("UserPassword", "pW", "adminPWD", "apiTokens", "otherSeCrEtLocal", "apiKey", "privateRSA"), null),
      Instant.ofEpochSecond(1700000000),
      "trillian",
      "modified",
      "",
      new String[]{});

    assertThat(entry).doesNotContain("'UserPassword' = 'UserPassword'");
    assertThat(entry).contains("'UserPassword' = ********");

    assertThat(entry).doesNotContain("'pW' = 'pW'");
    assertThat(entry).contains("'pW' = ********");

    assertThat(entry).doesNotContain("'adminPWD' = 'adminPWD'");
    assertThat(entry).contains("'adminPWD' = ********");

    assertThat(entry).doesNotContain("'apiTokens' = 'apiTokens'");
    assertThat(entry).contains("'apiTokens' = ********");

    assertThat(entry).doesNotContain("'otherSeCrEtLocal' = 'otherSeCrEtLocal'");
    assertThat(entry).contains("'otherSeCrEtLocal' = ********");

    assertThat(entry).doesNotContain("'apiKey' = 'apiKey'");
    assertThat(entry).contains("'apiKey' = ********");

    assertThat(entry).doesNotContain("'privateRSA' = 'privateRSA'");
    assertThat(entry).contains("'privateRSA' = ********");
  }

  @Test
  void shouldApplyAnnotationsOfSubObject() {
    Subfields subfields = new Subfields("shouldMask", "shouldIgnore", "passwordAutoMask");
    String entry = generator.generate(
      new EntryCreationContext<>(
        new RootFields(subfields, List.of(subfields)),
        null
      ),
      Instant.ofEpochSecond(1700000000),
      "trillian",
      "modified",
      "",
      new String[]{});

    assertThat(entry).doesNotContain("shouldIgnore");

    assertThat(entry).doesNotContain("'subfields.shouldMask' = 'shouldMask'");
    assertThat(entry).contains("'subfields.shouldMask' = ********");

    assertThat(entry).doesNotContain("'subfields.passwordAutoMask' = 'passwordAutoMask'");
    assertThat(entry).contains("'subfields.passwordAutoMask' = ********");

    assertThat(entry).doesNotContain("'subfieldsCollection/0.shouldMask' = 'shouldMask'");
    assertThat(entry).contains("'subfieldsCollection/0.shouldMask' = ********");

    assertThat(entry).doesNotContain("'subfieldsCollection/0.passwordAutoMask' = 'passwordAutoMask'");
    assertThat(entry).contains("'subfieldsCollection/0.passwordAutoMask' = ********");
  }

}

@Getter
@AllArgsConstructor
class RootFields {
  private Subfields subfields;
  private List<Subfields> subfieldsCollection;
}

@Getter
@AllArgsConstructor
@AuditEntry(maskedFields = "shouldMask", ignoredFields = "shouldIgnore", autoMask = true)
class Subfields {
  private String shouldMask;
  private String shouldIgnore;
  private String passwordAutoMask;
}

@Getter
@AllArgsConstructor
@AuditEntry(autoMask = false)
class DoNotAutoMaskFields {
  private String UserPassword;
  private String pW;
  private String adminPWD;
  private String apiTokens;
  private String otherSeCrEtLocal;
  private String apiKey;
  private String privateRSA;
}

@Getter
@AllArgsConstructor
@AuditEntry
class DoAutoMaskFields {
  private String UserPassword;
  private String pW;
  private String adminPWD;
  private String apiTokens;
  private String otherSeCrEtLocal;
  private String apiKey;
  private String privateRSA;
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
