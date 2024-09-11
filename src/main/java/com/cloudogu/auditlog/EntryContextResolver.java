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

import com.google.common.base.Strings;
import sonia.scm.auditlog.AuditEntry;
import sonia.scm.auditlog.AuditLogEntity;
import sonia.scm.auditlog.EntryCreationContext;

import static java.util.Arrays.stream;
import static java.util.stream.Stream.concat;

public class EntryContextResolver {

  private EntryContextResolver() {}

  static Object resolveObject(EntryCreationContext<?> context) {
    return context.getObject() != null ? context.getObject() : context.getOldObject();
  }

  static String resolveEntityName(EntryCreationContext<?> context) {
    if (!Strings.isNullOrEmpty(context.getEntity())) {
      return Strings.nullToEmpty(context.getEntity());
    }

    Object object = resolveObject(context);
    if (object instanceof AuditLogEntity) {
      return ((AuditLogEntity) object).getEntityName();
    }
    return "";
  }

  static String resolveAction(EntryCreationContext<?> context) {
    if (context.getOldObject() == null) {
      return "created";
    }
    if (context.getObject() == null) {
      return "deleted";
    }
    return "modified";
  }

  static String[] resolveLabels(EntryCreationContext<?> context) {
    Object object = resolveObject(context);
    String[] auditEntryAnnotationLabels;
    AuditEntry annotation = object.getClass().getAnnotation(AuditEntry.class);
    if (annotation != null) {
      auditEntryAnnotationLabels = annotation.labels();
    } else {
      auditEntryAnnotationLabels = new String[]{};
    }
    return concat(stream(auditEntryAnnotationLabels), context.getAdditionalLabels().stream())
      .distinct()
      .toArray(String[]::new);
  }
}
