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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.router.jersey.AbstractResourceTest.TestFiltered;
import io.servicetalk.http.router.jersey.ExecutionStrategy;
import io.servicetalk.http.router.jersey.TestPojo;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.router.jersey.ExecutionStrategy.ExecutorSelector.ROUTER_EXECUTOR;
import static io.servicetalk.http.router.jersey.resources.AbstractAsynchronousResources.PATH;
import static java.lang.System.arraycopy;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Collections.singletonMap;
import static javax.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.accepted;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.status;

@Path(PATH)
@ExecutionStrategy(ROUTER_EXECUTOR)
public class AsynchronousResourcesRouterExec extends AbstractAsynchronousResources {
    @Produces(TEXT_PLAIN)
    @Path("/head")
    @HEAD
    public Single<Response> explicitHead(final String requestContent) {
        return success(accepted().header(CONTENT_LENGTH, "123").build());
    }

    @Produces(TEXT_PLAIN)
    @Path("/text")
    @GET
    public Single<String> getText(@Nullable @QueryParam("qp") final String qp,
                                  @QueryParam("null") final boolean nullResult,
                                  @Nullable @HeaderParam("hp") final String hp) {
        if ("throw-not-translated".equals(qp)) {
            throw DELIBERATE_EXCEPTION;
        } else if ("throw-translated".equals(qp)) {
            throw new WebApplicationException("Deliberate Exception", CONFLICT);
        }

        return success(nullResult ? null : "GOT: " + qp + " & " + hp);
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text")
    @POST
    public Single<String> postText(final String requestContent) {
        return defer(() -> success("GOT: " + requestContent));
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-response")
    @GET
    public Single<Response> getTextResponse(@Context final HttpHeaders headers) {
        return success(noContent().header("X-Test", headers.getHeaderString("hdr")).build());
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-response")
    @POST
    public Single<Response> postTextResponse(final String requestContent) {
        return ctx.executionContext().executor().submit(() -> accepted("GOT: " + requestContent).build());
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-buffer")
    @GET
    public Single<Buffer> getTextBuffer() {
        return success(ctx.executionContext().bufferAllocator().fromAscii("DONE"));
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text-buffer")
    @POST
    public Single<Buffer> postTextBuffer(final Buffer requestContent) {
        final BufferAllocator allocator = ctx.executionContext().bufferAllocator();
        return ctx.executionContext().executor().submit(() -> allocator.newCompositeBuffer(2)
                .addBuffer(allocator.fromAscii("GOT: "))
                .addBuffer(requestContent));
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-buffer")
    @POST
    public Single<Buffer> postJsonBuffer(final Buffer requestContent) {
        final BufferAllocator allocator = ctx.executionContext().bufferAllocator();
        return ctx.executionContext().executor().submit(() -> allocator.newCompositeBuffer(3)
                .addBuffer(allocator.fromAscii("{\"got\":"))
                .addBuffer(requestContent)
                .addBuffer(allocator.fromAscii("}")));
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text-bytes")
    @POST
    public Single<byte[]> postTextBytes(final byte[] requestContent) {
        return ctx.executionContext().executor().submit(() -> {
            final byte[] responseContent = new byte[requestContent.length + 5];
            arraycopy("GOT: ".getBytes(US_ASCII), 0, responseContent, 0, 5);
            arraycopy(requestContent, 0, responseContent, 5, requestContent.length);
            return responseContent;
        });
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-bytes")
    @POST
    public Single<byte[]> postJsonBytes(final byte[] requestContent) {
        return ctx.executionContext().executor().submit(() -> {
            final byte[] responseContent = new byte[requestContent.length + 8];
            arraycopy("{\"got\":".getBytes(US_ASCII), 0, responseContent, 0, 7);
            arraycopy(requestContent, 0, responseContent, 7, requestContent.length);
            responseContent[requestContent.length + 7] = '}';
            return responseContent;
        });
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-buffer-response")
    @GET
    public Single<Response> getTextBufferResponse(@Context final HttpHeaders headers) {
        return success(status(203).entity(ctx.executionContext().bufferAllocator().fromAscii("DONE"))
                .header("X-Test", headers.getHeaderString("hdr"))
                .build());
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-buffer-response")
    @POST
    public Single<Response> postTextBufferResponse(final Buffer requestContent) {
        return postTextBuffer(requestContent).map(b -> accepted(b).build());
    }

    @Produces(APPLICATION_JSON)
    @Path("/json")
    @GET
    public Single<Map<String, Object>> getJson() {
        return success(singletonMap("foo", "bar0"));
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json")
    @POST
    public Single<Map<String, Object>> postJson(final Map<String, Object> requestContent) {
        final Map<String, Object> responseContent = new HashMap<>(requestContent);
        responseContent.put("foo", "bar1");
        return ctx.executionContext().executor().submit(() -> responseContent);
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-response")
    @PUT
    public Single<Response> putJsonResponse(final Map<String, Object> requestContent) {
        final Map<String, Object> responseContent = new HashMap<>(requestContent);
        responseContent.put("foo", "bar2");
        return success(accepted(responseContent).header("X-Test", "test-header").build());
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-pojoin-pojoout")
    @POST
    public Single<TestPojo> postJsonPojo(final TestPojo testPojo) {
        testPojo.setAnInt(testPojo.getAnInt() + 1);
        testPojo.setaString(testPojo.getaString() + "x");
        return ctx.executionContext().executor().submit(() -> testPojo);
    }

    @TestFiltered
    @Produces(TEXT_PLAIN)
    @Path("/filtered")
    @POST
    public Single<String> postFiltered(final String requestContent) {
        return success("GOT: " + requestContent);
    }
}
