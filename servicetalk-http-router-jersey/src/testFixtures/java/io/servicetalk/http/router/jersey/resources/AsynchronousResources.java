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
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.router.jersey.AbstractResourceTest.TestFiltered;
import io.servicetalk.http.router.jersey.TestPojo;

import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.internal.util.collection.Refs;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
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
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.http.router.jersey.resources.AsynchronousResources.PATH;
import static java.lang.System.arraycopy;
import static java.nio.charset.StandardCharsets.US_ASCII;
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

@Path(PATH)
public class AsynchronousResources extends AbstractAsynchronousResources {
    @Path("/void-completion")
    @GET
    public CompletionStage<Void> getVoidCompletion(@QueryParam("fail") final boolean fail,
                                                   @QueryParam("defer") final boolean defer) {

        final Callable<Void> task = () -> {
            if (fail) {
                throw DELIBERATE_EXCEPTION;
            } else {
                return null;
            }
        };

        if (defer) {
            return newCompletionStage(task);
        } else {
            return newCompletedCompletionStage(task);
        }
    }

    @Produces(TEXT_PLAIN)
    @Path("/head")
    @HEAD
    public CompletionStage<Response> explicitHead(final String requestContent) {
        return newCompletionStage(() -> accepted().header(CONTENT_LENGTH, "123").build());
    }

    @Produces(TEXT_PLAIN)
    @Path("/text")
    @GET
    public CompletionStage<String> getText(@Nullable @QueryParam("qp") final String qp,
                                           @QueryParam("null") final boolean nullResult,
                                           @Nullable @HeaderParam("hp") final String hp) {
        if ("throw-not-translated".equals(qp)) {
            throw DELIBERATE_EXCEPTION;
        } else if ("throw-translated".equals(qp)) {
            throw new WebApplicationException("Deliberate Exception", CONFLICT);
        }

        return completedFuture(nullResult ? null : "GOT: " + qp + " & " + hp);
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
        return newCompletionStage(() -> "DONE", delay, unit);
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
        return newCompletionStage(() -> accepted("GOT: " + requestContent).build());
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-buffer")
    @GET
    public CompletionStage<Buffer> getTextBuffer() {
        final BufferAllocator allocator = ctx.executionContext().bufferAllocator();
        return newCompletionStage(() -> allocator.fromAscii("DONE"));
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text-buffer")
    @POST
    public CompletionStage<Buffer> postTextBuffer(final Buffer requestContent) {
        final BufferAllocator allocator = ctx.executionContext().bufferAllocator();

        return newCompletionStage(() -> allocator.newCompositeBuffer(2)
                .addBuffer(allocator.fromAscii("GOT: "))
                .addBuffer(requestContent));
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-buffer")
    @POST
    public CompletionStage<Buffer> postJsonBuffer(final Buffer requestContent) {
        final BufferAllocator allocator = ctx.executionContext().bufferAllocator();

        return newCompletionStage(() -> allocator.newCompositeBuffer(3)
                .addBuffer(allocator.fromAscii("{\"got\":"))
                .addBuffer(requestContent)
                .addBuffer(allocator.fromAscii("}")));
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text-bytes")
    @POST
    public CompletionStage<byte[]> postTextBytes(final byte[] requestContent) {
        return newCompletionStage(() -> {
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
    public CompletionStage<byte[]> postJsonBytes(final byte[] requestContent) {
        return newCompletionStage(() -> {
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
    public CompletionStage<Response> getTextBufferResponse(@Context final HttpHeaders headers) {
        return completedFuture(status(203).entity(ctx.executionContext().bufferAllocator().fromAscii("DONE"))
                .header("X-Test", headers.getHeaderString("hdr"))
                .build());
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-buffer-response")
    @POST
    public CompletionStage<Response> postTextBufferResponse(final Buffer requestContent) {
        return postTextBuffer(requestContent).thenApply(b -> accepted(b).build());
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-pub-response")
    @GET
    public CompletionStage<Response> getTextPubResponse(@QueryParam("i") final int i) {
        final String contentString = "GOT: " + i;
        final Publisher<Buffer> responseContent =
                just(ctx.executionContext().bufferAllocator().fromAscii(contentString));

        return completedFuture(status(i)
                // We know the content length so we set it, otherwise the response is chunked
                .header(CONTENT_LENGTH, contentString.length())
                // Wrap content Publisher to capture its generic type (i.e. Buffer)
                .entity(new GenericEntity<Publisher<Buffer>>(responseContent) { })
                .build());
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
        final Cancellable cancellable =
                ctx.executionContext().executor().schedule(() -> cf.complete("DONE"), delay, unit);

        return ok(cf.whenComplete((r, t) -> {
            if (t instanceof CancellationException) {
                cancellable.cancel();
            }
        })).build();
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
    @Path("/json")
    @POST
    public CompletionStage<Map<String, Object>> postJson(final Map<String, Object> requestContent) {
        final Map<String, Object> responseContent = new HashMap<>(requestContent);
        responseContent.put("foo", "bar1");
        return completedFuture(responseContent);
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

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-pojoin-pojoout")
    @POST
    public CompletionStage<TestPojo> postJsonPojo(final TestPojo testPojo) {
        testPojo.setAnInt(testPojo.getAnInt() + 1);
        testPojo.setaString(testPojo.getaString() + "x");
        return completedFuture(testPojo);
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
        ctx.executionContext().executor().timer(10, MILLISECONDS)
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
        ctx.executionContext().executor().schedule(() ->
                ar.resume(singletonMap("foo", "bar3")), 10, MILLISECONDS);
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
        }, sse, Refs.of(0), ctx.executionContext().executor());
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
        }, sse, Refs.of(0), ctx.executionContext().executor());
    }

    private interface SseEmitter {
        CompletionStage<?> emit(OutboundSseEvent event);

        void close();
    }

    private void scheduleSseEventSend(final SseEmitter emmitter, final Sse sse, final Ref<Integer> iRef,
                                      final Executor executor) {
        executor.schedule(() -> {
            final int i = iRef.get();
            emmitter.emit(sse.newEvent("foo" + i)).whenComplete((r, t) -> {
                if (t == null && i < 9) {
                    iRef.set(i + 1);
                    scheduleSseEventSend(emmitter, sse, iRef, executor);
                } else {
                    emmitter.close();
                }
            });
        }, 10, MILLISECONDS);
    }

    private <T> CompletionStage<T> newCompletedCompletionStage(final Callable<T> task) {
        return newCompletionStage(task, 0, MILLISECONDS);
    }

    private <T> CompletionStage<T> newCompletionStage(final Callable<T> task) {
        return newCompletionStage(task, 10, MILLISECONDS);
    }

    private <T> CompletionStage<T> newCompletionStage(final Callable<T> task, final long delay, final TimeUnit unit) {
        final CompletableFuture<T> cf = new CompletableFuture<>();
        final Runnable failSafeTask = () -> {
            try {
                cf.complete(task.call());
            } catch (final Throwable t) {
                cf.completeExceptionally(t);
            }
        };

        if (delay == 0) {
            failSafeTask.run();
            return cf;
        }

        final Cancellable cancellable = ctx.executionContext().executor().schedule(failSafeTask, delay, unit);
        return cf.whenComplete((r, t) -> {
            if (t instanceof CancellationException) {
                cancellable.cancel();
            }
        });
    }
}
