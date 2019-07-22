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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.PlatformDependent.throwException;
import static io.servicetalk.http.api.BlockingUtils.futureGetCancelOnInterrupt;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class StreamingHttpServiceToBlockingStreamingHttpService implements BlockingStreamingHttpService {
    private final StreamingHttpService original;

    StreamingHttpServiceToBlockingStreamingHttpService(final StreamingHttpService original) {
        this.original = requireNonNull(original);
    }

    @Override
    public void handle(final HttpServiceContext ctx,
                       final BlockingStreamingHttpRequest request,
                       final BlockingStreamingHttpServerResponse svcResponse) throws Exception {
        // Block handle() for the duration of the request for now (for cancellation reasons)
        futureGetCancelOnInterrupt(handleBlockingRequest(ctx, request, svcResponse).toFuture());
    }

    @Nonnull
    private Completable handleBlockingRequest(final HttpServiceContext ctx,
                                              final BlockingStreamingHttpRequest request,
                                              final BlockingStreamingHttpServerResponse svcResponse) {
        return original.handle(ctx, request.toStreamingRequest(), ctx.streamingResponseFactory())
                .flatMapCompletable(streamingHttpResponse -> {
                    copyMeta(streamingHttpResponse, svcResponse);
                    return new PayloadBodyAndTrailersToPayloadWriter(
                            streamingHttpResponse.payloadBodyAndTrailers(), svcResponse.sendMetaData());
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

    private static class PayloadBodyAndTrailersToPayloadWriter extends SubscribableCompletable {

        private final Publisher<Object> payloadBodyAndTrailers;
        private final HttpPayloadWriter<Buffer> payloadWriter;

        PayloadBodyAndTrailersToPayloadWriter(final Publisher<Object> payloadBodyAndTrailers,
                                              final HttpPayloadWriter<Buffer> payloadWriter) {
            this.payloadBodyAndTrailers = payloadBodyAndTrailers;
            this.payloadWriter = payloadWriter;
        }

        @Override
        protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
            toSource(payloadBodyAndTrailers).subscribe(new PayloadPump(subscriber, payloadWriter));
        }

        private static final class PayloadPump implements PublisherSource.Subscriber<Object> {

            private static final Logger LOGGER = LoggerFactory.getLogger(PayloadPump.class);

            private static final AtomicIntegerFieldUpdater<PayloadPump> terminatedUpdater =
                    newUpdater(PayloadPump.class, "terminated");

            private final Subscriber subscriber;
            private final HttpPayloadWriter<Buffer> payloadWriter;
            private volatile int terminated;
            @Nullable
            private ConcurrentSubscription subscription;

            PayloadPump(final Subscriber subscriber, final HttpPayloadWriter<Buffer> payloadWriter) {
                this.subscriber = subscriber;
                this.payloadWriter = payloadWriter;
            }

            @Override
            public void onSubscribe(final PublisherSource.Subscription inSubscription) {
                // We need to protect sub.cancel() from concurrent invocation with sub.request(MAX)
                subscription = ConcurrentSubscription.wrap(inSubscription);
                subscriber.onSubscribe(subscription);
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(@Nullable final Object bufferOrTrailers) {
                assert bufferOrTrailers != null;
                try {
                    if (bufferOrTrailers instanceof Buffer) {
                        payloadWriter.write((Buffer) bufferOrTrailers);
                        return;
                    }
                    if (bufferOrTrailers instanceof HttpHeaders) {
                        payloadWriter.setTrailers((HttpHeaders) bufferOrTrailers);
                        return;
                    }
                    assert false : "Expected only buffer or trailer in payloadBodyAndTrailers()";
                } catch (IOException e) {
                    try {
                        if (tryTerminate()) {
                            subscriber.onError(e);
                        } else {
                            throwException(e);
                        }
                    } finally {
                        assert subscription != null;
                        subscription.cancel();
                    }
                }
            }

            @Override
            public void onError(final Throwable t) {
                // Don't close the payloadWriter on error, we need to bubble up the exception through the subscriber to
                // communicate the failure
                if (tryTerminate()) {
                    subscriber.onError(t);
                } else {
                    LOGGER.error("Failed to deliver onError() after termination", t);
                }
            }

            @Override
            public void onComplete() {
                try {
                    payloadWriter.close();
                } catch (IOException e) {
                    if (tryTerminate()) {
                        subscriber.onError(e);
                    } else {
                        LOGGER.warn("Failed to deliver IOException from payloadWriter.close() after termination", e);
                    }
                }
                if (tryTerminate()) {
                    subscriber.onComplete();
                }
            }

            boolean tryTerminate() {
                return terminatedUpdater.compareAndSet(this, 0, 1);
            }
        }
    }
}
