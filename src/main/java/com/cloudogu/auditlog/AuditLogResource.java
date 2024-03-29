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

import de.otto.edison.hal.Links;
import de.otto.edison.hal.paging.NumberedPaging;
import de.otto.edison.hal.paging.PagingRel;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import sonia.scm.api.v2.resources.ErrorDto;
import sonia.scm.api.v2.resources.LinkBuilder;
import sonia.scm.api.v2.resources.ScmPathInfoStore;
import sonia.scm.web.VndMediaType;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.List;

import static com.damnhandy.uri.template.UriTemplate.fromTemplate;
import static de.otto.edison.hal.Embedded.embeddedBuilder;
import static de.otto.edison.hal.Links.linkingTo;
import static de.otto.edison.hal.paging.NumberedPaging.oneBasedNumberedPaging;
import static java.util.stream.Collectors.toList;

@Path("v2/audit-log")
@OpenAPIDefinition(tags = {
  @Tag(name = "Audit Log", description = "Audit log related endpoints")
})
public class AuditLogResource {

  private final AuditLogService auditLogService;
  private final Provider<ScmPathInfoStore> scmPathInfoStoreProvider;

  @Inject
  public AuditLogResource(AuditLogService auditLogService, Provider<ScmPathInfoStore> scmPathInfoStoreProvider) {
    this.auditLogService = auditLogService;
    this.scmPathInfoStoreProvider = scmPathInfoStoreProvider;
  }

