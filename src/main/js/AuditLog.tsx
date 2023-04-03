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
import React, { FC, useState } from "react";
import { useTranslation } from "react-i18next";
import { ErrorNotification, Level, LinkPaginator, Loading, Title, urls } from "@scm-manager/ui-components";
import { Filters, useAuditLog } from "./useAuditLog";
import { Redirect, useLocation, useRouteMatch } from "react-router-dom";
import { Link, Links } from "@scm-manager/ui-types";
import queryString from "query-string";
import { Form } from "@scm-manager/ui-forms";

const ExportButton: FC<{ links: Links; filters: Filters }> = ({ links, filters }) => {
  const [t] = useTranslation("plugins");
  if (links.auditLogCsvExport) {
    let link = (links.auditLogCsvExport as Link).href;
    for (const filter of Object.entries(filters)) {
      if (filter[1]) {
        if (!link.includes("?")) {
          link += "?";
        } else {
          link += "&";
        }
        link += `${filter[0]}=${filter[1]}`;
      }
    }
    return (
      <a className="button" download={"scm-audit-log_" + Date.now() + ".csv"} href={link}>
        {t("scm-audit-log-plugin.filter.exportButton")}
      </a>
    );
  }
  return null;
};

const AuditLog: FC<{ links: Links }> = ({ links }) => {
  const [t] = useTranslation("plugins");
  const match = useRouteMatch();
  const page = urls.getPageFromMatch(match);
  const location = useLocation();
  const searchParams = queryString.parse(location.search) as {
    entity?: string;
    username?: string;
    label?: string;
    action?: string;
  };
  const [filters, setFilters] = useState<Filters>({
    entity: searchParams.entity || "",
    username: searchParams.username || "",
    label: searchParams.label || "",
    action: searchParams.action || "",
    from: "",
    to: ""
  });

  const { data, error, isLoading } = useAuditLog(page, filters);

  const filterForm = (
    <Form<Filters>
      onSubmit={setFilters}
      defaultValues={filters}
      translationPath={["plugins", "scm-audit-log-plugin.filter"]}
      withResetTo={{ entity: "", username: "", label: "", action: "", from: "", to: "" }}
    >
      <Form.Row>
        <Form.Input name="entity" />
        <Form.Select
          name="label"
          options={(["", ...(data?._embedded?.labels as { labels: string[] })?.labels] || []).map(label => ({
            label,
            value: label
          }))}
        />
        <Form.Input name="username" />
      </Form.Row>
      <Form.Row>
        <Form.Select
          name="action"
          options={["", "created", "modified", "deleted"].map(label => ({
            label,
            value: label
          }))}
        />
        <Form.Input name="from" type="date" />
        <Form.Input name="to" type="date" />
      </Form.Row>
    </Form>
  );

  if (error) {
    return <ErrorNotification error={error} />;
  }

  if (isLoading || !data) {
    // @ts-ignore annoying....
    return <Loading />;
  }

  if (data && data.pageTotal < page && page > 1) {
    return <Redirect to={`/admin/audit-log/${data.pageTotal}`} />;
  }

  return (
    <>
      <Title title={t("scm-audit-log-plugin.title")} />
      {filterForm}
      <hr />
      <Level right={<ExportButton links={links} filters={filters} />} />
      <pre>{data?._embedded?.entries.map(e => e.entry + "\n")}</pre>
      <hr />
      <LinkPaginator collection={data} page={page} />
    </>
  );
};

export default AuditLog;
