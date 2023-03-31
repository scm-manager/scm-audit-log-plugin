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
