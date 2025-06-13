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

import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import sonia.scm.migration.UpdateException;
import sonia.scm.store.QueryableMutableStore;
import sonia.scm.store.QueryableStoreExtension;

import java.io.File;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(QueryableStoreExtension.class)
@QueryableStoreExtension.QueryableTypes({AuditLogDao.class, LabelDao.class})
class H2ToQueryableUpdateStepTest {

  @Nested
  class WithOldDb {

    @TempDir
    private File temp;

    @BeforeEach
    void setUpDbFile() throws Exception {
      Files.copy(
        new File(Resources.getResource("com/cloudogu/auditlog/audit-log.mv.db").getFile()),
        new File(temp, "audit-log.mv.db")
      );
    }

    @Test
    void shouldMigrateLogs(AuditLogDaoStoreFactory auditLogDaoStoreFactory, LabelDaoStoreFactory labelDaoStoreFactory) throws Exception {
      H2ToQueryableUpdateStep updateStep = new H2ToQueryableUpdateStep(
        temp.getPath(),
        auditLogDaoStoreFactory,
        labelDaoStoreFactory
      );

      updateStep.doUpdate();

      QueryableMutableStore<AuditLogDao> store = auditLogDaoStoreFactory.getMutable();
      Map<String, AuditLogDao> logs = store.getAll();
      assertThat(logs.keySet()).containsExactly("1", "2", "3");

      AuditLogDao firstLog = logs.get("1");
      assertThat(firstLog.getAction()).isEqualTo("created");
      assertThat(firstLog.getEntry()).contains("'First Git repository'");
      assertThat(firstLog.getUsername()).isEqualTo("scmadmin");
      assertThat(firstLog.getLabels()).containsExactly("repository");
      assertThat(firstLog.getEntityName()).isEqualTo("scmadmin/hog");
      assertThat(labelDaoStoreFactory.getMutable().getAll().keySet())
        .containsExactly("git", "repository", "config");
    }

    @Test
    void shouldDeleteOldDbFile(AuditLogDaoStoreFactory auditLogDaoStoreFactory, LabelDaoStoreFactory labelDaoStoreFactory) throws Exception {
      H2ToQueryableUpdateStep updateStep = new H2ToQueryableUpdateStep(
        temp.getPath(),
        auditLogDaoStoreFactory,
        labelDaoStoreFactory
      );

      updateStep.doUpdate();

      assertThat(temp).doesNotExist();
    }

    @Test
    void shouldFailIfCountOfLogsDiffer(AuditLogDaoStoreFactory auditLogDaoStoreFactory, LabelDaoStoreFactory labelDaoStoreFactory) throws Exception {
      auditLogDaoStoreFactory.getMutable().put(new AuditLogDao());

      H2ToQueryableUpdateStep updateStep = new H2ToQueryableUpdateStep(
        temp.getPath(),
        auditLogDaoStoreFactory,
        labelDaoStoreFactory
      );

      Assertions.assertThrows(
        UpdateException.class,
        updateStep::doUpdate
      );

      assertThat(temp).isNotEmptyDirectory();
    }
  }

  @Test
  void shouldDoNothingIfDbDoesNotExist(AuditLogDaoStoreFactory auditLogDaoStoreFactory, LabelDaoStoreFactory labelDaoStoreFactory) throws Exception {
    H2ToQueryableUpdateStep updateStep = new H2ToQueryableUpdateStep(
      "./does/not/exist/audit-log",
      auditLogDaoStoreFactory,
      labelDaoStoreFactory
    );

    updateStep.doUpdate();

    assertThat(auditLogDaoStoreFactory.getMutable().getAll()).isEmpty();
    assertThat(labelDaoStoreFactory.getMutable().getAll()).isEmpty();
  }
}
