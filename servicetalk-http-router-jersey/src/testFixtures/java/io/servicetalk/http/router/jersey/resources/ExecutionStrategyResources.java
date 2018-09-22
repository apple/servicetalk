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
import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.router.jersey.ExecutionStrategy;
import io.servicetalk.serialization.api.DefaultSerializer;
import io.servicetalk.serialization.api.Serializer;
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
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.router.jersey.ExecutionStrategy.ExecutorSelector.ROUTER_EXECUTOR;
import static io.servicetalk.http.router.jersey.ExecutionStrategy.ExecutorSelector.SERVER_EXECUTOR;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static javax.ws.rs.core.Response.ok;

public final class ExecutionStrategyResources {
    public static final String THREAD_NAME = "thread";
    public static final String EXEC_NAME = "exec";

    private ExecutionStrategyResources() {
        // no instances
    }

    @Produces(APPLICATION_JSON)
    public abstract static class AbstractExecutionStrategyResource {
        private static final Serializer SERIALIZER = new DefaultSerializer(new JacksonSerializationProvider());

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
        // Server execution strategy
        //

        @ExecutionStrategy(SERVER_EXECUTOR)
        @GET
        @Path("/subrsc-srvr-exec")
        public Map<String, String> subResourceServerExec() {
            return getThreadingInfo(ctx, req, uriInfo);
        }

        @ExecutionStrategy(SERVER_EXECUTOR)
        @GET
        @Path("/subrsc-srvr-exec-single")
        public Single<Map<String, String>> subResourceServerExecSingle() {
            return getThreadingInfoSingle();
        }

        @ExecutionStrategy(SERVER_EXECUTOR)
        @GET
        @Path("/subrsc-srvr-exec-single-response")
        public Single<Response> subResourceServerExecSingleResponse() {
            return getThreadingInfoSingleResponse();
        }

        @ExecutionStrategy(SERVER_EXECUTOR)
        @GET
        @Path("/subrsc-srvr-exec-single-buffer")
        public Single<Buffer> subResourceServerExecSingleBuffer() {
            return getThreadingInfoSingleBuffer();
        }

        @ExecutionStrategy(SERVER_EXECUTOR)
        @Consumes(APPLICATION_JSON)
        @POST
        @Path("/subrsc-srvr-exec-single-mapped")
        public Single<Map<String, String>> subResourceServerExecSingleMapped(final Single<Buffer> body) {
            return getThreadingInfoSingleMapped(body);
        }

        @ExecutionStrategy(SERVER_EXECUTOR)
        @Consumes(APPLICATION_JSON)
        @POST
        @Path("/subrsc-srvr-exec-publisher-mapped")
        public Publisher<Buffer> subResourceServerExecPubMapped(final Publisher<Buffer> body) {
            return getThreadingInfoPublisherMapped(body);
        }

        //
        // Router default execution strategy
        //

        @ExecutionStrategy(ROUTER_EXECUTOR)
        @GET
        @Path("/subrsc-rtr-exec")
        public Map<String, String> subResourceRouterExec() {
            return getThreadingInfo(ctx, req, uriInfo);
        }

        @ExecutionStrategy(ROUTER_EXECUTOR)
        @GET
        @Path("/subrsc-rtr-exec-single")
        public Single<Map<String, String>> subResourceRouterExecSingle() {
            return getThreadingInfoSingle();
        }

        @ExecutionStrategy(ROUTER_EXECUTOR)
        @GET
        @Path("/subrsc-rtr-exec-single-response")
        public Single<Response> subResourceRouterExecSingleResponse() {
            return getThreadingInfoSingleResponse();
        }

        @ExecutionStrategy(ROUTER_EXECUTOR)
        @GET
        @Path("/subrsc-rtr-exec-single-buffer")
        public Single<Buffer> subResourceRouterExecSingleBuffer() {
            return getThreadingInfoSingleBuffer();
        }

