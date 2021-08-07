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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.router.api.NoOffloadsRouteExecutionStrategy;
import io.servicetalk.router.api.RouteExecutionStrategy;
import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.server.ManagedAsync;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;

import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.router.jersey.resources.SerializerUtils.MAP_STRING_STRING_SERIALIZER;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static javax.ws.rs.core.Response.ok;

public final class ExecutionStrategyResources {
    public static final String EXEC_NAME = "exec";
    public static final String RS_THREAD_NAME = "rs-thread";
    public static final String THREAD_NAME = "thread";

    private ExecutionStrategyResources() {
        // no instances
    }

    @Produces(APPLICATION_JSON)
    public abstract static class AbstractExecutionStrategyResource {
        @Context
        private ConnectionContext ctx;

        @Context
        private StreamingHttpRequest req;

        @Context
        private UriInfo uriInfo;

        //
        // Default execution strategy
        //

        @GET
        @Path("/subrsc-default")
        public Map<String, String> subResourceDefault() {
            return getThreadingInfo(ctx, req, uriInfo);
        }

        @GET
        @Path("/subrsc-default-single")
        public Single<Map<String, String>> subResourceDefaultSingle() {
            return getThreadingInfoSingle();
        }

        @GET
        @Path("/subrsc-default-single-response")
        public Single<Response> subResourceDefaultSingleResponse() {
            return getThreadingInfoSingleResponse();
        }

        @GET
        @Path("/subrsc-default-single-buffer")
        public Single<Buffer> subResourceDefaultSingleBuffer() {
            return getThreadingInfoSingleBuffer();
        }

        @Consumes(APPLICATION_JSON)
        @POST
        @Path("/subrsc-default-single-mapped")
        public Single<Map<String, String>> subResourceDefaultSingleMapped(final Single<Buffer> body) {
            return getThreadingInfoSingleMapped(body);
        }

        @Consumes(APPLICATION_JSON)
        @POST
        @Path("/subrsc-default-publisher-mapped")
        public Publisher<Buffer> subResourceDefaultPubMapped(final Publisher<Buffer> body) {
            return getThreadingInfoPublisherMapped(body);
        }

        //
        // Route execution strategy
        //

        @RouteExecutionStrategy(id = "test")
        @GET
        @Path("/subrsc-rte-exec-id")
        public Map<String, String> subResourceRouteExecId() {
            return getThreadingInfo(ctx, req, uriInfo);
        }

        @RouteExecutionStrategy(id = "test")
        @GET
        @Path("/subrsc-rte-exec-id-single")
        public Single<Map<String, String>> subResourceRouteExecIdSingle() {
            return getThreadingInfoSingle();
        }

        @RouteExecutionStrategy(id = "test")
        @GET
        @Path("/subrsc-rte-exec-id-single-response")
        public Single<Response> subResourceRouteExecIdSingleResponse() {
            return getThreadingInfoSingleResponse();
        }

        @RouteExecutionStrategy(id = "test")
        @GET
        @Path("/subrsc-rte-exec-id-single-buffer")
        public Single<Buffer> subResourceRouteExecIdSingleBuffer() {
            return getThreadingInfoSingleBuffer();
        }

        @RouteExecutionStrategy(id = "test")
        @Consumes(APPLICATION_JSON)
        @POST
        @Path("/subrsc-rte-exec-id-single-mapped")
        public Single<Map<String, String>> subResourceRouteExecIdSingleMapped(final Single<Buffer> body) {
            return getThreadingInfoSingleMapped(body);
        }

        @RouteExecutionStrategy(id = "test")
        @Consumes(APPLICATION_JSON)
        @POST
        @Path("/subrsc-rte-exec-id-publisher-mapped")
        public Publisher<Buffer> subResourceRouteExecIdPubMapped(final Publisher<Buffer> body) {
            return getThreadingInfoPublisherMapped(body);
        }

        //
        // No offloads strategy
        //

        @NoOffloadsRouteExecutionStrategy
        @GET
        @Path("/subrsc-rte-no-offloads")
        public Map<String, String> subResourceRouteNoOffloads() {
            return getThreadingInfo(ctx, req, uriInfo);
        }

        @NoOffloadsRouteExecutionStrategy
        @GET
        @Path("/subrsc-rte-no-offloads-single")
        public Single<Map<String, String>> subResourceRouteNoOffloadsSingle() {
            return getThreadingInfoSingle();
        }

        @NoOffloadsRouteExecutionStrategy
        @GET
        @Path("/subrsc-rte-no-offloads-single-response")
        public Single<Response> subResourceRouteNoOffloadsSingleResponse() {
            return getThreadingInfoSingleResponse();
        }

        @NoOffloadsRouteExecutionStrategy
        @GET
        @Path("/subrsc-rte-no-offloads-single-buffer")
        public Single<Buffer> subResourceRouteNoOffloadsSingleBuffer() {
            return getThreadingInfoSingleBuffer();
        }

