/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.ConnectablePayloadWriter;
import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.HeaderUtils.hasContentLength;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_RECEIVE_META_STRATEGY;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.StreamingHttpResponses.newTransportResponse;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

final class BlockingStreamingToStreamingService extends AbstractServiceAdapterHolder {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockingStreamingToStreamingService.class);
    private static final HttpExecutionStrategy DEFAULT_STRATEGY = OFFLOAD_RECEIVE_META_STRATEGY;
    private final BlockingStreamingHttpService original;

    BlockingStreamingToStreamingService(final BlockingStreamingHttpService original,
                                        final HttpExecutionStrategyInfluencer influencer) {
        super(influencer.influenceStrategy(DEFAULT_STRATEGY));
        this.original = requireNonNull(original);
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory responseFactory) {
        return new Single<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                final ThreadInterruptingCancellable tiCancellable = new ThreadInterruptingCancellable(currentThread());
                try {
                    subscriber.onSubscribe(tiCancellable);
                } catch (Throwable cause) {
                    handleExceptionFromOnSubscribe(subscriber, cause);
                    return;
                }

                final Processor exceptionProcessor = newCompletableProcessor();
                final BufferHttpPayloadWriter payloadWriter = new BufferHttpPayloadWriter(
                        ctx.headersFactory().newTrailers(), exceptionProcessor);
                DefaultBlockingStreamingHttpServerResponse response = null;
                try {
                    final Consumer<HttpResponseMetaData> sendMeta = (metaData) -> {
                        final StreamingHttpResponse result;
                        try {
                            // transfer-encoding takes precedence over content-length.
                            // > When a message does not have a Transfer-Encoding header field, a
                            // Content-Length header field can provide the anticipated size.
                            // https://tools.ietf.org/html/rfc7230#section-3.3.2
                            HttpHeaders headers = metaData.headers();
                            boolean addTrailers = isTransferEncodingChunked(headers);
                            if (!addTrailers && !HTTP_1_0.equals(metaData.version()) && !hasContentLength(headers) &&
                                    !hasContentLength(headers) &&
                                    // HEAD responses MUST never carry a payload, adding chunked makes no sense and
                                    // breaks our HttpResponseDecoder
                                    !HEAD.equals(request.method())) {
                                // this is likely not supported in http/1.0 and it is possible that a response has
                                // neither header and the connection close indicates the end of the response.
                                // https://tools.ietf.org/html/rfc7230#section-3.3.3
                                headers.add(TRANSFER_ENCODING, CHUNKED);
                                addTrailers = true;
                            }
                            Publisher<Object> messageBody = fromSource(exceptionProcessor)
                                    .merge(payloadWriter.connect());
                            if (addTrailers) {
                                messageBody = messageBody.concat(succeeded(payloadWriter.trailers()));
                            }
                            messageBody = messageBody.beforeSubscription(() -> new Subscription() {
                                @Override
                                public void request(final long n) {
                                }

                                @Override
                                public void cancel() {
                                    tiCancellable.cancel();
                                }
                            });
                            result = newTransportResponse(metaData.status(), metaData.version(), metaData.headers(),
                                    ctx.executionContext().bufferAllocator(), messageBody, ctx.headersFactory());
                        } catch (Throwable t) {
                            subscriber.onError(t);
                            throw t;
                        }
                        subscriber.onSuccess(result);
                    };

                    response = new DefaultBlockingStreamingHttpServerResponse(OK, request.version(),
                            ctx.headersFactory().newHeaders(), payloadWriter,
                            ctx.executionContext().bufferAllocator(), sendMeta);
                    original.handle(ctx, request.toBlockingStreamingRequest(), response);
                } catch (Throwable cause) {
                    tiCancellable.setDone(cause);
                    if (response == null || response.markMetaSent()) {
                        safeOnError(subscriber, cause);
                    } else {
                        try {
                            exceptionProcessor.onError(cause);
                        } finally {
                            try {
                                payloadWriter.close(cause);
                            } catch (IOException e) {
                                LOGGER.info("Unexpected exception from close {}", exceptionProcessor, e);
                            }
                        }
                    }
                    return;
                }
                tiCancellable.setDone();
            }
        };
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(original::close);
    }

    @Override
    public Completable closeAsyncGracefully() {
        return blockingToCompletable(original::closeGracefully);
    }

    private static final class BufferHttpPayloadWriter implements HttpPayloadWriter<Buffer> {
        private final ConnectablePayloadWriter<Buffer> payloadWriter = new ConnectablePayloadWriter<>();
        private final CompletableSource.Subscriber subscriber;
        private final HttpHeaders trailers;

        BufferHttpPayloadWriter(final HttpHeaders trailers, final CompletableSource.Subscriber subscriber) {
            this.trailers = trailers;
            this.subscriber = subscriber;
        }

        @Override
        public void write(final Buffer object) throws IOException {
            payloadWriter.write(object);
        }

        @Override
        public void flush() throws IOException {
            payloadWriter.flush();
        }

        @Override
        public void close() throws IOException {
            try {
                payloadWriter.close();
            } finally {
                subscriber.onComplete();
            }
        }

        @Override
        public void close(final Throwable cause) throws IOException {
            try {
                payloadWriter.close(cause);
            } finally {
                subscriber.onError(cause);
            }
        }

        @Override
        public HttpHeaders trailers() {
            return trailers;
        }

        Publisher<Buffer> connect() {
            return payloadWriter.connect();
        }
    }
}
