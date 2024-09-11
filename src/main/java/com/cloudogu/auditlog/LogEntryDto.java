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

import de.otto.edison.hal.HalRepresentation;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@SuppressWarnings("java:S2160") // Equals and Hashcode not needed for dto
public class LogEntryDto extends HalRepresentation {

  private Instant timestamp;
  private String entity;
  private String user;
  private String action;
  private String entry;

  private LogEntryDto() {}

  static LogEntryDto from(LogEntry entry) {
    LogEntryDto dto = new LogEntryDto();

    dto.setEntry(entry.getEntry());
    dto.setAction(entry.getAction());
    dto.setTimestamp(entry.getTimestamp());
    dto.setEntity(entry.getEntity());
    dto.setUser(entry.getUser());

    return dto;
  }
}
