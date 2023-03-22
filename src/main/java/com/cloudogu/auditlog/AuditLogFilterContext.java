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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;


@NoArgsConstructor
@Getter
@Setter
public class AuditLogFilterContext {
  private int pageNumber = 1;
  private int limit = 100;
  private String entity;
  private String username;
  private Date from;
  private Date to;
  private String label;
  private String action;

  @SuppressWarnings("java:S107") // Big constructor because of many filter options
  public AuditLogFilterContext(int pageNumber, int limit, String entity, String username, String from, String to, String label, String action) {
    this.pageNumber = pageNumber;
    this.limit = limit;
    this.entity = entity;
    this.username = username;
    if (from != null) {
      this.from = Date.valueOf(from);
    }
    if (to != null) {
      // Add one day to include the whole day instead of ending at 00:00:00 of the selected day
      this.to = new Date(Instant.ofEpochMilli(Date.valueOf(to).getTime()).plus(1, ChronoUnit.DAYS).toEpochMilli());
    }
    this.label = label;
    this.action = action;
  }

  public boolean hasContentFilter() {
    return entity != null || username != null || from != null || to != null || label != null || action != null;
  }
}

