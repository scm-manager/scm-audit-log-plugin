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
import {
  ErrorNotification,
  InputField,
  Level,
  LinkPaginator,
  Loading,
  Select,
  Title,
  urls
} from "@scm-manager/ui-components";
import { Filters, useAuditLog } from "./useAuditLog";
import { Redirect, useLocation, useRouteMatch } from "react-router-dom";
import { Link, Links } from "@scm-manager/ui-types";
import { Button } from "@scm-manager/ui-buttons";
import queryString from "query-string";
import styled from "styled-components";

const FullWidthSelect = styled(Select)`
  width: 100%;
  select {
    width: 100%;
  }
`;

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
  const [filters, setFilters] = useState<Filters>({});
  const [entity, setEntity] = useState<string>(searchParams.entity || "");
  const [username, setUsername] = useState<string>(searchParams.username || "");
  const [label, setLabel] = useState<string>(searchParams.label || "");
  const [action, setAction] = useState<string>(searchParams.action || "");
  const [from, setFrom] = useState<string>("");
  const [to, setTo] = useState<string>("");

  const { data, error, isLoading } = useAuditLog(page, filters);

  const filterForm = (
    <>
      <div className="columns">
        <div className="column">
          <InputField
            label={t("scm-audit-log-plugin.filter.entity.label")}
            helpText={t("scm-audit-log-plugin.filter.entity.helpText")}
            value={entity}
            onChange={setEntity}
          />
        </div>
        <div className="column">
          <FullWidthSelect
            label={t("scm-audit-log-plugin.filter.label.label")}
            helpText={t("scm-audit-log-plugin.filter.label.helpText")}
            value={label}
            options={(["", ...(data?._embedded?.labels as { labels: string[] })?.labels] || []).map(label => ({
              label,
              value: label
            }))}
            onChange={setLabel}
          />
        </div>
        <div className="column">
          <InputField
            label={t("scm-audit-log-plugin.filter.username.label")}
            helpText={t("scm-audit-log-plugin.filter.username.helpText")}
            value={username}
            onChange={setUsername}
          />
        </div>
      </div>
      <div className="columns">
        <div className="column">
          <FullWidthSelect
            label={t("scm-audit-log-plugin.filter.action.label")}
            helpText={t("scm-audit-log-plugin.filter.action.helpText")}
            value={action}
            options={["", "created", "modified", "deleted"].map(label => ({
              label,
              value: label
            }))}
            onChange={setAction}
          />
        </div>
        <div className="column">
          <InputField
            label={t("scm-audit-log-plugin.filter.from.label")}
            helpText={t("scm-audit-log-plugin.filter.from.helpText")}
            type="date"
            value={from}
            onChange={setFrom}
          />
        </div>
        <div className="column">
          <InputField
            label={t("scm-audit-log-plugin.filter.to.label")}
            helpText={t("scm-audit-log-plugin.filter.to.helpText")}
            type="date"
            value={to}
            onChange={setTo}
          />
        </div>
      </div>
    </>
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
      <Level
        right={
          <div className="buttons">
            <Button variant="primary" onClick={() => setFilters({ entity, username, label, from, to, action })}>
              {t("scm-audit-log-plugin.filter.applyButton")}
            </Button>
            <Button
              onClick={() => {
                setEntity("");
                setLabel("");
                setUsername("");
                setAction("");
                setFrom("");
                setTo("");
              }}
            >
              {t("scm-audit-log-plugin.filter.resetButton")}
            </Button>
          </div>
        }
      />
      <hr />
      <Level right={<ExportButton links={links} filters={filters} />} />
      <pre>{data?._embedded?.entries.map(e => e.entry + "\n")}</pre>
      <hr />
      <LinkPaginator collection={data} page={page} />
    </>
  );
};

export default AuditLog;
