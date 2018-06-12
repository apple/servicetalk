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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.router.jersey.AbstractResourceTest.TestFiltered;
import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.internal.util.collection.Refs;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.router.jersey.TestUtils.asChunkPublisher;
import static io.servicetalk.http.router.jersey.resources.AsynchronousResources.PATH;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static javax.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.GATEWAY_TIMEOUT;
import static javax.ws.rs.core.Response.accepted;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;

/**
 * Asynchronous (in JAX-RS lingo) resources.
 */
@Path(PATH)
public class AsynchronousResources {
    public static final String PATH = "/async";

    @Context
    private ConnectionContext ctx;

    @Produces(TEXT_PLAIN)
    @Path("/text")
    @GET
    public CompletionStage<String> getText(@Nullable @QueryParam("qp") final String qp,
                                           @Nullable @HeaderParam("hp") final String hp) {
        if ("throw-not-translated".equals(qp)) {
            throw DELIBERATE_EXCEPTION;
        } else if ("throw-translated".equals(qp)) {
            throw new WebApplicationException("Deliberate Exception", CONFLICT);
        }

        return completedFuture("GOT: " + qp + " & " + hp);
    }

    @Produces(TEXT_PLAIN)
    @Path("/failed-text")
    @GET
    public CompletionStage<String> getFailed(@QueryParam("cancel") final boolean cancel) {
        final CompletableFuture<String> cf = new CompletableFuture<>();
        if (cancel) {
            cf.cancel(true);
        } else {
            cf.completeExceptionally(DELIBERATE_EXCEPTION);
        }
        return cf;
    }

    @Produces(TEXT_PLAIN)
    @Path("/delayed-text")
    @GET
    public CompletionStage<String> getDelayedText(@Nonnull @QueryParam("delay") final long delay,
                                                  @Nonnull @QueryParam("unit") final TimeUnit unit) {
        final CompletableFuture<String> cf = new CompletableFuture<>();
        final Cancellable cancellable = ctx.getExecutor().schedule(() -> cf.complete("DONE"), delay, unit);

        return cf.whenComplete((r, t) -> {
            if (t instanceof CancellationException) {
                cancellable.cancel();
            }
        });
    }

    @Produces(TEXT_PLAIN)
    @Path("/response-comsta")
    @GET
    public Response getResponseCompletionStage(@Context final HttpHeaders headers) {
        return ok(completedFuture("DONE")).build();
    }

