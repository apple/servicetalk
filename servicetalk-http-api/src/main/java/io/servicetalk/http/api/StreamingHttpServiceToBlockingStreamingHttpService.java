/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;

import java.io.IOException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.BlockingUtils.futureGetCancelOnInterrupt;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.Objects.requireNonNull;

final class StreamingHttpServiceToBlockingStreamingHttpService implements BlockingStreamingHttpService {
    private final StreamingHttpService original;
    private final int demandBatchSize;

    StreamingHttpServiceToBlockingStreamingHttpService(final StreamingHttpService original) {
        this(original, 64);
    }

    StreamingHttpServiceToBlockingStreamingHttpService(final StreamingHttpService original,
                                                       final int demandBatchSize) {
        if (demandBatchSize <= 0) {
            throw new IllegalArgumentException("demandBatchSize: " + demandBatchSize + " (expected >0)");
        }
        this.original = requireNonNull(original);
        this.demandBatchSize = demandBatchSize;
    }

    @Override
    public void handle(final HttpServiceContext ctx,
                       final BlockingStreamingHttpRequest request,
                       final BlockingStreamingHttpServerResponse svcResponse) throws Exception {
        futureGetCancelOnInterrupt(handleBlockingRequest(ctx, request, svcResponse).toFuture());
    }

    @Nonnull
    private Completable handleBlockingRequest(final HttpServiceContext ctx,
                                              final BlockingStreamingHttpRequest request,
                                              final BlockingStreamingHttpServerResponse svcResponse) {
        return original.handle(ctx, request.toStreamingRequest(), ctx.streamingResponseFactory())
                .flatMapCompletable(streamingHttpResponse -> {
                    copyMeta(streamingHttpResponse, svcResponse);
                    return new MessageBodyToPayloadWriter(streamingHttpResponse.payloadBodyAndTrailers(),
                            svcResponse.sendMetaData(), demandBatchSize);
                });
    }

    private void copyMeta(final StreamingHttpResponse streamingResponse,
                          final BlockingStreamingHttpServerResponse svcResponse) {
        svcResponse.setHeaders(streamingResponse.headers());
        svcResponse.status(streamingResponse.status());
        svcResponse.version(streamingResponse.version());
    }

    @Override
    public void close() throws Exception {
        original.closeAsync().toFuture().get();
    }

    @Override
    public void closeGracefully() throws Exception {
        original.closeAsyncGracefully().toFuture().get();
    }

    private static final class MessageBodyToPayloadWriter extends SubscribableCompletable {
        private final Publisher<Object> messageBody;
        private final HttpPayloadWriter<Buffer> payloadWriter;
        private final int demandBatchSize;

        MessageBodyToPayloadWriter(final Publisher<Object> messageBody,
                                   final HttpPayloadWriter<Buffer> payloadWriter,
                                   final int demandBatchSize) {
            this.messageBody = messageBody;
            this.payloadWriter = payloadWriter;
            this.demandBatchSize = demandBatchSize;
        }

        @Override
        protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
            toSource(messageBody).subscribe(new PayloadPump(subscriber, payloadWriter, demandBatchSize));
        }

        private static final class PayloadPump implements PublisherSource.Subscriber<Object> {
            private final Subscriber subscriber;
            private final HttpPayloadWriter<Buffer> payloadWriter;
            @Nullable
            private Subscription subscription;
            private final int demandBatchSize;
            private int itemsToNextRequest;

            PayloadPump(final Subscriber subscriber, final HttpPayloadWriter<Buffer> payloadWriter,
                        final int demandBatchSize) {
                this.subscriber = subscriber;
                this.payloadWriter = payloadWriter;
                this.demandBatchSize = demandBatchSize;
            }

            @Override
            public void onSubscribe(final Subscription inSubscription) {
                // We need to protect sub.cancel() from concurrent invocation.
                subscription = ConcurrentSubscription.wrap(inSubscription);
                subscriber.onSubscribe(subscription);
                itemsToNextRequest = demandBatchSize;
                subscription.request(demandBatchSize);
            }

            @Override
            public void onNext(@Nullable final Object bufferOrTrailers) {
                if (bufferOrTrailers instanceof Buffer) {
                    try {
                        payloadWriter.write((Buffer) bufferOrTrailers);
                    } catch (IOException e) {
                        throwException(e);
                    }
                } else if (bufferOrTrailers instanceof HttpHeaders) {
                    payloadWriter.setTrailers((HttpHeaders) bufferOrTrailers);
                } else {
                    throw new IllegalArgumentException("unsupported type: " + bufferOrTrailers);
                }
                requestMoreIfRequired();
            }

            @Override
            public void onError(final Throwable t) {
                try {
                    payloadWriter.close(t);
                } catch (Throwable cause) {
                    subscriber.onError(cause);
                    return;
                }
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                try {
                    payloadWriter.close();
                } catch (Throwable cause) {
                    subscriber.onError(cause);
                    return;
                }
                subscriber.onComplete();
            }

            private void requestMoreIfRequired() {
                // Request more when we half of the outstanding demand has been delivered. This attempts to keep some
                // outstanding demand in the event there is impedance mismatch between producer and consumer (as opposed to
                // waiting until outstanding demand reaches 0) while still having an upper bound.
                if (--itemsToNextRequest == demandBatchSize >>> 1) {
                    final int toRequest = demandBatchSize - itemsToNextRequest;
                    itemsToNextRequest = demandBatchSize;
                    assert subscription != null;
                    subscription.request(toRequest);
                }
            }
        }
    }
}
