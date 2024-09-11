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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import org.github.sdorra.jse.ShiroExtension;
import org.github.sdorra.jse.SubjectAware;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sonia.scm.api.v2.resources.ScmPathInfoStore;
import sonia.scm.web.JsonMockHttpResponse;
import sonia.scm.web.RestDispatcher;

import jakarta.inject.Provider;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, ShiroExtension.class})
@SubjectAware(value = "trillian")
class AuditLogResourceTest {

  @Mock
  private AuditLogService service;
  @Mock
  private Provider<ScmPathInfoStore> scmPathInfoStoreProvider;

  @InjectMocks
  private AuditLogResource resource;

  private RestDispatcher restDispatcher;

  @BeforeEach
  void init() {
    restDispatcher = new RestDispatcher();
    restDispatcher.addSingletonResource(resource);
    ScmPathInfoStore scmPathInfoStore = new ScmPathInfoStore();
    scmPathInfoStore.set(() -> URI.create(""));
    lenient().when(scmPathInfoStoreProvider.get()).thenReturn(scmPathInfoStore);
  }

  @Test
  void shouldGetEntriesAsText() throws URISyntaxException, UnsupportedEncodingException {
    when(service.getEntries(any())).thenReturn(ImmutableList.of(new LogEntry(Instant.ofEpochMilli(1700000000), "admins", "trillian", "modified", "2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified group 'admins'\n" +
      "Diff:\n" +
      "* changes on sonia.scm.group.Group/ :\n" +
      "  - 'type' changed: 'external' -> 'internal'"), new LogEntry(Instant.ofEpochMilli(1700000000), "admins", "trillian", "modified", "2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified group 'admins'\n" +
      "Diff:\n" +
      "* changes on sonia.scm.group.Group/ :\n" +
      "  - 'type' changed: 'internal' -> 'external'")));

    MockHttpRequest request = MockHttpRequest.get("/v2/audit-log/export/text-plain");
    MockHttpResponse response = new MockHttpResponse();

    restDispatcher.invoke(request, response);

    assertThat(response.getContentAsString()).isEqualTo("2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified group 'admins'\n" +
      "Diff:\n" +
      "* changes on sonia.scm.group.Group/ :\n" +
      "  - 'type' changed: 'external' -> 'internal'\n" +
      "2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified group 'admins'\n" +
      "Diff:\n" +
      "* changes on sonia.scm.group.Group/ :\n" +
      "  - 'type' changed: 'internal' -> 'external'\n");
  }

  @Test
  void shouldGetEntriesAsTextWithFilter() throws URISyntaxException {
    MockHttpRequest request = MockHttpRequest.get("/v2/audit-log/export/text-plain?entity=scmadmin&username=trillian&from=2023-01-01&to=2023-02-01&label=jenkins");
    MockHttpResponse response = new MockHttpResponse();

    restDispatcher.invoke(request, response);

    verify(service).getEntries(argThat(filterContext -> {
      assertThat(filterContext.getEntity()).isEqualTo("scmadmin");
      assertThat(filterContext.getUsername()).isEqualTo("trillian");
      assertThat(filterContext.getLabel()).isEqualTo("jenkins");
      assertThat(filterContext.getFrom()).hasToString("2023-01-01");
      // Day increased by one to 'include' all matches
      assertThat(filterContext.getTo()).hasToString("2023-02-02");
      return true;
    }));
  }

  @Test
  void shouldGetEntriesAsCsv() throws URISyntaxException, UnsupportedEncodingException {
    when(service.getEntries(any())).thenReturn(ImmutableList.of(new LogEntry(Instant.ofEpochMilli(1700000000), "admins", "trillian", "modified", "2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified group 'admins'\n" +
      "Diff:\n" +
      "* changes on sonia.scm.group.Group/ :\n" +
      "  - 'type' changed: 'external' -> 'internal'"), new LogEntry(Instant.ofEpochMilli(1700000000), "admins", "trillian", "modified", "2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified group 'admins'\n" +
      "Diff:\n" +
      "* changes on sonia.scm.group.Group/ :\n" +
      "  - 'type' changed: 'internal' -> 'external'")));

    MockHttpRequest request = MockHttpRequest.get("/v2/audit-log/export/csv");
    MockHttpResponse response = new MockHttpResponse();

    restDispatcher.invoke(request, response);

    assertThat(response.getContentAsString()).contains("Timestamp,Username,Action,Entity,Diff")
      .contains("trillian,modified,admins,2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified group 'admins' Diff: * changes on sonia.scm.group.Group/ :   - 'type' changed: 'external' -> 'internal'")
      .contains("trillian,modified,admins,2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified group 'admins' Diff: * changes on sonia.scm.group.Group/ :   - 'type' changed: 'internal' -> 'external'");
  }

