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

import React from "react";
import { binder, extensionPoints } from "@scm-manager/ui-extensions";
import AuditLogNavigation from "./AuditLogNavigation";
import AuditLog from "./AuditLog";
import RepositoryAuditLogLink from "./RepositoryAuditLogLink";
import UserAuditLogLink from "./UserAuditLogLink";
import GroupAuditLogLink from "./GroupAuditLogLink";

const auditLogRoute = () => ({
  path: "audit-log/:page?",
  element: <AuditLog />
});

binder.bind("admin.route", auditLogRoute);
binder.bind<extensionPoints.AdminNavigation>("admin.navigation", AuditLogNavigation);
binder.bind<extensionPoints.RepositoryInformationTableBottom>(
  "repository.information.table.bottom",
  RepositoryAuditLogLink,
);
binder.bind<extensionPoints.UserInformationTableBottom>("user.information.table.bottom", UserAuditLogLink);
binder.bind<extensionPoints.GroupInformationTableBottom>("group.information.table.bottom", GroupAuditLogLink);
