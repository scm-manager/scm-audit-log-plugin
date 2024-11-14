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
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.javers.common.string.PrettyValuePrinter;
import org.javers.core.ChangesByObject;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.changetype.PropertyChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sonia.scm.auditlog.AuditEntry;
import sonia.scm.auditlog.EntryCreationContext;
import sonia.scm.xml.XmlCipherStringAdapter;
import sonia.scm.xml.XmlEncryptionAdapter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.cloudogu.auditlog.EntryContextResolver.resolveAction;

public class AuditEntryGenerator {

  private static final List<String> AUTOMASKED_FIELD_NAMES = List.of(
    "password",
    "pw",
    "pwd",
    "token",
    "secret",
    "key",
    "private"
  );

  private static final AuditEntry DEFAULT_AUDIT_ENTRY = getDefaultAuditEntry();

  private static final Logger LOG = LoggerFactory.getLogger(AuditEntryGenerator.class);

  private static final String REPOSITORY_LABEL = "repository";
  private static final String NAMESPACE_LABEL = "namespace";
  private static final String USER_LABEL = "user";
  private static final String GROUP_LABEL = "group";
  private static final PrettyValuePrinter PRETTY_VALUE_PRINTER = PrettyValuePrinter.getDefault();
  private final Javers javers = JaversBuilder.javers().build();
  private final ZoneId zoneId;

  AuditEntryGenerator() {
    this(ZoneId.systemDefault());
  }

  @VisibleForTesting
  AuditEntryGenerator(ZoneId zoneId) {
    this.zoneId = zoneId;
  }

  private static AuditEntry getDefaultAuditEntry() {
    return new AuditEntry() {

      @Override
      public Class<? extends Annotation> annotationType() {
        return AuditEntry.class;
      }

      @Override
      public String[] labels() {
        return new String[0];
      }

      @Override
      public String[] maskedFields() {
        return new String[0];
      }

      @Override
      public String[] ignoredFields() {
        return new String[0];
      }

      @Override
      public boolean ignore() {
        return false;
      }

      @Override
      public boolean autoMask() {
        return true;
      }
    };
  }

  <T> String generate(EntryCreationContext<T> context, Instant timestamp, String username, String action, String entityName, String[] labels) {
    StringBuilder builder = new StringBuilder()
      .append(timestamp.truncatedTo(ChronoUnit.SECONDS).atZone(zoneId)).append(" ")
      .append("[").append(action.toUpperCase()).append("] '")
      .append(username).append("' ")
      .append(action).append(" ");

    if (labels.length == 1) {
      builder.append(labels[0]);
      if (!Strings.isNullOrEmpty(entityName)) {
        builder.append(" '").append(entityName).append("'");
      }
    } else {
      for (String label : labels) {
        if (!label.equalsIgnoreCase(REPOSITORY_LABEL)
          && !label.equalsIgnoreCase(NAMESPACE_LABEL)
          && !label.equalsIgnoreCase(GROUP_LABEL)
          && !label.equalsIgnoreCase(USER_LABEL)) {
          builder.append(label).append(" ");
        }
      }
      if (Arrays.stream(labels).anyMatch(l -> l.equalsIgnoreCase(REPOSITORY_LABEL))) {
        builder.append("for repository '").append(entityName).append("'");
      } else if (Arrays.stream(labels).anyMatch(l -> l.equalsIgnoreCase(NAMESPACE_LABEL))) {
        builder.append("for namespace '").append(entityName).append("'");
      } else if (Arrays.stream(labels).anyMatch(l -> l.equalsIgnoreCase(GROUP_LABEL))) {
        builder.append("for group '").append(entityName).append("'");
      } else if (Arrays.stream(labels).anyMatch(l -> l.equalsIgnoreCase(USER_LABEL))) {
        builder.append("for user '").append(entityName).append("'");
      }
    }

    if (!action.equalsIgnoreCase("deleted")) {
      handleDiff(context, builder);
    }

    return builder.toString();
  }