  @Test
  void shouldGetEntriesAsCsvWithFilter() throws URISyntaxException {
    MockHttpRequest request = MockHttpRequest.get("/v2/audit-log/export/csv?entity=scmadmin&username=trillian&from=2023-01-01&to=2023-02-01&label=jenkins");
    MockHttpResponse response = new MockHttpResponse();

    restDispatcher.invoke(request, response);

    verify(service).getEntries(argThat(filterContext -> {
      assertThat(filterContext.getEntity()).isEqualTo("scmadmin");
      assertThat(filterContext.getUsername()).isEqualTo("trillian");
      assertThat(filterContext.getLabel()).isEqualTo("jenkins");
      assertThat(filterContext.getFrom()).hasToString("2023-01-01");
      // Day increased by one to 'include' all matches
      assertThat(filterContext.getTo()).hasToString("2023-02-02");
      return true;
    }));
  }

  @Test
  void shouldGetEntriesAsJson() throws URISyntaxException {
    when(service.getEntries(any())).thenReturn(ImmutableList.of(
      new LogEntry(Instant.ofEpochMilli(1700000000), "admins", "trillian", "modified",
        "2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified group 'admins'\n" +
          "Diff:\n" +
          "* changes on sonia.scm.group.Group/ :\n" +
          "  - 'type' changed: 'external' -> 'internal'"),
      new LogEntry(Instant.ofEpochMilli(1700000000), "admins", "trillian", "modified",
        "2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified group 'admins'\n" +
          "Diff:\n" +
          "* changes on sonia.scm.group.Group/ :\n" +
          "  - 'type' changed: 'internal' -> 'external'")));

    when(service.getTotalEntries(any())).thenReturn(1);

    MockHttpRequest request = MockHttpRequest.get("/v2/audit-log");
    JsonMockHttpResponse response = new JsonMockHttpResponse();

    restDispatcher.invoke(request, response);

    JsonNode content = response.getContentAsJson();
    assertThat(content.get("page").asInt()).isZero();
    assertThat(content.get("pageTotal").asInt()).isEqualTo(1);

    assertThat(content.get("_embedded").get("entries").get(0).get("entry").textValue())
      .isEqualTo("2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified group 'admins'\n" +
        "Diff:\n" +
        "* changes on sonia.scm.group.Group/ :\n" +
        "  - 'type' changed: 'external' -> 'internal'");
    assertThat(content.get("_embedded").get("entries").get(1).get("entry").textValue())
      .isEqualTo("2023-11-14T22:13:20Z [MODIFIED] 'trillian' modified group 'admins'\n" +
        "Diff:\n" +
        "* changes on sonia.scm.group.Group/ :\n" +
        "  - 'type' changed: 'internal' -> 'external'");

    JsonNode links = content.get("_links");
    assertThat(links.get("self")).isNotNull();
    assertThat(links.get("first")).isNotNull();
    assertThat(links.get("last")).isNotNull();
  }

  @Test
  void shouldGetEntriesAsJsonWithFilter() throws URISyntaxException {
    MockHttpRequest request = MockHttpRequest.get("/v2/audit-log?entity=scmadmin&username=trillian&from=2023-01-01&to=2023-02-01&label=jenkins");
    MockHttpResponse response = new MockHttpResponse();

    restDispatcher.invoke(request, response);

    verify(service).getEntries(argThat(filterContext -> {
      assertThat(filterContext.getEntity()).isEqualTo("scmadmin");
      assertThat(filterContext.getUsername()).isEqualTo("trillian");
      assertThat(filterContext.getLabel()).isEqualTo("jenkins");
      assertThat(filterContext.getFrom()).hasToString("2023-01-01");
      // Day increased by one to 'include' all matches
      assertThat(filterContext.getTo()).hasToString("2023-02-02");
      return true;
    }));
  }

}