        @ExecutionStrategy(ROUTER_EXECUTOR)
        @Consumes(APPLICATION_JSON)
        @POST
        @Path("/subrsc-rtr-exec-single-mapped")
        public Single<Map<String, String>> subResourceRouterExecSingleMapped(final Single<Buffer> body) {
            return getThreadingInfoSingleMapped(body);
        }

        @ExecutionStrategy(ROUTER_EXECUTOR)
        @Consumes(APPLICATION_JSON)
        @POST
        @Path("/subrsc-rtr-exec-publisher-mapped")
        public Publisher<Buffer> subResourceRouterExecPubMapped(final Publisher<Buffer> body) {
            return getThreadingInfoPublisherMapped(body);
        }

        //
        // Router ID execution strategy
        //

        @ExecutionStrategy(value = ROUTER_EXECUTOR, executorId = "test")
        @GET
        @Path("/subrsc-rtr-exec-id")
        public Map<String, String> subResourceRouterExecId() {
            return getThreadingInfo(ctx, req, uriInfo);
        }

        @ExecutionStrategy(value = ROUTER_EXECUTOR, executorId = "test")
        @GET
        @Path("/subrsc-rtr-exec-id-single")
        public Single<Map<String, String>> subResourceRouterExecIdSingle() {
            return getThreadingInfoSingle();
        }

        @ExecutionStrategy(value = ROUTER_EXECUTOR, executorId = "test")
        @GET
        @Path("/subrsc-rtr-exec-id-single-response")
        public Single<Response> subResourceRouterExecIdSingleResponse() {
            return getThreadingInfoSingleResponse();
        }

        @ExecutionStrategy(value = ROUTER_EXECUTOR, executorId = "test")
        @GET
        @Path("/subrsc-rtr-exec-id-single-buffer")
        public Single<Buffer> subResourceRouterExecIdSingleBuffer() {
            return getThreadingInfoSingleBuffer();
        }

        @ExecutionStrategy(value = ROUTER_EXECUTOR, executorId = "test")
        @Consumes(APPLICATION_JSON)
        @POST
        @Path("/subrsc-rtr-exec-id-single-mapped")
        public Single<Map<String, String>> subResourceRouterExecIdSingleMapped(final Single<Buffer> body) {
            return getThreadingInfoSingleMapped(body);
        }

        @ExecutionStrategy(value = ROUTER_EXECUTOR, executorId = "test")
        @Consumes(APPLICATION_JSON)
        @POST
        @Path("/subrsc-rtr-exec-id-publisher-mapped")
        public Publisher<Buffer> subResourceRouterExecIdPubMapped(final Publisher<Buffer> body) {
            return getThreadingInfoPublisherMapped(body);
        }

        //
        // Support
        //

        private Single<Map<String, String>> getThreadingInfoSingle() {
            final Map<String, String> threadingInfo = getThreadingInfo(ctx, req, uriInfo);
            final Thread resourceMethodThread = currentThread();

            return defer(() -> fromSameExecutorAs(resourceMethodThread) ? success(threadingInfo) :
                    error(new IllegalStateException("Expected resource method thread: " + resourceMethodThread +
                            ", but got: " + currentThread())));
        }

        private Single<Response> getThreadingInfoSingleResponse() {
            final Map<String, String> threadingInfo = getThreadingInfo(ctx, req, uriInfo);
            final Thread resourceMethodThread = currentThread();

            return defer(() -> fromSameExecutorAs(resourceMethodThread) ? success(ok(threadingInfo).build()) :
                    error(new IllegalStateException("Expected resource method thread: " + resourceMethodThread +
                            ", but got: " + currentThread())));
        }

