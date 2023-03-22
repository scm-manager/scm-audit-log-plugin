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

import java.util.List;

import static com.cloudogu.auditlog.Filters.createFilterQuery;

public class SqlQueryGenerator {

  private SqlQueryGenerator() {}

  static String createEntriesQuery(AuditLogFilterContext filterContext, List<Filters.AppliedFilter> appliedFilters) {
    return "SELECT TIMESTAMP_,ENTITY,USERNAME,ACTION_,ENTRY FROM AUDITLOG " +
      createFilterQuery(filterContext, appliedFilters) +
      "GROUP BY TIMESTAMP_,ENTITY,USERNAME,ACTION_,ENTRY " +
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
