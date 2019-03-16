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
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.ConnectablePayloadWriter;
import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponseWithTrailers;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

final class BlockingStreamingHttpServiceToStreamingHttpService extends StreamingHttpService {
    private final BlockingStreamingHttpService service;
    private final HttpExecutionStrategy effectiveStrategy;

    private BlockingStreamingHttpServiceToStreamingHttpService(final BlockingStreamingHttpService service,
                                                               final HttpExecutionStrategy effectiveStrategy) {
        this.service = requireNonNull(service);
        this.effectiveStrategy = requireNonNull(effectiveStrategy);
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory responseFactory) {

        return new Single<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                final ThreadInterruptingCancellable tiCancellable = new ThreadInterruptingCancellable(currentThread());
                subscriber.onSubscribe(tiCancellable);

                final AtomicBoolean metaSent = new AtomicBoolean();
                final CompletableProcessor payloadProcessor = new CompletableProcessor();

                final BiFunction<HttpResponseMetaData, BufferHttpPayloadWriter, HttpPayloadWriter<Buffer>> sendMeta =
                        (metaData, payloadWriter) -> {
                    if (!metaSent.compareAndSet(false, true)) {
                        throw new IllegalStateException("Response meta-data is already sent");
                    }

                    final Publisher<Object> payloadBodyAndTrailers = payloadProcessor.merge(payloadWriter.connect()
                            .map(buffer -> (Object) buffer) // down cast to Object
                            .concatWith(success(payloadWriter.trailers())));

                    subscriber.onSuccess(newResponseWithTrailers(metaData.status(), metaData.version(),
                            metaData.headers(), ctx.executionContext().bufferAllocator(), payloadBodyAndTrailers));
                    return payloadWriter;
                };
                try {
                    final BufferHttpPayloadWriter payloadWriter = new BufferHttpPayloadWriter(
                            ctx.headersFactory().newTrailers(), payloadProcessor);
                    service.handle(ctx, request.toBlockingStreamingRequest(),
                            new DefaultBlockingStreamingHttpServerResponse(OK, request.version(),
                            ctx.headersFactory().newHeaders(), payloadWriter, ctx.executionContext().bufferAllocator(),
                                    sendMeta, metaSent));
                } catch (Throwable cause) {
                    tiCancellable.setDone(cause);
                    if (metaSent.compareAndSet(false, true)) {
                        subscriber.onError(cause);
                    } else {
                        payloadProcessor.onError(cause);
                    }
                    return;
                }
                tiCancellable.setDone();
            }
        };
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(service::close);
    }

    @Override
    public HttpExecutionStrategy executionStrategy() {
        return effectiveStrategy;
    }

    static StreamingHttpService transform(final BlockingStreamingHttpService service) {
        // The recommended approach for filtering is using the filter factories which forces people to use the
        // StreamingHttpServiceFilter API and use the effective strategy. When that path is used, then we will not get
        // here as the intermediate transitions take care of returning the original StreamingHttpService.
        // If we are here, it is for a user implemented BlockingStreamingHttpService, so we assume the strategy provided
        // by the passed service is the effective strategy.
        return new BlockingStreamingHttpServiceToStreamingHttpService(service, service.executionStrategy());
    }

    static final class BufferHttpPayloadWriter implements HttpPayloadWriter<Buffer> {

        private final ConnectablePayloadWriter<Buffer> payloadWriter = new ConnectablePayloadWriter<>();
        private final HttpHeaders trailers;
        private final CompletableProcessor payloadProcessor;

        BufferHttpPayloadWriter(final HttpHeaders trailers, final CompletableProcessor payloadProcessor) {
            this.trailers = trailers;
            this.payloadProcessor = payloadProcessor;
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
                payloadProcessor.onComplete();
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
