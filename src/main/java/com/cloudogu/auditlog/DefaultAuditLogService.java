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

package com.cloudogu.auditlog;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import sonia.scm.auditlog.EntryCreationContext;
import sonia.scm.plugin.Extension;
import sonia.scm.store.Condition;
import sonia.scm.store.QueryableMutableStore;
import sonia.scm.store.QueryableStore;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.cloudogu.auditlog.EntryContextResolver.resolveAction;
import static com.cloudogu.auditlog.EntryContextResolver.resolveEntityName;
import static com.cloudogu.auditlog.EntryContextResolver.resolveLabels;

@Slf4j
@Extension
@Singleton
public class DefaultAuditLogService implements AuditLogService {

  private final AuditEntryGenerator entryGenerator = new AuditEntryGenerator();
  private final AuditLogDaoStoreFactory daoStoreFactory;
  private final LabelDaoStoreFactory labelDaoStoreFactory;

  @Inject
  public DefaultAuditLogService(AuditLogDaoStoreFactory daoStoreFactory, LabelDaoStoreFactory labelDaoStoreFactory) {
    this.daoStoreFactory = daoStoreFactory;
    this.labelDaoStoreFactory = labelDaoStoreFactory;
  }

  @Override
  public void createEntry(EntryCreationContext<?> context) {
    String username = getUsername();
    createDBEntry(username, context);
    createLabelsForNewEntry(resolveLabels(context));
  }

  private void createDBEntry(String username, EntryCreationContext<?> context) {
    Instant timestamp = Instant.now();
    String entityName = resolveEntityName(context);
    String action = resolveAction(context);
    String[] labels = resolveLabels(context);
    String entry = entryGenerator.generate(context, timestamp, username, action, entityName, labels);
    if (!Strings.isNullOrEmpty(entry)) {
      AuditLogDao auditLogDao = new AuditLogDao();
      auditLogDao.setTimestamp(timestamp);
      auditLogDao.setEntityName(entityName.toLowerCase());
      auditLogDao.setUsername(!Strings.isNullOrEmpty(username) ? username.toLowerCase() : username);
      auditLogDao.setAction(action.toLowerCase());
      auditLogDao.setLabels(Set.of(labels));
      auditLogDao.setEntry(entry);
      try (QueryableMutableStore<AuditLogDao> mutableStore = daoStoreFactory.getMutable()) {
        mutableStore.put(auditLogDao);
      }
    }
  }

  public Collection<LogEntry> getEntries(AuditLogFilterContext filterContext) {
    PermissionChecker.checkReadAuditLog();
    return getLogEntries(filterContext);
  }

  @VisibleForTesting
  @SuppressWarnings("unchecked")
  List<LogEntry> getLogEntries(AuditLogFilterContext filterContext) {
    QueryableStore<AuditLogDao> daoQueryableStore = daoStoreFactory.get();
    Condition<AuditLogDao>[] filters = resolveAppliedQueryFilters(filterContext);
    return daoQueryableStore.query(filters)
      .orderBy(AuditLogDaoQueryFields.INTERNAL_ID, QueryableStore.Order.DESC)
      .findAll((long) (filterContext.getPageNumber() - 1) * filterContext.getLimit(), filterContext.getLimit())
      .stream()
      .map(dao -> new LogEntry(
        dao.getTimestamp(),
        dao.getEntityName(),
        dao.getUsername(),
        dao.getAction(),
        dao.getEntry()
      ))
      .toList();
  }

  @Override
  public int getTotalEntries(AuditLogFilterContext filterContext) {
    PermissionChecker.checkReadAuditLog();

    QueryableStore<AuditLogDao> daoQueryableStore = daoStoreFactory.get();
    return (int) daoQueryableStore.query(
      resolveAppliedQueryFilters(filterContext)
    ).count();
  }

  @Override
  public Set<String> getLabels() {
    return labelDaoStoreFactory.getMutable().getAll().keySet();
  }

  private static String getUsername() {
    try {
      Object principal = SecurityUtils.getSubject().getPrincipal();
      return principal == null ? null : principal.toString();
    } catch (UnavailableSecurityManagerException e) {
      return null;
    }
  }

  private void createLabelsForNewEntry(String[] labels) {
    try (QueryableMutableStore<LabelDao> store = labelDaoStoreFactory.getMutable()) {
      Arrays.stream(labels).forEach(
        label -> store.put(label.toLowerCase(), new LabelDao())
      );
    }
  }

  @SuppressWarnings("unchecked")
  private Condition<AuditLogDao>[] resolveAppliedQueryFilters(AuditLogFilterContext filterContext) {
    List<Condition<AuditLogDao>> conditions = new ArrayList<>();
    if (filterContext.getFrom() != null) {
      conditions.add(AuditLogDaoQueryFields.TIMESTAMP.after(filterContext.getFrom().minus(1, ChronoUnit.MILLIS)));
    }
    if (filterContext.getTo() != null) {
      conditions.add(AuditLogDaoQueryFields.TIMESTAMP.before(filterContext.getTo().plus(1, ChronoUnit.MILLIS)));
    }
    if (filterContext.getEntity() != null) {
      conditions.add(AuditLogDaoQueryFields.ENTITYNAME.like(filterContext.getEntity()));
    }
    if (filterContext.getUsername() != null) {
      conditions.add(AuditLogDaoQueryFields.USERNAME.like(filterContext.getUsername()));
    }
    if (filterContext.getLabel() != null) {
      conditions.add(AuditLogDaoQueryFields.LABELS.contains(filterContext.getLabel()));
    }
    if (filterContext.getAction() != null) {
      conditions.add(AuditLogDaoQueryFields.ACTION.eq(filterContext.getAction()));
    }
    return conditions.toArray(new Condition[0]);
  }
}
