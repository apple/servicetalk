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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.api.CompositeBuffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.router.jersey.AbstractResourceTest.TestFiltered;
import io.servicetalk.http.router.jersey.TestPojo;
import io.servicetalk.transport.api.ConnectionContext;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
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

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.router.jersey.TestUtils.getContentAsString;
import static io.servicetalk.http.router.jersey.resources.SerializerUtils.MAP_STRING_OBJECT_SERIALIZER;
import static io.servicetalk.http.router.jersey.resources.SerializerUtils.MAP_STRING_OBJECT_STREAM_SERIALIZER;
import static io.servicetalk.http.router.jersey.resources.SynchronousResources.PATH;
import static java.lang.System.arraycopy;
import static java.nio.charset.StandardCharsets.US_ASCII;
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
 * Synchronous (in JAX-RS lingo [1]) resources.
 * <p>
 * [1] These resources are synchronous because they return either a {@link Response} or a response body entity that
 * will be wrapped with {@link Response#ok(Object)}, whether or not this entity body is complete or streaming.
 */
@Path(PATH)
public class SynchronousResources {
    public static final String PATH = "/sync";

    @Context
    private ConnectionContext ctx;

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
    public String serviceTalkRequest(@Context final StreamingHttpRequest serviceTalkRequest) {
        return "GOT: " + serviceTalkRequest.requestTarget();
    }

    @Produces(TEXT_PLAIN)
    @Path("/head")
    @HEAD
    public Response explicitHead(final String requestContent) {
        return accepted().header(CONTENT_LENGTH, "123").build();
    }

    @Produces(TEXT_PLAIN)
    @Path("/text")
    @GET
    public String getText(@Nullable @QueryParam("qp") final String qp,
                          @QueryParam("null") final boolean nullResult,
                          @Nullable @HeaderParam("hp") final String hp) {
        if ("throw-not-translated".equals(qp)) {
            throw DELIBERATE_EXCEPTION;
        } else if ("throw-translated".equals(qp)) {
            throw new WebApplicationException("Deliberate Exception", CONFLICT);
        }

        return nullResult ? null : "GOT: " + qp + " & " + hp;
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

    @Produces(TEXT_PLAIN)
    @Path("/text-buffer")
    @GET
    public Buffer getTextBuffer() {
        return ctx.executionContext().bufferAllocator().fromAscii("DONE");
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text-buffer")
    @POST
    public Buffer postTextBuffer(final Buffer requestContent) {
        final BufferAllocator allocator = ctx.executionContext().bufferAllocator();
        final CompositeBuffer cb = allocator.newCompositeBuffer(2);
        return cb.addBuffer(allocator.fromAscii("GOT: ")).addBuffer(requestContent);
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-buffer")
    @POST
    public Buffer postJsonBuffer(final Buffer requestContent) {
        final BufferAllocator allocator = ctx.executionContext().bufferAllocator();
        final CompositeBuffer cb = allocator.newCompositeBuffer(3);
        return cb.addBuffer(allocator.fromAscii("{\"got\":")).addBuffer(requestContent)
                .addBuffer(allocator.fromAscii("}"));
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text-bytes")
    @POST
    public byte[] postTextBytes(final byte[] requestContent) {
        final byte[] responseContent = new byte[requestContent.length + 5];
        arraycopy("GOT: ".getBytes(US_ASCII), 0, responseContent, 0, 5);
        arraycopy(requestContent, 0, responseContent, 5, requestContent.length);
        return responseContent;
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-bytes")
    @POST
    public byte[] postJsonBytes(final byte[] requestContent) {
        final byte[] responseContent = new byte[requestContent.length + 8];
        arraycopy("{\"got\":".getBytes(US_ASCII), 0, responseContent, 0, 7);
        arraycopy(requestContent, 0, responseContent, 7, requestContent.length);
        responseContent[requestContent.length + 7] = '}';
        return responseContent;
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-buffer-response")
    @GET
    public Response getTextBufferResponse(@Context final HttpHeaders headers) {
        return status(203).entity(ctx.executionContext().bufferAllocator().fromAscii("DONE"))
                .header("X-Test", headers.getHeaderString("hdr"))
                .build();
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-buffer-response")
    @POST
    public Response postTextBufferResponse(final Buffer requestContent) {
        return accepted(postTextBuffer(requestContent)).build();
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text-strin-pubout")
    @POST
    public Publisher<Buffer> postTextStrInPubOut(final String requestContent) {
        return from(ctx.executionContext().bufferAllocator().fromUtf8("GOT: " + requestContent));
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text-pubin-strout")
    @POST
    public String postTextPubInStrOut(final Publisher<Buffer> requestContent) {
        return "GOT: " + getContentAsString(requestContent);
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text-pubin-pubout")
    @POST
    public Publisher<Buffer> postTextPubInPubOut(final Publisher<Buffer> requestContent) {
        return from(ctx.executionContext().bufferAllocator().fromAscii("GOT: ")).concat(requestContent);
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-pub-response")
    @GET
    public Response getTextPubResponse(@QueryParam("i") final int i) {
        final String contentString = "GOT: " + i;
        final Publisher<Buffer> responseContent =
                from(ctx.executionContext().bufferAllocator().fromAscii(contentString));

        return status(i)
                // We know the content length so we set it, otherwise the response is chunked
                .header(CONTENT_LENGTH, contentString.length())
                // Wrap content Publisher to capture its generic type (i.e. Buffer)
                .entity(new GenericEntity<Publisher<Buffer>>(responseContent) { })
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

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-oio-streams")
    @POST
    public StreamingOutput postJsonOioStreams(final InputStream requestContent) {
        return output -> {
            output.write("{\"got\":".getBytes(UTF_8));
            int b;
            while ((b = requestContent.read()) >= 0) {
                output.write(b);
            }
            output.write('}');
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
        return singletonMap("foo", "bar0");
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json")
    @POST
    public Map<String, Object> postJson(final Map<String, Object> requestContent) {
        final Map<String, Object> responseContent = new HashMap<>(requestContent);
        responseContent.put("foo", "bar1");
        return responseContent;
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-response")
    @PUT
    public Response putJsonResponse(final Map<String, Object> requestContent) {
        // Jersey's JacksonJsonProvider (thus blocking IO) is used for both request deserialization
        // and response serialization
        final Map<String, Object> responseContent = new HashMap<>(requestContent);
        responseContent.put("foo", "bar2");
        return accepted(responseContent).header("X-Test", "test-header").build();
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-mapin-pubout")
    @POST
    public Publisher<Buffer> postJsonMapInPubOut(final Map<String, Object> requestContent) {
        // Jersey's JacksonJsonProvider (thus blocking IO) is used for request deserialization
        // and ServiceTalk streaming serialization is used for the response
        final Map<String, Object> responseContent = new HashMap<>(requestContent);
        responseContent.put("foo", "bar3");
        return MAP_STRING_OBJECT_STREAM_SERIALIZER.serialize(from(responseContent),
                ctx.executionContext().bufferAllocator());
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-pubin-mapout")
    @POST
    public Map<String, Object> postJsonPubInMapOut(final Publisher<Buffer> requestContent) {
        // ServiceTalk streaming deserialization is used for the request
        // and Jersey's JacksonJsonProvider (thus blocking IO) is used for response serialization
        final Map<String, Object> requestData =
                MAP_STRING_OBJECT_STREAM_SERIALIZER.deserialize(requestContent,
                        ctx.executionContext().bufferAllocator()).toIterable().iterator().next();
        final Map<String, Object> responseContent = new HashMap<>(requestData);
        responseContent.put("foo", "bar4");
        return responseContent;
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-pubin-pubout")
    @POST
    public Publisher<Buffer> postJsonPubInPubOut(final Publisher<Buffer> requestContent) {
        // ServiceTalk streaming is used for both request deserialization and response serialization
        final Publisher<Map<String, Object>> response = MAP_STRING_OBJECT_STREAM_SERIALIZER.deserialize(
                requestContent, ctx.executionContext().bufferAllocator())
                        .map(requestData -> {
                            final Map<String, Object> responseContent = new HashMap<>(requestData);
                            responseContent.put("foo", "bar5");
                            return responseContent;
                        });

        return MAP_STRING_OBJECT_STREAM_SERIALIZER.serialize(response, ctx.executionContext().bufferAllocator());
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-buf-sglin-sglout-response")
    @POST
    public Response postJsonBufSingleInSingleOutResponse(final Single<Buffer> requestContent) {
        final BufferAllocator allocator = ctx.executionContext().bufferAllocator();
        final Single<Buffer> response = requestContent.map(buf -> {
            final Map<String, Object> responseContent =
                    new HashMap<>(MAP_STRING_OBJECT_SERIALIZER.deserialize(buf, allocator));
            responseContent.put("foo", "bar6");
            return MAP_STRING_OBJECT_SERIALIZER.serialize(responseContent, allocator);
        });

        return accepted(new GenericEntity<Single<Buffer>>(response) { }).build();
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-buf-pubin-pubout")
    @POST
    public Publisher<Buffer> postJsonBufPubInPubOut(final Publisher<Buffer> requestContent) {
        final BufferAllocator allocator = ctx.executionContext().bufferAllocator();
        return requestContent.map(buf -> allocator.fromUtf8(buf.toString(UTF_8).toUpperCase()));
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-buf-pubin-pubout-response")
    @POST
    public Response postJsonBufPubInPubOutResponse(final Publisher<Buffer> requestContent) {
        return accepted(new GenericEntity<Publisher<Buffer>>(postJsonBufPubInPubOut(requestContent)) { }).build();
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-pojoin-pojoout")
    @POST
    public TestPojo postJsonPojoInPojoOut(final TestPojo testPojo) {
        testPojo.setAnInt(testPojo.getAnInt() + 1);
        testPojo.setaString(testPojo.getaString() + "x");
        return testPojo;
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-pojoin-pojoout-response")
    @POST
    public Response postJsonPojoInPojoOutResponse(final TestPojo testPojo) {
        testPojo.setAnInt(testPojo.getAnInt() + 1);
        testPojo.setaString(testPojo.getaString() + "x");
        return accepted(testPojo).build();
    }

    @Produces(APPLICATION_JSON)
    @Path("/security-context")
    @GET
    public SecurityContext securityContext(@Context final SecurityContext securityContext) {
        return securityContext;
    }
}