        @NoOffloadsRouteExecutionStrategy
        @Consumes(APPLICATION_JSON)
        @POST
        @Path("/subrsc-rte-no-offloads-single-mapped")
        public Single<Map<String, String>> subResourceRouteNoOffloadsSingleMapped(final Single<Buffer> body) {
            return getThreadingInfoSingleMapped(body);
        }

        @NoOffloadsRouteExecutionStrategy
        @Consumes(APPLICATION_JSON)
        @POST
        @Path("/subrsc-rte-no-offloads-publisher-mapped")
        public Publisher<Buffer> subResourceRouteNoOffloadsPubMapped(final Publisher<Buffer> body) {
            return getThreadingInfoPublisherMapped(body);
        }

        //
        // Support
        //

        private Single<Map<String, String>> getThreadingInfoSingle() {
            final Map<String, String> threadingInfo = getThreadingInfo(ctx, req, uriInfo);
            return defer(() -> {
                threadingInfo.put(RS_THREAD_NAME, currentThread().getName());
                return succeeded(threadingInfo);
            });
        }

        private Single<Response> getThreadingInfoSingleResponse() {
            final Map<String, String> threadingInfo = getThreadingInfo(ctx, req, uriInfo);
            return defer(() -> {
                threadingInfo.put(RS_THREAD_NAME, currentThread().getName());
                return succeeded(ok(threadingInfo).build());
            });
        }

        private Single<Buffer> getThreadingInfoSingleBuffer() {
            final BufferAllocator allocator = ctx.executionContext().bufferAllocator();
            final Map<String, String> threadingInfo = getThreadingInfo(ctx, req, uriInfo);
            return defer(() -> {
                threadingInfo.put(RS_THREAD_NAME, currentThread().getName());
                return succeeded(MAP_STRING_STRING_SERIALIZER.serialize(threadingInfo, allocator));
            });
        }

        private Single<Map<String, String>> getThreadingInfoSingleMapped(final Single<Buffer> content) {
            final Map<String, String> threadingInfo = getThreadingInfo(ctx, req, uriInfo);
            return content.flatMap(__ -> {
                threadingInfo.put(RS_THREAD_NAME, currentThread().getName());
                return succeeded(threadingInfo);
            });
        }

        private Publisher<Buffer> getThreadingInfoPublisherMapped(final Publisher<Buffer> content) {
            return content.ignoreElements().concat(getThreadingInfoSingleBuffer().toPublisher());
        }

        private static Map<String, String> getThreadingInfo(final ConnectionContext ctx,
                                                            final StreamingHttpRequest req,
                                                            final UriInfo uriInfo) {
            // Use the opportunity to assert that other context objects are valid
            if (!req.path().equals('/' + uriInfo.getPath())) {
                throw new IllegalStateException("Invalid @context state for: " + req);
            }

            final Map<String, String> info = new HashMap<>(2);
            info.put(THREAD_NAME, currentThread().getName());
            info.put(EXEC_NAME, ctx.executionContext().executor().toString());
            return info;
        }
    }

    @Path("/rsc-default")
    public static class ResourceDefaultStrategy extends AbstractExecutionStrategyResource {
        // No extra method
    }

    @RouteExecutionStrategy(id = "test")
    @Path("/rsc-rte-exec-id")
    public static class ResourceRouteExecIdStrategy extends AbstractExecutionStrategyResource {
        // No extra method
    }

    @NoOffloadsRouteExecutionStrategy
    @Path("/rsc-rte-no-offloads")
    public static class ResourceRouteNoOffloadsStrategy extends AbstractExecutionStrategyResource {
        // No extra method
    }

    @Path("/rsc-invalid")
    public static class ResourceInvalidExecStrategy {
        @RouteExecutionStrategy(id = "")
        @GET
        @Path("/empty-id")
        public void emptyId() {
            // NOOP
        }

        @NoOffloadsRouteExecutionStrategy
        @RouteExecutionStrategy(id = "test")
        @GET
        @Path("/conflicting")
        public void conflictingAnnotations() {
            // NOOP
        }
    }

    @RouteExecutionStrategy(id = "test")
    @Path("/rsc-unsupported-async")
    public static class ResourceUnsupportedAsync {
        @GET
        @Path("/suspended")
        public void suspended(@Suspended final AsyncResponse ar) {
            ar.resume("DONE");
        }

        @Produces(SERVER_SENT_EVENTS)
        @GET
        @Path("/sse")
        public void sse(@Context final SseEventSink eventSink, @Context final Sse sse) {
            // NOOP
        }

        @ManagedAsync
        @GET
        @Path("/managed")
        public void managed() {
            // NOOP
        }

        @GET
        @Path("/cf")
        public CompletionStage<String> cf() {
            return completedFuture("DONE");
        }
    }
}