        private Single<Buffer> getThreadingInfoSingleBuffer() {
            final BufferAllocator allocator = ctx.getExecutionContext().getBufferAllocator();
            final Map<String, String> threadingInfo = getThreadingInfo(ctx, req, uriInfo);
            final Thread resourceMethodThread = currentThread();

            return defer(() -> fromSameExecutorAs(resourceMethodThread) ?
                    success(SERIALIZER.serialize(threadingInfo, allocator)) :
                    error(new IllegalStateException("Expected resource method thread: " + resourceMethodThread +
                            ", but got: " + currentThread())));
        }

        private Single<Map<String, String>> getThreadingInfoSingleMapped(final Single<Buffer> content) {
            final Map<String, String> threadingInfo = getThreadingInfo(ctx, req, uriInfo);
            final Thread resourceMethodThread = currentThread();

            return content.flatMap(__ -> fromSameExecutorAs(resourceMethodThread) ? success(threadingInfo) :
                    error(new IllegalStateException("Expected resource method thread: " + resourceMethodThread +
                            ", but got: " + currentThread())));
        }

        private Publisher<Buffer> getThreadingInfoPublisherMapped(final Publisher<Buffer> content) {
            // This single is deferred and will perform the resourceMethodThread comparison only when the response
            // publisher will be subscribed
            final Single<Buffer> bufferSingle = getThreadingInfoSingleBuffer();
            final Thread resourceMethodThread = currentThread();

            return content
                    .doOnNext(__ -> requireFromSameExecutorAs(resourceMethodThread))
                    .ignoreElements().andThen(bufferSingle.toPublisher())
                    .doOnRequest(__ -> requireFromSameExecutorAs(resourceMethodThread));
        }

        private static Map<String, String> getThreadingInfo(final ConnectionContext ctx, final StreamingHttpRequest req,
                                                            final UriInfo uriInfo) {
            // Use the opportunity to assert that other context objects are valid
            if (!req.path().equals('/' + uriInfo.getPath())) {
                throw new IllegalStateException("Invalid @Context state for: " + req);
            }

            final Map<String, String> info = new HashMap<>(2);
            info.put(THREAD_NAME, currentThread().getName());
            info.put(EXEC_NAME, ctx.getExecutionContext().getExecutor().toString());
            return info;
        }

        private static boolean fromSameExecutorAs(final Thread expectedThread) {
            return currentThread().getName().replaceAll("\\d", "")
                    .equals(expectedThread.getName().replaceAll("\\d", ""));
        }

        private static void requireFromSameExecutorAs(final Thread expectedThread) {
            if (!fromSameExecutorAs(expectedThread)) {
                throw new IllegalStateException("Expected thread: " + expectedThread + ", but got: " + currentThread());
            }
        }
    }

    @Path("/rsc-default")
    public static class ResourceDefaultStrategy extends AbstractExecutionStrategyResource {
        // No extra method
    }

    @ExecutionStrategy(SERVER_EXECUTOR)
    @Path("/rsc-srvr-exec")
    public static class ResourceServerExecStrategy extends AbstractExecutionStrategyResource {
        // No extra method
    }

    @ExecutionStrategy(ROUTER_EXECUTOR)
    @Path("/rsc-rtr-exec")
    public static class ResourceRouterExecStrategy extends AbstractExecutionStrategyResource {
        // No extra method
    }

    @ExecutionStrategy(value = ROUTER_EXECUTOR, executorId = "test")
    @Path("/rsc-rtr-exec-id")
    public static class ResourceRouterExecIdStrategy extends AbstractExecutionStrategyResource {
        // No extra method
    }

    @ExecutionStrategy(value = SERVER_EXECUTOR, executorId = "test")
    @Path("/rsc-invalid")
    public static class ResourceInvalidExecStrategy {
        @ExecutionStrategy(value = SERVER_EXECUTOR, executorId = "test")
        @GET
        @Path("/id-with-srvr-exec")
        public void idWithServerExec() {
            // NOOP
        }

        @GET
        @Path("/default")
        public void defaultStrategy() {
            // NOOP
        }
    }

    @ExecutionStrategy(ROUTER_EXECUTOR)
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
