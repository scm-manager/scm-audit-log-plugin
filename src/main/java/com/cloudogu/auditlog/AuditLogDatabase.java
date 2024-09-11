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

import org.h2.jdbcx.JdbcConnectionPool;
import sonia.scm.SCMContextProvider;

import jakarta.inject.Inject;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

class AuditLogDatabase implements ServletContextListener {

  private final JdbcConnectionPool dataSource;

  @Inject
  AuditLogDatabase(SCMContextProvider contextProvider) throws SQLException {
    this("jdbc:h2:" + contextProvider.getBaseDirectory() + "/audit-log/audit-log");
  }

  AuditLogDatabase(String url) throws SQLException {
    dataSource = JdbcConnectionPool.create(url, null, null);

    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE IF NOT EXISTS AUDITLOG(ID int auto_increment primary key, TIMESTAMP_ timestamp, ENTITY varchar, USERNAME varchar, ACTION_ varchar, ENTRY varchar);");
      statement.execute("CREATE TABLE IF NOT EXISTS LABELS(AUDIT int, LABEL varchar);");
    }
  }

  public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }

  @Override
  public void contextInitialized(ServletContextEvent servletContextEvent) {
    // nothing to do
  }

  @Override
  public void contextDestroyed(ServletContextEvent servletContextEvent) {
    dataSource.dispose();
  }
}
