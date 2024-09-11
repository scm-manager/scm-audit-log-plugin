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

import java.util.List;

import static com.cloudogu.auditlog.Filters.createFilterQuery;

public class SqlQueryGenerator {

  private SqlQueryGenerator() {}

  static String createEntriesQuery(AuditLogFilterContext filterContext, List<Filters.AppliedFilter> appliedFilters) {
    return "SELECT TIMESTAMP_,ENTITY,USERNAME,ACTION_,ENTRY FROM AUDITLOG " +
      createFilterQuery(filterContext, appliedFilters) +
      "ORDER BY ID DESC " +
      "LIMIT " + filterContext.getLimit() + " " +
      "OFFSET " + (filterContext.getPageNumber() - 1) * filterContext.getLimit() + ";";
  }

  static String createCountQuery(AuditLogFilterContext filterContext, List<Filters.AppliedFilter> appliedFilters) {
    return "SELECT COUNT(*) AS total FROM AUDITLOG "
      + createFilterQuery(filterContext, appliedFilters) + ";";
  }

  static String createLabelsQuery() {
    return "SELECT DISTINCT LABEL FROM LABELS";
  }
}
