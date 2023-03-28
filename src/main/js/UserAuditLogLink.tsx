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
