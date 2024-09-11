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

import { Repository } from "@scm-manager/ui-types";
import React, { FC } from "react";
import { useTranslation } from "react-i18next";
import { useIndex } from "@scm-manager/ui-api";
import { urls } from "@scm-manager/ui-components";

type Props = {
  repository: Repository;
};
const RepositoryAuditLogLink: FC<Props> = ({ repository }) => {
  const [t] = useTranslation("plugins");
  const { data: index, isLoading } = useIndex();

  if (isLoading || !index || !index._links.auditLog) {
    return null;
  }

  return (
    <tr>
      <th>{t("scm-audit-log-plugin.repository.key")}</th>
      <td>
        <a href={urls.withContextPath("/admin/audit-log/1?label=repository&entity=") + `${repository.namespace}/${repository.name}`}>
          {t("scm-audit-log-plugin.repository.link")}
        </a>
      </td>
    </tr>
  );
};
export default RepositoryAuditLogLink;
