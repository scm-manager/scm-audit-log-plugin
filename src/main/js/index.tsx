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

import { binder, extensionPoints } from "@scm-manager/ui-extensions";
import { Links } from "@scm-manager/ui-types";
import React, { FC } from "react";
import { Route, Switch } from "react-router-dom";
import AuditLogNavigation from "./AuditLogNavigation";
import AuditLog from "./AuditLog";
import RepositoryAuditLogLink from "./RepositoryAuditLogLink";
import UserAuditLogLink from "./UserAuditLogLink";
import GroupAuditLogLink from "./GroupAuditLogLink";

type PredicateProps = {
  links: Links;
};

export const predicate = ({ links }: PredicateProps) => {
  return !!(links && links.auditLog);
};

const AuditLogRoute: FC<{ links: Links }> = ({ links }) => {
  return (
    <Switch>
      <Route path="/admin/audit-log/" exact>
        <AuditLog links={links} />
      </Route>
      <Route path="/admin/audit-log/:page" exact>
        <AuditLog links={links} />
      </Route>
    </Switch>
  );
};

binder.bind<extensionPoints.AdminRoute>("admin.route", AuditLogRoute, predicate);
binder.bind<extensionPoints.AdminNavigation>("admin.navigation", AuditLogNavigation, predicate);
binder.bind<extensionPoints.RepositoryInformationTableBottom>("repository.information.table.bottom", RepositoryAuditLogLink);
binder.bind<extensionPoints.UserInformationTableBottom>("user.information.table.bottom", UserAuditLogLink);
binder.bind<extensionPoints.GroupInformationTableBottom>("group.information.table.bottom", GroupAuditLogLink);
