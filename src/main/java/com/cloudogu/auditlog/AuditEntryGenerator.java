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

package com.cloudogu.auditlog;

import com.google.common.base.Strings;
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

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
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
      .append(timestamp.truncatedTo(ChronoUnit.SECONDS)).append(" ")
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
