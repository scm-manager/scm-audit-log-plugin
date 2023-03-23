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

import org.h2.jdbcx.JdbcConnectionPool;
import sonia.scm.SCMContextProvider;

import javax.inject.Inject;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
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
