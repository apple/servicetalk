/*
 * Copyright © 2018-2019, 2021-2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.ScanMapper;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.ConnectablePayloadWriter;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.http.api.DefaultHttpExecutionStrategy.OFFLOAD_RECEIVE_META_STRATEGY;
import static io.servicetalk.http.api.DefaultPayloadInfo.forTransportReceive;
import static io.servicetalk.http.api.HeaderUtils.hasContentLength;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpProtocolVersion.h1TrailersSupported;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.oio.api.internal.PayloadWriterUtils.safeClose;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

final class BlockingStreamingToStreamingService extends AbstractServiceAdapterHolder {
    private static final HttpExecutionStrategy DEFAULT_STRATEGY = OFFLOAD_RECEIVE_META_STRATEGY;
    private final BlockingStreamingHttpService original;

    BlockingStreamingToStreamingService(final BlockingStreamingHttpService original,
                                        final HttpExecutionStrategy strategy) {
        super(defaultStrategy() == strategy ? DEFAULT_STRATEGY : strategy);
        this.original = requireNonNull(original);
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory responseFactory) {
        return new SubscribableSingle<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                final ThreadInterruptingCancellable tiCancellable = new ThreadInterruptingCancellable(currentThread());
                try {
                    subscriber.onSubscribe(tiCancellable);
                } catch (Throwable cause) {
                    handleExceptionFromOnSubscribe(subscriber, cause);
                    return;
                }

                // This exists to help users with error propagation. If the user closes the payloadWriter and they throw
                // (e.g. try-with-resources) this processor is merged with the payloadWriter Publisher so the error will
                // still be propagated.
                final CompletableSource.Processor exceptionProcessor = newCompletableProcessor();
                final BufferHttpPayloadWriter payloadWriter = new BufferHttpPayloadWriter(
                        () -> ctx.headersFactory().newTrailers());
                DefaultBlockingStreamingHttpServerResponse response = null;
                try {
                    final Consumer<DefaultHttpResponseMetaData> sendMeta = (metaData) -> {
                        final DefaultStreamingHttpResponse result;
                        try {
                            // transfer-encoding takes precedence over content-length.
                            // > When a message does not have a Transfer-Encoding header field, a
                            // Content-Length header field can provide the anticipated size.
                            // https://tools.ietf.org/html/rfc7230#section-3.3.2
                            final HttpHeaders headers = metaData.headers();
                            final HttpProtocolVersion version = metaData.version();
                            boolean addTrailers = version.major() > 1 || isTransferEncodingChunked(headers);
                            if (!addTrailers && h1TrailersSupported(version) && !hasContentLength(headers) &&
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
                                    .merge(payloadWriter.connect().shareContextOnSubscribe());
                            if (addTrailers) {
                                messageBody = messageBody.scanWithMapper(() -> new TrailersMapper(payloadWriter));
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
                            result = new DefaultStreamingHttpResponse(metaData.status(), version, headers,
                                    metaData.context0(), ctx.executionContext().bufferAllocator(), messageBody,
                                    forTransportReceive(false, version, headers), ctx.headersFactory());
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

                    // The user code has returned successfully, complete the processor so the response stream can
                    // complete. If the user handles the request asynchronously (e.g. on another thread) they are
                    // responsible for closing the payloadWriter.
                    exceptionProcessor.onComplete();
                } catch (Throwable cause) {
                    tiCancellable.setDone(cause);
                    if (response == null || response.markMetaSent()) {
                        safeOnError(subscriber, cause);
                    } else {
                        try {
                            exceptionProcessor.onError(cause);
                        } finally {
                            safeClose(payloadWriter, cause);
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
        return Completable.fromCallable(() -> {
            original.close();
            return null;
        });
    }

    @Override
    public Completable closeAsyncGracefully() {
        return Completable.fromCallable(() -> {
            original.closeGracefully();
            return null;
        });
    }

    private static final class BufferHttpPayloadWriter implements HttpPayloadWriter<Buffer> {
        private final ConnectablePayloadWriter<Buffer> payloadWriter = new ConnectablePayloadWriter<>();
        private final Supplier<HttpHeaders> trailersFactory;
        @Nullable
        private HttpHeaders trailers;

        BufferHttpPayloadWriter(final Supplier<HttpHeaders> trailersFactory) {
            this.trailersFactory = trailersFactory;
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
            payloadWriter.close();
        }

        @Override
        public void close(final Throwable cause) throws IOException {
            payloadWriter.close(cause);
        }

        @Override
        public HttpHeaders trailers() {
            if (trailers == null) {
                trailers = trailersFactory.get();
            }
            return trailers;
        }

        @Nullable
        HttpHeaders trailers0() {
            return trailers;
        }

        Publisher<Buffer> connect() {
            return payloadWriter.connect();
        }
    }

    private static final class TrailersMapper implements ScanMapper<Object, Object>, ScanMapper.MappedTerminal<Object> {
        private final BufferHttpPayloadWriter payloadWriter;

        TrailersMapper(final BufferHttpPayloadWriter payloadWriter) {
            this.payloadWriter = payloadWriter;
        }

        @Nullable
        @Override
        public Object mapOnNext(@Nullable final Object next) {
            return next;
        }

        @Nullable
        @Override
        public MappedTerminal<Object> mapOnError(final Throwable cause) {
            return null;
        }

        @Override
        public MappedTerminal<Object> mapOnComplete() {
            return this;
        }

        @Nullable
        @Override
        public Object onNext() {
            return payloadWriter.trailers0();
        }

        @Override
        public boolean onNextValid() {
            return payloadWriter.trailers0() != null;
        }

        @Nullable
        @Override
        public Throwable terminal() {
            return null;
        }
    }
}
