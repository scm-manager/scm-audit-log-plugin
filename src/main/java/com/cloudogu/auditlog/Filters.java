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
        .append("WHERE TRUE ");

      appliedFilters
        .forEach(filter -> builder.append(filter.getSqlClause()));

      return builder.toString();
    }
  }

  static List<AppliedFilter> resolveAppliedFilters(AuditLogFilterContext filterContext) {
    List<AppliedFilter> appliedFilters = new ArrayList<>();

    if (filterContext.getFrom() != null) {
      appliedFilters.add(new AppliedFilter("AND CAST(AUDITLOG.TIMESTAMP_ as TIMESTAMP WITH TIME ZONE) >= ? ", filterContext.getFrom().toString()));
    }
    if (filterContext.getTo() != null) {
      appliedFilters.add(new AppliedFilter("AND CAST(AUDITLOG.TIMESTAMP_ as TIMESTAMP WITH TIME ZONE) < ? ", filterContext.getTo().toString()));
    }
    if (filterContext.getEntity() != null) {
      appliedFilters.add(new AppliedFilter("AND AUDITLOG.ENTITY LIKE ? ", normalizeValue(filterContext.getEntity())));
    }
    if (filterContext.getUsername() != null) {
      appliedFilters.add(new AppliedFilter("AND AUDITLOG.USERNAME LIKE ? ", normalizeValue(filterContext.getUsername())));
    }
    if (filterContext.getLabel() != null) {
      appliedFilters.add(new AppliedFilter("AND AUDITLOG.ID IN (SELECT LABELS.AUDIT FROM LABELS WHERE LABELS.LABEL = ?) ", normalizeValue(filterContext.getLabel())));
    }
    if (filterContext.getAction() != null) {
      appliedFilters.add(new AppliedFilter("AND AUDITLOG.ACTION_ = ? ", normalizeValue(filterContext.getAction())));
    }

    return appliedFilters;
  }

  private static String normalizeValue(String value) {
    return value.toLowerCase().replace("*", "%");
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
