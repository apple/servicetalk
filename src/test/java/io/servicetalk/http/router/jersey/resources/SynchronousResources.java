/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.router.jersey.resources;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.router.jersey.AbstractResourceTest.TestFiltered;
import io.servicetalk.transport.api.ConnectionContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.router.jersey.TestUtils.asChunkPublisher;
import static io.servicetalk.http.router.jersey.TestUtils.getContentAsString;
import static io.servicetalk.http.router.jersey.resources.SynchronousResources.PATH;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.joining;
import static javax.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.accepted;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;

/**
 * Synchronous (in JAX-RS lingo) resources.
 */
@Path(PATH)
public class SynchronousResources {
    @Context
    private ConnectionContext ctx;

    public static final String PATH = "/sync";

    @Produces(TEXT_PLAIN)
    @Path("/uris/{type:(relative|absolute)}")
    @GET
    public String getUri(@PathParam("type") final String uriType, @Context final UriInfo uriInfo) {
        if (uriType.charAt(0) == 'a') {
            return uriInfo.getAbsolutePathBuilder().build().toString();
        } else {
            return UriBuilder.fromResource(AsynchronousResources.class).path("/text").build().toString();
        }
    }

    @Path("/statuses/444")
    @GET
    public Response get444Status() {
        return status(444, "Three fours!").build();
    }

    @Produces(TEXT_PLAIN)
    @Path("/matrix/{pathSegment:ps}/params")
    @GET
    public String objectsByCategory(@PathParam("pathSegment") final PathSegment pathSegment,
                                    @MatrixParam("mp") final List<String> matrixParams) {
        final MultivaluedMap<String, String> matrixParameters = pathSegment.getMatrixParameters();
        final String categorySegmentPath = pathSegment.getPath();
        return "GOT: "
                + matrixParameters.keySet().stream().map(k -> k + "="
                + matrixParameters.get(k).stream().collect(joining(","))).collect(joining(","))
                + " & " + categorySegmentPath
                + " & " + matrixParams.stream().collect(joining(","));
    }

    @Produces(TEXT_PLAIN)
    @Path("/bogus-chunked")
    @GET
    public Response bogusChunked() {
        return ok("foo").header("Transfer-Encoding", "chunked").build();
    }

    @Produces(TEXT_PLAIN)
    @Path("/servicetalk-request")
    @GET
    public String serviceTalkRequest(@Context final HttpRequest<HttpPayloadChunk> serviceTalkRequest) {
        return "GOT: " + serviceTalkRequest.getRequestTarget();
    }

    @Produces(TEXT_PLAIN)
    @Path("/text")
    @GET
    public String getText(@Nullable @QueryParam("qp") final String qp,
                          @Nullable @HeaderParam("hp") final String hp) {
        if ("throw-not-translated".equals(qp)) {
            throw DELIBERATE_EXCEPTION;
        } else if ("throw-translated".equals(qp)) {
            throw new WebApplicationException("Deliberate Exception", CONFLICT);
        }

        return "GOT: " + qp + " & " + hp;
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text")
    @POST
    public String postText(final String requestContent) {
        return "GOT: " + requestContent;
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-response")
    @GET
    public Response getTextResponse(@Context final HttpHeaders headers) {
        return noContent().header("X-Test", headers.getHeaderString("hdr")).build();
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-response")
    @POST
    public Response postTextResponse(final String requestContent) {
        return accepted("GOT: " + requestContent).build();
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text-strin-pubout")
    @POST
    public Publisher<HttpPayloadChunk> postTextStrInPubOut(final String requestContent) {
        return asChunkPublisher("GOT: " + requestContent, ctx.getBufferAllocator(), ctx.getExecutor());
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text-pubin-strout")
    @POST
    public String postTextPubInStrOut(final Publisher<HttpPayloadChunk> requestContent) {
        return "GOT: " + getContentAsString(requestContent);
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text-pubin-pubout")
    @POST
    public Publisher<HttpPayloadChunk> postTextPubInPubOut(final Publisher<HttpPayloadChunk> requestContent) {
        return asChunkPublisher("GOT: ", ctx.getBufferAllocator(), ctx.getExecutor()).concatWith(requestContent);
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-pub-response")
    @GET
    public Response getTextPubResponse(@QueryParam("i") final int i) {
        final String contentString = "GOT: " + i;
        final Publisher<HttpPayloadChunk> responseContent =
                asChunkPublisher(contentString, ctx.getBufferAllocator(), ctx.getExecutor());

        // Wrap content Publisher to capture its generic type (i.e. HttpPayloadChunk)
        final GenericEntity<Publisher<HttpPayloadChunk>> entity = new GenericEntity<Publisher<HttpPayloadChunk>>(responseContent) {
        };
        return status(i)
                // We know the content length so we set it, otherwise the response is chunked
                .header(CONTENT_LENGTH, contentString.length())
                .entity(entity)
                .build();
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text-oio-streams")
    @POST
    public StreamingOutput postTextOioStreams(final InputStream requestContent) {
        return output -> {
            output.write("GOT: ".getBytes(UTF_8));
            int b;
            while ((b = requestContent.read()) >= 0) {
                output.write(b);
            }
            output.flush();
        };
    }

    @TestFiltered
    @Produces(TEXT_PLAIN)
    @Path("/filtered")
    @POST
    public String filtered(final String requestContent) {
        return "GOT: " + requestContent;
    }

    @Produces(APPLICATION_JSON)
    @Path("/json")
    @GET
    public Map<String, Object> getJson() {
        return singletonMap("foo", "bar1");
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-response")
    @PUT
    public Response putJsonResponse(final Map<String, Object> requestContent) {
        final Map<String, Object> responseContent = new HashMap<>(requestContent);
        responseContent.put("foo", "bar2");
        return accepted(responseContent).header("X-Test", "test-header").build();
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-mapin-pubout")
    @POST
    public Publisher<HttpPayloadChunk> postJsonMapInPubOut(final Map<String, Object> requestContent)
            throws IOException {
        final Map<String, Object> responseContent = new HashMap<>(requestContent);
        responseContent.put("foo", "bar3");
        return asChunkPublisher(new ObjectMapper().writeValueAsBytes(responseContent),
                ctx.getBufferAllocator(), ctx.getExecutor());
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-pubin-mapout")
    @POST
    public Map<String, Object> postJsonPubInMapOut(final Publisher<HttpPayloadChunk> requestContent)
            throws IOException {
        @SuppressWarnings("unchecked")
        final Map<String, Object> responseContent =
                new HashMap<>(new ObjectMapper().readValue(getContentAsString(requestContent), Map.class));
        responseContent.put("foo", "bar4");
        return responseContent;
    }

    @Produces(APPLICATION_JSON)
    @Path("/security-context")
    @GET
    public SecurityContext serviceTalkRequest(@Context final SecurityContext securityContext) {
        return securityContext;
    }
}
