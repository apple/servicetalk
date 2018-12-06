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
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.router.jersey.NoOffloadsRouteExecutionStrategy;
import io.servicetalk.http.router.jersey.RouteExecutionStrategy;
import io.servicetalk.transport.api.ConnectionContext;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;

import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.never;
import static io.servicetalk.http.router.jersey.TestUtils.getContentAsString;
import static io.servicetalk.http.router.jersey.resources.CancellableResources.PATH;
import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static javax.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(PATH)
public class CancellableResources {
    public static final String PATH = "/cancel";

    public final CountDownLatch sseSinkClosedLatch = new CountDownLatch(1);

    @Path("/suspended")
    @GET
    public void getForeverSuspended(@Suspended final AsyncResponse ar) {
        // The scheduled task in DefaultContainerResponseWriter will never complete
        ar.setTimeout(7, DAYS);
    }

    @Produces(TEXT_PLAIN)
    @Path("/single")
    @GET
    public Single<String> getSingleNever() {
        // The single subscriber in EndpointEnhancingRequestFilter will never complete
        return never();
    }

    @RouteExecutionStrategy(id = "test")
    @Produces(TEXT_PLAIN)
    @Path("/offload")
    @GET
    public String getOffloadedBlocked() throws InterruptedException {
        // The scheduled task in EndpointEnhancingRequestFilter will never complete
        sleep(DAYS.toMillis(7));
        return "never reached";
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/oio-streams")
    @POST
    public StreamingOutput postOioStreams(final InputStream requestContent) {
        return output -> {
            output.write("GOT: ".getBytes(UTF_8));
            int b;
            while ((b = requestContent.read()) >= 0) {
                output.write(b);
            }
            output.flush();
        };
    }

    @RouteExecutionStrategy(id = "test")
    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/offload-oio-streams")
    @POST
    public StreamingOutput postOffloadedOioStreams(final InputStream requestContent) {
        return postOioStreams(requestContent);
    }

    @NoOffloadsRouteExecutionStrategy
    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/no-offloads-oio-streams")
    @POST
    public StreamingOutput postNoOffloadsOioStreams(final InputStream requestContent) {
        return postOioStreams(requestContent);
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/rs-streams")
    @POST
    public Publisher<Buffer> postRsStreams(@QueryParam("subscribe") final boolean subscribe,
                                           final Publisher<Buffer> requestContent,
                                           @Context final ConnectionContext ctx) {
        final BufferAllocator allocator = ctx.executionContext().bufferAllocator();
        final CompositeBuffer responseBuffer = allocator.newCompositeBuffer(2).addBuffer(allocator.fromAscii("GOT: "));

        // If subscribe is true, we consume the request publisher in the resource method, otherwise we let the server
        // subscribe when it gets the response back from the router
        if (subscribe) {
            return just(responseBuffer.writeAscii(getContentAsString(requestContent)));
        } else {
            final AtomicBoolean first = new AtomicBoolean(true);
            return requestContent.map(c -> first.compareAndSet(true, false) ? responseBuffer.addBuffer(c) : c);
        }
    }

    @RouteExecutionStrategy(id = "test")
    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/offload-rs-streams")
    @POST
    public Publisher<Buffer> postOffloadedRsStreams(@QueryParam("subscribe") final boolean subscribe,
                                                    final Publisher<Buffer> requestContent,
                                                    @Context final ConnectionContext ctx) {
        return postRsStreams(subscribe, requestContent, ctx);
    }

    @NoOffloadsRouteExecutionStrategy
    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/no-offloads-rs-streams")
    @POST
    public Publisher<Buffer> postNoOffloadsRsStreams(@QueryParam("subscribe") final boolean subscribe,
                                                     final Publisher<Buffer> requestContent,
                                                     @Context final ConnectionContext ctx) {
        return postRsStreams(subscribe, requestContent, ctx);
    }

    @Produces(SERVER_SENT_EVENTS)
    @Path("/sse")
    @GET
    public void getSseStream(@Context final SseEventSink eventSink,
                             @Context final Sse sse,
                             @Context final ConnectionContext ctx) {
        sendSseUntilFailure(eventSink, sse, ctx.executionContext().executor());
    }

    private void sendSseUntilFailure(final SseEventSink eventSink, final Sse sse, final Executor executor) {
        try {
            eventSink.send(sse.newEvent("foo"));
            executor.schedule(() -> sendSseUntilFailure(eventSink, sse, executor), 10, MILLISECONDS);
        } catch (final Throwable t) {
            if (eventSink.isClosed()) {
                sseSinkClosedLatch.countDown();
            } else {
                throw new IllegalStateException("SseEventSink should be closed", t);
            }
        }
    }
}
