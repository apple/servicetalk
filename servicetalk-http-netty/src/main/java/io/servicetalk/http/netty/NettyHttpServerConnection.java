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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.RejectedSubscribeError;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequests;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.NettyConnection;

import io.netty.channel.Channel;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.netty.HeaderUtils.addResponseTransferEncodingIfNecessary;
import static io.servicetalk.http.netty.SpliceFlatStreamToMetaSingle.flatten;

final class NettyHttpServerConnection extends NettyConnection<Object, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyHttpServerConnection.class);
    private final DefaultHttpServiceContext context;
    private final StreamingHttpService service;
    private BiFunction<HttpRequestMetaData, Publisher<Object>, StreamingHttpRequest> packer;

    NettyHttpServerConnection(final Channel channel, final Publisher<Object> requestObjectPublisher,
                              final TerminalPredicate<Object> terminalPredicate,
                              final CloseHandler closeHandler,
                              final DefaultHttpServiceContext context,
                              final StreamingHttpService service) {
        super(channel, context, requestObjectPublisher, terminalPredicate, closeHandler);
        this.context = context;
        this.service = service;
        final BufferAllocator alloc = context.getExecutionContext().getBufferAllocator();
        packer = (HttpRequestMetaData hdr, Publisher<Object> pandt) -> spliceRequest(alloc, hdr, pandt);
    }

    Completable process() {
        final Publisher<Object> connRequestObjectPublisher = read();

        final Single<StreamingHttpRequest> requestSingle =
                new SpliceFlatStreamToMetaSingle<>(connRequestObjectPublisher, packer);
        return handleRequestAndWriteResponse(requestSingle);
    }

    private static StreamingHttpRequest spliceRequest(final BufferAllocator alloc,
                                                      final HttpRequestMetaData hr,
                                                      final Publisher<Object> pub) {
        return StreamingHttpRequests.newRequestWithTrailers(hr.method(), hr.requestTarget(), hr.version(), hr.headers(), alloc, pub);
    }

    private Completable handleRequestAndWriteResponse(final Single<StreamingHttpRequest> requestSingle) {
        final Publisher<Object> responseObjectPublisher = requestSingle.flatMapPublisher(request -> {
            final HttpRequestMethod requestMethod = request.method();
            final HttpKeepAlive keepAlive = HttpKeepAlive.getResponseKeepAlive(request);
            // We transform the request and delay the completion of the result flattened stream to avoid resubscribing
            // to the NettyChannelPublisher before the previous subscriber has terminated. Otherwise we may attempt
            // to do duplicate subscribe on NettyChannelPublisher, which will result in a connection closure.
            CompletableProcessor processor = new CompletableProcessor();
            StreamingHttpRequest request2 = request.transformPayloadBody(
                    // Cancellation is assumed to close the connection, or be ignored if this Subscriber has already
                    // terminated. That means we don't need to trigger the processor as completed because we don't care
                    // about processing more requests.
                    payload -> payload.doAfterSubscriber(() -> new Subscriber<Buffer>() {
                        @Override
                        public void onSubscribe(final Subscription s) {
                        }

                        @Override
                        public void onNext(final Buffer buffer) {
                        }

                        @Override
                        public void onError(final Throwable t) {
                            // After the response payload has terminated, we attempt to subscribe to the request payload
                            // and drain/discard the content (in case the user forgets to consume the stream). However
                            // this means we may introduce a duplicate subscribe and this doesn't mean the request
                            // content has not terminated.
                            if (!(t instanceof RejectedSubscribeError)) {
                                processor.onComplete();
                            }
                        }

                        @Override
                        public void onComplete() {
                            processor.onComplete();
                        }
                    }));
            final Completable drainRequestPayloadBody = request2.payloadBody().ignoreElements()
                    // ignore error about duplicate subscriptions, we are forcing a subscription here and the user
                    // may also subscribe, so it is OK if we fail here.
                    .onErrorResume(t -> completed());
            return handleRequest(request2)
                    .map(response -> processResponse(requestMethod, keepAlive, drainRequestPayloadBody, response))
                    .flatMapPublisher(resp -> flatten(resp, StreamingHttpResponse::payloadBodyAndTrailers))
                    .concatWith(processor);
            // We are writing to the connection which may request more data from the EventLoop. So offload control
            // signals which may have blocking code.
        }).subscribeOn(context.getExecutionContext().getExecutor());
        return writeResponse(responseObjectPublisher.repeat(val -> true));
    }

    private Single<StreamingHttpResponse> handleRequest(final StreamingHttpRequest request) {
        return new Single<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                // Since we do not offload data path for the request single, this method will be invoked from the
                // EventLoop. So, we offload the call to StreamingHttpService.
                Executor exec = context.getExecutionContext().getExecutor();
                exec.execute(() -> {
                    Single<StreamingHttpResponse> source;
                    try {
                        source = service.handle(context, request.transformPayloadBody(bdy -> bdy.publishOn(exec)),
                                context.getStreamingFactory())
                                .onErrorResume(cause -> newErrorResponse(cause, request));
                    } catch (final Throwable cause) {
                        source = newErrorResponse(cause, request);
                    }
                    source.subscribe(subscriber);
                });
            }
        };
    }

    private static StreamingHttpResponse processResponse(final HttpRequestMethod requestMethod,
                                                         final HttpKeepAlive keepAlive,
                                                         final Completable drainRequestPayloadBody,
                                                         final StreamingHttpResponse response) {
        addResponseTransferEncodingIfNecessary(response, requestMethod);
        keepAlive.addConnectionHeaderIfNecessary(response);

        // When the response payload publisher completes, read any of the request payload that hasn't already
        // been read. This is necessary for using a persistent connection to send multiple requests.
        return response.transformPayloadBody(responsePayload -> responsePayload.concatWith(drainRequestPayloadBody));
    }

    private Single<StreamingHttpResponse> newErrorResponse(final Throwable cause,
                                                           final StreamingHttpRequest request) {
        LOGGER.error("internal server error service={} connection={}", service, context, cause);
        StreamingHttpResponse resp = context.getStreamingFactory().serverError().version(request.version());
        resp.headers().set(CONTENT_LENGTH, ZERO);
        return success(resp);
    }

    private Completable writeResponse(final Publisher<Object> responseObjectPublisher) {
        return write(responseObjectPublisher);
    }
}
