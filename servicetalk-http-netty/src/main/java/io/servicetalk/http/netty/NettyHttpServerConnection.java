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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.LastHttpPayloadChunk;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.NettyConnection;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpPayloadChunks.newLastPayloadChunk;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static io.servicetalk.http.netty.HeaderUtils.addResponseTransferEncodingIfNecessary;
import static io.servicetalk.http.netty.SpliceFlatStreamToMetaSingle.flatten;

final class NettyHttpServerConnection extends NettyConnection<Object, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyHttpServerConnection.class);
    private static final Predicate<HttpPayloadChunk> LAST_HTTP_PAYLOAD_CHUNK_PREDICATE =
            p -> p instanceof LastHttpPayloadChunk;
    private final ConnectionContext context;
    private final StreamingHttpService service;

    NettyHttpServerConnection(final Channel channel, final Publisher<Object> requestObjectPublisher,
                              final TerminalPredicate<Object> terminalPredicate,
                              final CloseHandler closeHandler,
                              final ConnectionContext context,
                              final StreamingHttpService service) {
        super(channel, context, requestObjectPublisher, terminalPredicate, closeHandler);
        this.context = context;
        this.service = service;
    }

    Completable process() {
        final Publisher<Object> connRequestObjectPublisher = read();
        final Single<StreamingHttpRequest<HttpPayloadChunk>> requestSingle =
                new SpliceFlatStreamToMetaSingle<>(connRequestObjectPublisher, NettyHttpServerConnection::spliceRequest);
        return handleRequestAndWriteResponse(requestSingle);
    }

    private static StreamingHttpRequest<HttpPayloadChunk> spliceRequest(final HttpRequestMetaData hr,
                                                                        final Publisher<HttpPayloadChunk> pub) {
        return newRequest(hr.getVersion(), hr.getMethod(), hr.getRequestTarget(), pub, hr.getHeaders());
    }

    private Completable handleRequestAndWriteResponse(final Single<StreamingHttpRequest<HttpPayloadChunk>> requestSingle) {
        final Publisher<Object> responseObjectPublisher = requestSingle.flatMapPublisher(request -> {
            final HttpRequestMethod requestMethod = request.getMethod();
            final HttpKeepAlive keepAlive = HttpKeepAlive.getResponseKeepAlive(request);
            // We transform the request and delay the completion of the result flattened stream to avoid resubscribing
            // to the NettyChannelPublisher before the previous subscriber has terminated. Otherwise we may attempt
            // to do duplicate subscribe on NettyChannelPublisher, which will result in a connection closure.
            CompletableProcessor processor = new CompletableProcessor();
            StreamingHttpRequest<HttpPayloadChunk> request2 = request.transformPayloadBody(
                    // Cancellation is assumed to close the connection, so we don't need to trigger the processor as
                    // completed because we don't care about processing more requests.
                    payload -> payload.doOnComplete(processor::onComplete));
            final Completable drainRequestPayloadBody = request2.getPayloadBody().ignoreElements()
                    // ignore error about duplicate subscriptions, we are forcing a subscription here and the user
                    // may also subscribe, so it is OK if we fail here.
                    .onErrorResume(t -> completed());
            return handleRequest(request2)
                    .map(response -> processResponse(requestMethod, keepAlive, drainRequestPayloadBody, response))
                    .flatMapPublisher(resp -> flatten(resp, StreamingHttpResponse::getPayloadBody))
                    .concatWith(processor);
            // We are writing to the connection which may request more data from the EventLoop. So offload control
            // signals which may have blocking code.
        }).subscribeOn(context.getExecutionContext().getExecutor());
        return writeResponse(responseObjectPublisher.repeat(val -> true));
    }

    private Single<StreamingHttpResponse<HttpPayloadChunk>> handleRequest(final StreamingHttpRequest<HttpPayloadChunk> request) {
        return new Single<StreamingHttpResponse<HttpPayloadChunk>>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse<HttpPayloadChunk>> subscriber) {
                // Since we do not offload data path for the request single, this method will be invoked from the
                // EventLoop. So, we offload the call to StreamingHttpService.
                context.getExecutionContext().getExecutor().execute(() -> {
                    Single<StreamingHttpResponse<HttpPayloadChunk>> source;
                    try {
                        source = service.handle(context,
                                request.transformPayloadBody(bdy ->
                                        bdy.publishOn(context.getExecutionContext().getExecutor())))
                                .onErrorResume(cause -> newErrorResponse(cause, request));
                    } catch (final Throwable cause) {
                        source = newErrorResponse(cause, request);
                    }
                    source.subscribe(subscriber);
                });
            }
        };
    }

    private static StreamingHttpResponse<HttpPayloadChunk> processResponse(final HttpRequestMethod requestMethod,
                                                                           final HttpKeepAlive keepAlive,
                                                                           final Completable drainRequestPayloadBody,
                                                                           final StreamingHttpResponse<HttpPayloadChunk> response) {
        addResponseTransferEncodingIfNecessary(response, requestMethod);
        keepAlive.addConnectionHeaderIfNecessary(response);

        // When the response payload publisher completes, read any of the request payload that hasn't already
        // been read. This is necessary for using a persistent connection to send multiple requests.
        return response.transformPayloadBody(responsePayload -> ensureLastPayloadChunk(responsePayload)
                .concatWith(drainRequestPayloadBody));
    }

    private static Publisher<HttpPayloadChunk> ensureLastPayloadChunk(
            final Publisher<HttpPayloadChunk> responseObjectPublisher) {
        return responseObjectPublisher.liftSynchronous(new EnsureLastItemBeforeCompleteOperator<>(
                LAST_HTTP_PAYLOAD_CHUNK_PREDICATE,
                () -> EmptyLastHttpPayloadChunk.INSTANCE));
    }

    private Single<StreamingHttpResponse<HttpPayloadChunk>> newErrorResponse(final Throwable cause,
                                                                             final StreamingHttpRequest<HttpPayloadChunk> request) {
        LOGGER.error("internal server error service={} connection={}", service, context, cause);
        final StreamingHttpResponse<HttpPayloadChunk> response = newResponse(request.getVersion(), INTERNAL_SERVER_ERROR,
                just(newLastPayloadChunk(EMPTY_BUFFER, EmptyHttpHeaders.INSTANCE)));
        response.getHeaders().set(CONTENT_LENGTH, ZERO);
        return success(response);
    }

    private Completable writeResponse(final Publisher<Object> responseObjectPublisher) {
        return write(responseObjectPublisher);
    }
}
