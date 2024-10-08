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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FiltersTest {

  @Test
  void shouldApplyFiltersWithoutCaseSensitivity() {
    List<Filters.AppliedFilter> appliedFilters = Filters.resolveAppliedFilters(
      new AuditLogFilterContext(
        0,
        0,
        "ENtiTy",
        "Trillian",
        "2023-01-10",
        "2024-01-10",
        "repository",
        "created")
    );

    assertThat(appliedFilters.stream().map(Filters.AppliedFilter::getValue))
      .contains("trillian", "entity", "repository", "created");
  }

  @Test
  void shouldTransformAsteriskToPercentSignForWildcardSearches() {
    List<Filters.AppliedFilter> appliedFilters = Filters.resolveAppliedFilters(new AuditLogFilterContext(
      0,
      0,
      "*nt*",
      "*illian",
      "2023-01-10",
      "2024-01-10",
      "repository",
      "creat*")
    );

   assertThat(appliedFilters.stream().map(Filters.AppliedFilter::getValue))
     .contains("%nt%", "%illian", "creat%");
  }
}
