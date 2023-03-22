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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Filters {

  private Filters() {
  }

  static String createFilterQuery(AuditLogFilterContext filterContext, List<Filters.AppliedFilter> appliedFilters) {
    if (!filterContext.hasContentFilter()) {
      return "";
    } else {
      StringBuilder builder = new StringBuilder();
      builder
        .append("LEFT OUTER JOIN LABELS ")
        .append("ON AUDITLOG.ID = LABELS.AUDIT ")
        .append("WHERE TRUE ");

      appliedFilters
        .forEach(filter -> builder.append(filter.getSqlClause()));

      return builder.toString();
    }
  }

  static List<AppliedFilter> resolveAppliedFilters(AuditLogFilterContext filterContext) {
    List<AppliedFilter> appliedFilters = new ArrayList<>();

    if (filterContext.getFrom() != null) {
      appliedFilters.add(new AppliedFilter("AND AUDITLOG.TIMESTAMP_ >= ? ", filterContext.getFrom().toString()));
    }
    if (filterContext.getTo() != null) {
      appliedFilters.add(new AppliedFilter("AND AUDITLOG.TIMESTAMP_ <= ? ", filterContext.getTo().toString()));
    }
    if (filterContext.getEntity() != null) {
      appliedFilters.add(new AppliedFilter("AND AUDITLOG.ENTITY = ? ", filterContext.getEntity()));
    }
    if (filterContext.getUsername() != null) {
      appliedFilters.add(new AppliedFilter("AND AUDITLOG.USERNAME = ? ", filterContext.getUsername()));
    }
    if (filterContext.getLabel() != null) {
      appliedFilters.add(new AppliedFilter("AND LABELS.LABEL = ? ", filterContext.getLabel()));
    }
    if (filterContext.getAction() != null) {
      appliedFilters.add(new AppliedFilter("AND AUDITLOG.ACTION_ = ? ", filterContext.getAction()));
    }

    return appliedFilters;
  }

  static void setFilterValues(PreparedStatement statement, List<AppliedFilter> appliedFilters) throws SQLException {
    if (!appliedFilters.isEmpty()) {
      Iterator<AppliedFilter> iterator = appliedFilters.iterator();
      for (int i = 1; i <= appliedFilters.size(); i++) {
        statement.setString(i, iterator.next().getValue());
      }
    }
  }

  @AllArgsConstructor
  @Getter
  static class AppliedFilter {
    private String sqlClause;
    private String value;
  }
}
