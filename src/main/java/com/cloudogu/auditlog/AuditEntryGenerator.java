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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sonia.scm.auditlog.AuditEntry;
import sonia.scm.auditlog.EntryCreationContext;
import sonia.scm.xml.XmlCipherStringAdapter;
import sonia.scm.xml.XmlEncryptionAdapter;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.cloudogu.auditlog.DefaultAuditLogService.resolveAction;

public class AuditEntryGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(AuditEntryGenerator.class);

  private static final String REPOSITORY_LABEL = "repository";
  private static final String NAMESPACE_LABEL = "namespace";
  private static final PrettyValuePrinter PRETTY_VALUE_PRINTER = PrettyValuePrinter.getDefault();
  private final Javers javers = JaversBuilder.javers().build();

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
        if (!label.equalsIgnoreCase(REPOSITORY_LABEL) && !label.equalsIgnoreCase(NAMESPACE_LABEL)) {
          builder.append(label).append(" ");
        }
      }
      if (Arrays.stream(labels).anyMatch(l -> l.equalsIgnoreCase(REPOSITORY_LABEL))) {
        builder.append("for repository '").append(entityName).append("'");
      } else if (Arrays.stream(labels).anyMatch(l -> l.equalsIgnoreCase(NAMESPACE_LABEL))) {
        builder.append("for namespace '").append(entityName).append("'");
      }
    }

    if (!action.equalsIgnoreCase("deleted")) {
      handleDiff(context, builder);
    }

    return builder.toString();
  }

  private <T> void handleDiff(EntryCreationContext<T> context, StringBuilder builder) {
    T object = getObject(context);
    List<ChangesByObject> changes = javers.compare(context.getOldObject(), context.getObject()).groupByObject();
    String[] ignoredFields;
    String[] maskedFields;
    AuditEntry annotation = object.getClass().getAnnotation(AuditEntry.class);
    if (annotation != null) {
      ignoredFields = annotation.ignoredFields();
      maskedFields = annotation.maskedFields();
    } else {
      ignoredFields = new String[]{};
      maskedFields = new String[]{};
    }

    if (hasOnlyIgnoredFieldsChanged(changes, ignoredFields)) {
      builder.setLength(0);
      return;
    }

    builder.append("\nDiff:\n");

    changes.forEach(it ->
      it.getPropertyChanges().forEach(c -> {
          if (shouldMaskChange(object, maskedFields, c.getPropertyName())) {
            if (resolveAction(context).equals("modified")) {
              builder.append("  - '").append(c.getPropertyName()).append("' changed: ").append("********").append("\n");
            } else {
              builder.append("  - '").append(c.getPropertyName()).append("' = ").append("********").append("\n");
            }
          } else if (!shouldIgnoreField(ignoredFields, c.getPropertyName())) {
            builder.append("  - ").append(c.prettyPrint(PRETTY_VALUE_PRINTER).replace("\n", "\n  ")).append("\n");
          }
        }
      )
    );
  }

  <T> T getObject(EntryCreationContext<T> context) {
    return context.getObject() != null ? context.getObject() : context.getOldObject();
  }

  private boolean hasOnlyIgnoredFieldsChanged(List<ChangesByObject> changes, String[] ignoredFields) {
    HashSet<String> changedFields = new HashSet<>();
    changes.forEach(c -> c.getPropertyChanges().forEach(d -> changedFields.add(d.getPropertyName())));
    Arrays.asList(ignoredFields).forEach(changedFields::remove);

    return changedFields.isEmpty();

  }

  private boolean shouldIgnoreField(String[] ignoredFields, String fieldName) {
    return Arrays.asList(ignoredFields).contains(fieldName);
  }

  private <T> boolean shouldMaskChange(T object, String[] maskedFields, String fieldName) {
    if (Arrays.asList(maskedFields).contains(fieldName)) {
      return true;
    } else {
      try {
        XmlJavaTypeAdapter adapterClass = object.getClass().getDeclaredField(fieldName).getAnnotation(XmlJavaTypeAdapter.class);
        if (adapterClass != null) {
          return adapterClass.value().isAssignableFrom(XmlCipherStringAdapter.class) || adapterClass.value().isAssignableFrom(XmlEncryptionAdapter.class);
        }
      } catch (NoSuchFieldException e) {
        LOG.debug("Could not resolve annotation for field " + fieldName, e);
      }
    }
    return false;
  }
}
