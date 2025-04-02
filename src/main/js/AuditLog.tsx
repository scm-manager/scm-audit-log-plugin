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

import React, { FC, useState } from "react";
import { useTranslation } from "react-i18next";
import { ErrorNotification, Level, LinkPaginator, Loading, Title, urls } from "@scm-manager/ui-components";
import { useDocumentTitle } from "@scm-manager/ui-core";
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
  const [t] = useTranslation("plugins");
  useDocumentTitle(
    data?.pageTotal && data.pageTotal > 1 && page
      ? t("scm-audit-log-plugin.auditLogWithPage", { page, total: data.pageTotal })
      : t("scm-audit-log-plugin.title"),
  );

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