    @Produces(TEXT_PLAIN)
    @Path("/delayed-response-comsta")
    @GET
    public Response getDelayedResponseCompletionStage(@Nonnull @QueryParam("delay") final long delay,
                                                      @Nonnull @QueryParam("unit") final TimeUnit unit) {
        final CompletableFuture<String> cf = new CompletableFuture<>();
        final Cancellable cancellable = ctx.getExecutor().schedule(() -> cf.complete("DONE"), delay, unit);

        return ok(cf.whenComplete((r, t) -> {
            if (t instanceof CancellationException) {
                cancellable.cancel();
            }
        })).build();
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text")
    @POST
    public CompletionStage<String> postText(final String requestContent) {
        return completedFuture("GOT: " + requestContent);
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-response")
    @GET
    public CompletionStage<Response> getTextResponse(@Context final HttpHeaders headers) {
        return completedFuture(noContent().header("X-Test", headers.getHeaderString("hdr")).build());
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-response")
    @POST
    public CompletionStage<Response> postTextResponse(final String requestContent) {
        return completedFuture(accepted("GOT: " + requestContent).build());
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-pub-response")
    @GET
    public CompletionStage<Response> getTextPubResponse(@QueryParam("i") final int i) {
        final String contentString = "GOT: " + i;
        final Publisher<HttpPayloadChunk> responseContent =
                asChunkPublisher(contentString, ctx.getExecutionContext().getBufferAllocator());

        // Wrap content Publisher to capture its generic type (i.e. HttpPayloadChunk)
        final GenericEntity<Publisher<HttpPayloadChunk>> entity = new GenericEntity<Publisher<HttpPayloadChunk>>(responseContent) {
        };
        return completedFuture(status(i)
                // We know the content length so we set it, otherwise the response is chunked
                .header(CONTENT_LENGTH, contentString.length())
                .entity(entity)
                .build());
    }

    @TestFiltered
    @Produces(TEXT_PLAIN)
    @Path("/filtered")
    @POST
    public CompletionStage<String> postFiltered(final String requestContent) {
        return completedFuture("GOT: " + requestContent);
    }

    @Produces(APPLICATION_JSON)
    @Path("/json")
    @GET
    public CompletionStage<Map<String, Object>> getJson() {
        return completedFuture(singletonMap("foo", "bar1"));
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-response")
    @PUT
    public CompletionStage<Response> putJsonResponse(final Map<String, Object> requestContent) {
        final Map<String, Object> responseContent = new HashMap<>(requestContent);
        responseContent.put("foo", "bar2");
        return completedFuture(accepted(responseContent).header("X-Test", "test-header").build());
    }

    @Produces(TEXT_PLAIN)
    @Path("/suspended/resume")
    @GET
    public void getAsyncResponseResume(@Suspended final AsyncResponse ar) {
        ar.resume("DONE");
    }

    @Produces(TEXT_PLAIN)
    @Path("/suspended/cancel")
    @GET
    public void getAsyncResponseCancel(@Suspended final AsyncResponse ar) {
        ar.cancel();
    }

    @Produces(TEXT_PLAIN)
    @Path("/suspended/timeout-resume")
    @GET
    public void getAsyncResponseTimeoutResume(@Suspended final AsyncResponse ar) {
        ar.setTimeout(1, MINUTES);
        ctx.getExecutor().timer(10, MILLISECONDS)
                .doAfterComplete(() -> ar.resume("DONE"))
                .subscribe();
    }

    @Produces(TEXT_PLAIN)
    @Path("/suspended/timeout-expire")
    @GET
    public void getAsyncResponseTimeoutExpire(@Suspended final AsyncResponse ar) {
        // Set timeout twice to ensure users can update it at will
        ar.setTimeout(1, MINUTES);
        ar.setTimeout(1, NANOSECONDS);
    }

    @Produces(TEXT_PLAIN)
    @Path("/suspended/timeout-expire-handled")
    @GET
    public void getAsyncResponseTimeoutExpireHandled(@Suspended final AsyncResponse ar) {
        ar.setTimeoutHandler(ar2 -> ar2.resume(status(GATEWAY_TIMEOUT).build()));
        ar.setTimeout(1, NANOSECONDS);
    }

    @Produces(TEXT_PLAIN)
    @Path("/suspended/resume-timeout")
    @GET
    public void getAsyncResponseResumeTimeout(@Suspended final AsyncResponse ar) {
        ar.resume("DONE");
        ar.setTimeout(1, MINUTES);
    }

    @Produces(TEXT_PLAIN)
    @Path("/suspended/busy")
    @GET
    public void getAsyncResponseBusy(@Suspended final AsyncResponse ar) {
        // Neither resume nor cancel -> busy for ever
    }

    @Produces(APPLICATION_JSON)
    @Path("/suspended/json")
    @GET
    public void getJsonAsyncResponse(@Suspended final AsyncResponse ar) {
        ctx.getExecutor().schedule(() -> ar.resume(singletonMap("foo", "bar3")), 10, MILLISECONDS);
    }

    @Produces(SERVER_SENT_EVENTS)
    @Path("/sse/stream")
    @GET
    public void getSseStream(@Context final SseEventSink eventSink,
                             @Context final Sse sse) {
        scheduleSseEventSend(new SseEmitter() {
            @Override
            public CompletionStage<?> emit(final OutboundSseEvent event) {
                return eventSink.send(event);
            }

            @Override
            public void close() {
                eventSink.close();
            }
        }, sse, Refs.of(0));
    }

    @Produces(SERVER_SENT_EVENTS)
    @Path("/sse/broadcast")
    @GET
    public void getSseBroadcast(@Context final SseEventSink eventSink,
                                @Context final Sse sse) {
        eventSink.send(sse.newEvent("bar"));
        final SseBroadcaster sseBroadcaster = sse.newBroadcaster();
        sseBroadcaster.register(eventSink);

        scheduleSseEventSend(new SseEmitter() {
            @Override
            public CompletionStage<?> emit(final OutboundSseEvent event) {
                // This returns null :( for the moment (JerseySseBroadcaster not fully implemented yet)
                sseBroadcaster.broadcast(event);
                return completedFuture(null);
            }

            @Override
            public void close() {
                sseBroadcaster.close();
            }
        }, sse, Refs.of(0));
    }

    private interface SseEmitter {
        CompletionStage<?> emit(OutboundSseEvent event);

        void close();
    }

    private void scheduleSseEventSend(final SseEmitter emmitter, final Sse sse, final Ref<Integer> iRef) {
        ctx.getExecutor().schedule(() -> {
            final int i = iRef.get();
            emmitter.emit(sse.newEvent("foo" + i)).whenComplete((r, t) -> {
                if (t == null && i < 9) {
                    iRef.set(i + 1);
                    scheduleSseEventSend(emmitter, sse, iRef);
                } else {
                    emmitter.close();
                }
            });
        }, 10, MILLISECONDS);
    }
}
