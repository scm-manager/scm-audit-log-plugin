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
