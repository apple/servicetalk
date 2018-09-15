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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.LastHttpPayloadChunk;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceResponseFactory;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.NettyConnection;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.netty.HeaderUtils.addResponseTransferEncodingIfNecessary;
import static io.servicetalk.http.netty.SpliceFlatStreamToMetaSingle.flatten;

final class NettyHttpServerConnection extends NettyConnection<Object, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyHttpServerConnection.class);
    private final StreamingHttpServiceResponseFactory respFactory;
    private final StreamingHttpService service;
    private BiFunction<HttpRequestMetaData, Publisher<Object>, StreamingHttpRequest> packer;

    NettyHttpServerConnection(final Channel channel, final Publisher<Object> requestObjectPublisher,
                              final TerminalPredicate<Object> terminalPredicate,
                              final CloseHandler closeHandler,
                              final StreamingHttpServiceResponseFactory respFactory,
                              final StreamingHttpService service) {
        super(channel, respFactory.getConnectionContext(), requestObjectPublisher, terminalPredicate, closeHandler);
        this.respFactory = respFactory;
        this.service = service;
        final BufferAllocator alloc = respFactory.getConnectionContext().getExecutionContext().getBufferAllocator();
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
        return newRequest(hr.getMethod(), hr.getRequestTarget(), hr.getVersion(), hr.getHeaders(), alloc, pub);
    }

    private Completable handleRequestAndWriteResponse(final Single<StreamingHttpRequest> requestSingle) {
        final Publisher<Object> responseObjectPublisher = requestSingle.flatMapPublisher(request -> {
            final HttpRequestMethod requestMethod = request.getMethod();
            final HttpKeepAlive keepAlive = HttpKeepAlive.getResponseKeepAlive(request);
            final Completable drainRequestPayloadBody = request.getPayloadBody().ignoreElements().onErrorResume(
                    t -> completed()
                    /* ignore error from SpliceFlatStreamToMetaSingle about duplicate subscriptions. */);

            return handleRequest(request)
                    .map(response -> processResponse(requestMethod, keepAlive, drainRequestPayloadBody, response))
                    .flatMapPublisher(resp -> flatten(resp, StreamingHttpResponse::getPayloadBody));
            // We are writing to the connection which may request more data from the EventLoop. So offload control
            // signals which may have blocking code.
        }).subscribeOn(respFactory.getConnectionContext().getExecutionContext().getExecutor());
        return writeResponse(responseObjectPublisher.repeat(val -> true));
    }

    private Single<StreamingHttpResponse> handleRequest(final StreamingHttpRequest request) {
        return new Single<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                // Since we do not offload data path for the request single, this method will be invoked from the
                // EventLoop. So, we offload the call to StreamingHttpService.
                Executor exec = respFactory.getConnectionContext().getExecutionContext().getExecutor();
                exec.execute(() -> {
                    Single<StreamingHttpResponse> source;
                    try {
                        source = service.handle(respFactory, request.transformPayloadBody(bdy -> bdy.publishOn(exec)))
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
        LOGGER.error("internal server error service={} connection={}", service, respFactory, cause);
        final StreamingHttpResponse response = respFactory.serverError().setVersion(request.getVersion());
        return success(response);
    }

    private Completable writeResponse(final Publisher<Object> responseObjectPublisher) {
        return write(responseObjectPublisher);
    }
}
