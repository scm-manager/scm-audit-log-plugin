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

import { apiClient } from "@scm-manager/ui-components";
import { useQuery } from "react-query";
import { ApiResult, useRequiredIndexLink } from "@scm-manager/ui-api";
import { HalRepresentationWithEmbedded } from "@scm-manager/ui-types";

type AuditLogEntry = {
  timestamp: Date;
  entity: string;
  user: string;
  action: string;
  entry: string;
};

type AuditLogEntries = {
  entries: AuditLogEntry[];
};

type AuditLog = HalRepresentationWithEmbedded<AuditLogEntries> & {
  page: number;
  pageTotal: number;
};

export type Filters = {
  entity: string;
  username: string;
  label: string;
  action: string;
  from: string;
  to: string;
};

export const useAuditLog = (pageNumber: number, filters: Filters): ApiResult<AuditLog> => {
  const indexLink = useRequiredIndexLink("auditLog");
  return useQuery<AuditLog, Error>(["auditLog", pageNumber, filters], () =>
    apiClient
      .get(
        Object.entries(filters).reduce(
          (link, [filterKey, filterValue]) => (filterValue ? `${link}&${filterKey}=${filterValue}` : link),
          indexLink + `?pageNumber=${pageNumber}`
        )
      )
      .then(response => response.json())
  );
};
