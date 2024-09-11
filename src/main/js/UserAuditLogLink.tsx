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

import { User } from "@scm-manager/ui-types";
import React, { FC } from "react";
import { useTranslation } from "react-i18next";
import { useIndex } from "@scm-manager/ui-api";
import { urls } from "@scm-manager/ui-components";

type Props = {
  user: User;
};
const UserAuditLogLink: FC<Props> = ({ user }) => {
  const [t] = useTranslation("plugins");
  const { data: index, isLoading } = useIndex();

  if (isLoading || !index || !index._links.auditLog) {
    return null;
  }

  return (
    <tr>
      <th>{t("scm-audit-log-plugin.user.key")}</th>
      <td>
        <a href={urls.withContextPath("/admin/audit-log/1?label=user&entity=") + user.name}>
          {t("scm-audit-log-plugin.user.link")}
        </a>
      </td>
    </tr>
  );
};
export default UserAuditLogLink;
