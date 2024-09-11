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