  @GET
  @Produces("text/csv")
  @Path("/export/csv")
  @Operation(summary = "Get filtered audit log entries as csv", description = "Returns a pre-formatted csv of the filtered audit log entries.", tags = "Audit Log")
  @ApiResponse(
    responseCode = "200",
    description = "success",
    content = @Content(
      mediaType = "text/csv"
    )
  )
  @ApiResponse(responseCode = "401", description = "not authenticated / invalid credentials")
  @ApiResponse(responseCode = "403", description = "not authorized, the current user does not have the \"auditLog:read\" privilege")
  @ApiResponse(
    responseCode = "500",
    description = "internal server error",
    content = @Content(
      mediaType = VndMediaType.ERROR_TYPE,
      schema = @Schema(implementation = ErrorDto.class)
    )
  )
  public StreamingOutput exportAuditLogAsCsv(@DefaultValue("1") @QueryParam("pageNumber") int page,
                                             @DefaultValue("999999999") @QueryParam("pageSize") int limit,
                                             @QueryParam("entity") String entity,
                                             @QueryParam("username") String username,
                                             @QueryParam("from") String from,
                                             @QueryParam("to") String to,
                                             @QueryParam("label") String label,
                                             @QueryParam("action") String action
  ) {
    return output -> {
      try (PrintWriter out = new PrintWriter(output)) {
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
          .setHeader("Timestamp", "Username", "Action", "Entity", "Diff")
          .build();
        CSVPrinter csvPrinter = new CSVPrinter(out, csvFormat);
        auditLogService.getEntries(new AuditLogFilterContext(page, limit, entity, username, from, to, label, action))
          .forEach(e -> {
              try {
                csvPrinter.printRecord(e.getTimestamp(), e.getUser(), e.getAction(), e.getEntity(), e.getEntry().replace("\n", " "));
              } catch (IOException ex) {
                throw new AuditLogException("Export as csv failed ", ex);
              }
            }
          );
      }
    };
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @Path("/export/text-plain")
  @Operation(summary = "Get filtered audit log entries as text", description = "Returns a pre-formatted text of the filtered audit log entries.", tags = "Audit Log")
  @ApiResponse(
    responseCode = "200",
    description = "success",
    content = @Content(
      mediaType = MediaType.TEXT_PLAIN
    )
  )
  @ApiResponse(responseCode = "401", description = "not authenticated / invalid credentials")
  @ApiResponse(responseCode = "403", description = "not authorized, the current user does not have the \"auditLog:read\" privilege")
  @ApiResponse(
    responseCode = "500",
    description = "internal server error",
    content = @Content(
      mediaType = VndMediaType.ERROR_TYPE,
      schema = @Schema(implementation = ErrorDto.class)
    )
  )
  public StreamingOutput exportAuditLogWithLineBreak(@DefaultValue("1") @QueryParam("pageNumber") int page,
                            @DefaultValue("999999999") @QueryParam("pageSize") int limit,
                            @QueryParam("entity") String entity,
                            @QueryParam("username") String username,
                            @QueryParam("from") String from,
                            @QueryParam("to") String to,
                            @QueryParam("label") String label,
                            @QueryParam("action") String action
  ) {
    return output -> {
      try (PrintWriter out = new PrintWriter(output)) {
        auditLogService.getEntries(new AuditLogFilterContext(page, limit, entity, username, from, to, label, action))
          .forEach(e -> out.println(e.getEntry()));
      }
    };
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("")
  @Operation(summary = "Get filtered audit log entries as json with pagination support", description = "Returns json with list of audit log entries.", tags = "Audit Log")
  @ApiResponse(
    responseCode = "200",
    description = "success",
    content = @Content(
      mediaType = MediaType.APPLICATION_JSON
    )
  )
  @ApiResponse(responseCode = "401", description = "not authenticated / invalid credentials")
  @ApiResponse(responseCode = "403", description = "not authorized, the current user does not have the \"auditLog:read\" privilege")
  @ApiResponse(
    responseCode = "500",
    description = "internal server error",
    content = @Content(
      mediaType = VndMediaType.ERROR_TYPE,
      schema = @Schema(implementation = ErrorDto.class)
    )
  )
  public Response getPaginatedAuditLog(@DefaultValue("1") @QueryParam("pageNumber") int page,
                                       @DefaultValue("100") @QueryParam("pageSize") int limit,
                                       @QueryParam("entity") String entity,
                                       @QueryParam("username") String username,
                                       @QueryParam("from") String from,
                                       @QueryParam("to") String to,
                                       @QueryParam("label") String label,
                                       @QueryParam("action") String action
  ) {
    AuditLogFilterContext filterContext = new AuditLogFilterContext(page, limit, entity, username, from, to, label, action);
    List<LogEntryDto> entries = auditLogService.getEntries(filterContext)
      .stream()
      .map(LogEntryDto::from)
      .collect(toList());
    return Response.ok().entity(createDtoWithPagination(filterContext, entries)).build();
  }

  private AuditLogDto createDtoWithPagination(AuditLogFilterContext filterContext, List<LogEntryDto> entries) {
    int totalEntries = auditLogService.getTotalEntries(filterContext);
    NumberedPaging paging = oneBasedNumberedPaging(filterContext.getPageNumber(), filterContext.getLimit(), totalEntries);
    AuditLogDto auditLogDto = new AuditLogDto(
      createLinks(paging, filterContext),
      embeddedBuilder()
        .with("entries", entries)
        .with("labels", new LabelsDto(auditLogService.getLabels()))
        .build()
    );
    auditLogDto.setPage(filterContext.getPageNumber() - 1);
    auditLogDto.setPageTotal(computePageTotal(filterContext.getLimit(), totalEntries));
    return auditLogDto;
  }

  private int computePageTotal(int pageSize, int total) {
    if (total % pageSize > 0) {
      return total / pageSize + 1;
    } else {
      return total / pageSize;
    }
  }

  private Links createLinks(NumberedPaging page, AuditLogFilterContext filterContext) {
    LinkBuilder linkBuilder = new LinkBuilder(scmPathInfoStoreProvider.get().get(), AuditLogResource.class);

    String selfLink = linkBuilder
      .method("getPaginatedAuditLog")
      .parameters(String.valueOf(filterContext.getPageNumber() - 1), String.valueOf(filterContext.getLimit()), "")
      .href();

    Links.Builder linksBuilder = linkingTo()
      .with(page.links(
        fromTemplate(selfLink + "{?pageNumber,pageSize}"),
        EnumSet.allOf(PagingRel.class)));
    return linksBuilder.build();
  }
}
