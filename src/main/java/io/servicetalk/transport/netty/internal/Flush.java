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

import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundInvoker;
import io.netty.util.concurrent.EventExecutor;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.FlushStrategyHolder.FlushSignals;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Utilities related to channel flush.
 */
public final class Flush {

    private Flush() {
        // no instances
    }

    /**
     * Composes flush signals from {@code flushSignals} into {@code source}.
     * Returned {@link Publisher} will take care of flushing the channel whenever any item is emitted from {@code flushSignals}.
     *
     * @param channel Channel to flush.
     * @param source Original source.
     * @param flushSignals {@link Publisher} that emits an item whenever it wishes to flush the channel.
     * @param <T> Type of elements emitted by {@code source}.
     * @return {@link Publisher} that forwards all items from {@code source} and flushes the channel whenever any item is emitted from {@code flushSignals}.
     */
    public static <T> Publisher<T> composeFlushes(Channel channel, Publisher<T> source, FlushSignals flushSignals) {
        return new Publisher<T>() {
            @Override
            protected void handleSubscribe(Subscriber<? super T> subscriber) {
                source.subscribe(new FlushSubscriber<>(flushSignals, subscriber, channel));
            }
        };
    }

    private static final class FlushSubscriber<T> implements Subscriber<T>, Runnable {
        private final FlushSignals flushSignals;
        private final Subscriber<? super T> subscriber;
        private final ChannelOutboundInvoker channel;
        private final EventExecutor eventLoop;
        @Nullable
        private Cancellable flushCancellable;
        private volatile boolean enqueueFlush;

        FlushSubscriber(FlushSignals flushSignals, Subscriber<? super T> subscriber, Channel channel) {
            this.flushSignals = requireNonNull(flushSignals);
            this.subscriber = requireNonNull(subscriber);
            this.channel = requireNonNull(channel);
            this.eventLoop = requireNonNull(channel.eventLoop());
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            Cancellable flushCancellable = flushSignals.listen(this);
            this.flushCancellable = flushCancellable;
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    subscription.request(n);
                }

                @Override
                public void cancel() {
                    subscription.cancel();
                    flushCancellable.cancel();
                }
            });
        }

        @Override
        public void onNext(T t) {
            // We do a volatile load on enqueueFlush because for users on the "slow path" (writing off the event loop thread)
            // we can avoid the volatile store on each onNext operation. Note since we only store to enqueueFlush in this
            // Subscriber method there will be no concurrency so we don't have to use any atomic operations.
            // We check the enqueueFlush after inEventLoop because for users which want the "fast path" (writing on event loop thread)
            // we short circuit on the "in event loop check".
            if (!eventLoop.inEventLoop() && !enqueueFlush) {
                enqueueFlush = true;
            }
            subscriber.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            assert flushCancellable != null : "onError called without onSubscribe.";
            flushCancellable.cancel();
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            assert flushCancellable != null : "onComplete called without onSubscribe.";
            flushCancellable.cancel();
            subscriber.onComplete();
        }

        @Override
        public void run() {
            // FlushSignal listener.
            if (enqueueFlush) {
                eventLoop.execute(channel::flush);
            } else {
                channel.flush();
            }
        }
    }
}
