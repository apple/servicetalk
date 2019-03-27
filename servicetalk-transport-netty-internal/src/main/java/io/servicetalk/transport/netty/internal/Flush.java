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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.netty.internal.FlushStrategy.WriteEventsListener;

import io.netty.channel.Channel;
import io.netty.util.concurrent.EventExecutor;

import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static java.util.Objects.requireNonNull;

/**
 * Utilities related to channel flush.
 */
final class Flush {

    private Flush() {
        // no instances
    }

    /**
     * Apply the passed {@link FlushStrategy} to the passed {@link Publisher} such that the passed {@link Channel} is
     * flushed according to the {@link FlushStrategy}.
     *
     * @param channel Channel to flush.
     * @param source Original source.
     * @param flushStrategy {@link FlushStrategy} to apply.
     * @param <T> Type of elements emitted by {@code source}.
     * @return {@link Publisher} that forwards all items from {@code source} and flushes the channel as directed by
     * {@link FlushStrategy}.
     */
    static <T> Publisher<T> composeFlushes(Channel channel, Publisher<T> source, FlushStrategy flushStrategy) {
        requireNonNull(channel);
        requireNonNull(flushStrategy);
        return source.liftSync(subscriber -> new FlushSubscriber<>(flushStrategy, subscriber, channel));
    }

    private static final class FlushSubscriber<T> implements Subscriber<T> {
        private final EventExecutor eventLoop;
        private final Subscriber<? super T> subscriber;
        private final WriteEventsListener writeEventsListener;
        private volatile boolean enqueueFlush;

        FlushSubscriber(FlushStrategy flushStrategy, Subscriber<? super T> subscriber, Channel channel) {
            this.eventLoop = requireNonNull(channel.eventLoop());
            this.subscriber = requireNonNull(subscriber);
            this.writeEventsListener = flushStrategy.apply(() -> {
                if (enqueueFlush) {
                    eventLoop.execute(channel::flush);
                } else {
                    channel.flush();
                }
            });
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            try {
                writeEventsListener.writeStarted();
            } catch (Throwable t) {
                subscription.cancel();
                subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                subscriber.onError(t);
                return;
            }
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    subscription.request(n);
                }

                @Override
                public void cancel() {
                    subscription.cancel();
                    writeEventsListener.writeCancelled();
                }
            });
        }

        @Override
        public void onNext(T t) {
            // We do a volatile load on enqueueFlush because for users on the "slow path" (writing off the event loop
            // thread) we can avoid the volatile store on each onNext operation. Note since we only store to
            // enqueueFlush in this Subscriber method there will be no concurrency so we don't have to use any atomic
            // operations.
            // We check the enqueueFlush after inEventLoop because for users which want the "fast path" (writing on
            // event loop thread) we short circuit on the "in event loop check".
            if (!eventLoop.inEventLoop() && !enqueueFlush) {
                enqueueFlush = true;
            }
            subscriber.onNext(t);
            writeEventsListener.itemWritten();
        }

        @Override
        public void onError(Throwable t) {
            try {
                writeEventsListener.writeTerminated();
            } catch (Throwable t1) {
                t.addSuppressed(t1);
            }
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            try {
                writeEventsListener.writeTerminated();
            } catch (Throwable t) {
                subscriber.onError(t);
                return;
            }
            subscriber.onComplete();
        }
    }
}
