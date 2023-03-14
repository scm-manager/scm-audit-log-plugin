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
import React, { FC } from "react";
import { useTranslation } from "react-i18next";
import { ErrorNotification, LinkPaginator, Loading, Title, urls } from "@scm-manager/ui-components";
import { useAuditLog } from "./useAuditLog";
import { useRouteMatch } from "react-router-dom";
import { Links } from "@scm-manager/ui-types";

const AuditLog: FC<{ links: Links }> = () => {
  const [t] = useTranslation("plugins");
  const match = useRouteMatch();
  const page = urls.getPageFromMatch(match);

  const { data, error, isLoading } = useAuditLog(page, []);

  if (error) {
    return <ErrorNotification error={error} />;
  }

  if (isLoading || !data) {
    // @ts-ignore annoying....
    return <Loading />;
  }

  console.log(data);

  return (
    <>
      <Title title={t("scm-audit-log-plugin.title")} />
      {/*//TODO implement filter*/}
      <pre>{data?._embedded?.entries.map(e => e.entry + "\n")}</pre>
      <hr />
      <LinkPaginator collection={data} page={page} filter={""} />
    </>
  );
};

export default AuditLog;
