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

import com.google.inject.util.Providers;
import org.github.sdorra.jse.ShiroExtension;
import org.github.sdorra.jse.SubjectAware;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sonia.scm.api.v2.resources.HalAppender;
import sonia.scm.api.v2.resources.HalEnricherContext;
import sonia.scm.api.v2.resources.ScmPathInfoStore;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, ShiroExtension.class})
@SubjectAware(
  value = "trillian"
)
class IndexLinkEnricherTest {

  @Mock
  private ScmPathInfoStore scmPathInfoStore;
  @Mock
  private HalEnricherContext context;
  @Mock
  private HalAppender appender;

  private IndexLinkEnricher enricher;

  @BeforeEach
  void initEnricher() {
    enricher = new IndexLinkEnricher(Providers.of(scmPathInfoStore));
  }

  @Test
  @SubjectAware(permissions = "auditLog:read")
  void shouldAppendReadLink() {
    when(scmPathInfoStore.get()).thenReturn(() -> URI.create("/scm/"));
    enricher.enrich(context, appender);

    verify(appender).appendLink("auditLog", "/scm/v2/audit-log");
    verify(appender).appendLink("auditLogCsvExport", "/scm/v2/audit-log/export/csv");
  }


  @Test
  void shouldNotAppendLink() {
    enricher.enrich(context, appender);

    verify(appender, never()).appendLink(any(), any());
  }
}
