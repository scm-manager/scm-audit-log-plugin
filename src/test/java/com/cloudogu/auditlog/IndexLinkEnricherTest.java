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