  private <T> void handleDiff(EntryCreationContext<T> context, StringBuilder builder) {
    List<ChangesByObject> changes = javers.compare(context.getOldObject(), context.getObject()).groupByObject();

    if (hasOnlyIgnoredFieldsChanged(changes)) {
      builder.setLength(0);
      return;
    }

    builder.append("\nDiff:\n");

    changes.forEach(it ->
      it.getPropertyChanges().forEach(c -> {
          if (shouldMaskChange(c) || shouldAutoMaskChange(c)) {
            if (resolveAction(context).equals("modified")) {
              builder.append("  - '").append(c.getPropertyNameWithPath()).append("' changed: ").append("********").append("\n");
            } else {
              builder.append("  - '").append(c.getPropertyNameWithPath()).append("' = ").append("********").append("\n");
            }
          } else if (!shouldIgnoreField(c)) {
            builder.append("  - ").append(c.prettyPrint(PRETTY_VALUE_PRINTER).replace("\n", "\n  ")).append("\n");
          }
        }
      )
    );
  }

  private boolean hasOnlyIgnoredFieldsChanged(List<ChangesByObject> changes) {
    for(ChangesByObject changesByObject : changes) {
      for(PropertyChange changedProperty : changesByObject.getPropertyChanges()) {
        List<String> ignoredFields = List.of(getAuditEntryAnnotation(changedProperty).ignoredFields());

        if(!ignoredFields.contains(changedProperty.getPropertyName())) {
          return false;
        }
      }
    }

    return true;
  }

  private AuditEntry getAuditEntryAnnotation(PropertyChange change) {
    Optional<Object> changedObject = change.getAffectedObject();

    if(changedObject.isEmpty()) {
      return DEFAULT_AUDIT_ENTRY;
    }

    AuditEntry annotation = changedObject.get().getClass().getAnnotation(AuditEntry.class);
    if(annotation == null) {
      return DEFAULT_AUDIT_ENTRY;
    }

    return annotation;
  }

  private <T> boolean shouldMaskChange(PropertyChange<T> change) {
    AuditEntry annotation = getAuditEntryAnnotation(change);

    if (Arrays.asList(annotation.maskedFields()).contains(change.getPropertyName())) {
      return true;
    } else {
      try {
        Optional<Object> changedObject = change.getAffectedObject();
        if(changedObject.isEmpty()) {
          return false;
        }

        XmlJavaTypeAdapter adapterClass = findField(changedObject.get().getClass(), change.getPropertyName()).getAnnotation(XmlJavaTypeAdapter.class);

        if (adapterClass != null) {
          return adapterClass.value().isAssignableFrom(XmlCipherStringAdapter.class) || adapterClass.value().isAssignableFrom(XmlEncryptionAdapter.class);
        }
      } catch (NoSuchFieldException e) {
        LOG.debug("Could not resolve annotation for field " + change.getPropertyName(), e);
      }
    }

    return false;
  }

  private static <T> Field findField(Class<T> clazz, String fieldName) throws NoSuchFieldException {
    try {
      return clazz.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      if (clazz.getSuperclass() != null) {
        return findField(clazz.getSuperclass(), fieldName);
      }
      throw e;
    }
  }

  private <T> boolean shouldAutoMaskChange(PropertyChange<T> change) {
    AuditEntry annotation = getAuditEntryAnnotation(change);

    if(!annotation.autoMask()) {
      return false;
    }

    for(String maskedFieldName : AUTOMASKED_FIELD_NAMES) {
      if(change.getPropertyName().toLowerCase().contains(maskedFieldName)) {
        return true;
      }
    }

    return false;
  }

  private boolean shouldIgnoreField(PropertyChange change) {
    AuditEntry annotation = getAuditEntryAnnotation(change);
    return Arrays.asList(annotation.ignoredFields()).contains(change.getPropertyName());
  }
}
