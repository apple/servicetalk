/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.TransportObserver;

import java.net.ConnectException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * A {@link ConnectionFactory} that limits the total number of active connections created using this
 * {@link ConnectionFactory}. A connection is considered active if {@link ListenableAsyncCloseable#onClose()} has
 * not yet terminated.
 *
 * @param <ResolvedAddress> The type of a resolved address that can be used for connecting.
 * @param <C> The type of connections created by this factory.
 */
public final class LimitingConnectionFactoryFilter<ResolvedAddress, C extends ListenableAsyncCloseable>
        implements ConnectionFactoryFilter<ResolvedAddress, C> {

    private final ConnectionLimiter<ResolvedAddress, C> limiter;

    private LimitingConnectionFactoryFilter(final ConnectionLimiter<ResolvedAddress, C> limiter) {
        this.limiter = limiter;
    }

    @Override
    public ExecutionStrategy requiredOffloads() {
        return ExecutionStrategy.offloadNone();
    }

    /**
     * Create a new {@link ConnectionFactory} that only creates a maximum of {@code maxConnections} active connections.
     *
     * @param maxConnections Maximum number of active connections to create.
     * @param <A> The type of a resolved address that can be used for connecting.
     * @param <C> The type of connections created by the returned factory.
     * @return A new {@link ConnectionFactory} that limits the number of active connections.
     */
    public static <A, C extends ListenableAsyncCloseable> ConnectionFactoryFilter<A, C> withMax(
            int maxConnections) {
        return new LimitingConnectionFactoryFilter<>(new MaxConnectionsLimiter<>(maxConnections));
    }

    /**
     * Create a new {@link ConnectionFactory} that limits the created connections using the passed
     * {@link ConnectionLimiter}.
     *
     * @param limiter {@link ConnectionLimiter} to use.
     * @param <A> The type of a resolved address that can be used for connecting.
     * @param <C> The type of connections created by the returned factory.
     * @return A new {@link ConnectionFactory} that limits the number of active connections.
     */
    public static <A, C extends ListenableAsyncCloseable> ConnectionFactoryFilter<A, C> with(
            ConnectionLimiter<A, C> limiter) {
        return new LimitingConnectionFactoryFilter<>(requireNonNull(limiter));
    }

    @Override
    public ConnectionFactory<ResolvedAddress, C> create(final ConnectionFactory<ResolvedAddress, C> original) {
        return new LimitingFilter<>(original, limiter);
    }

    /**
     * A contract to limit number of connections created by {@link LimitingConnectionFactoryFilter}.
     * <p>
     * The following rules apply:
     * <ul>
     * <li>{@link #isConnectAllowed(Object)} <em>MUST</em> be called before calling
     * {@link #onConnectionClose(Object)}.</li>
     * <li>{@link #onConnectionClose(Object)} <em>MAY</em> be called at most once for each call to
     * {@link #isConnectAllowed(Object)}.</li>
     * </ul>
     *
     * @param <ResolvedAddress> The type of a resolved address that can be used for connecting.
     * @param <C> The type of connections created by this factory.
     */
    @SuppressWarnings("unused") // C needs to be captured at limiter
    public interface ConnectionLimiter<ResolvedAddress, C extends ListenableAsyncCloseable> {

        /**
         * Requests permission to create a single connection to the passed {@link ResolvedAddress target address}.
         * If this method returns {@code true} then {@link #onConnectionClose(Object)} will be called when the
         * connection created by the caller is closed.
         * <p>
         * A simple counting implementation will typically increment the count in this method and decrement it in
         * {@link #onConnectionClose(Object)}.
         *
         * @param target {@link ResolvedAddress} for which connection is requested.
         * @return {@code true} if connection is allowed.
         */
        boolean isConnectAllowed(ResolvedAddress target);

        /**
         * Callback invoked when a connection created after getting permission from
         * {@link #isConnectAllowed(Object)} is closed.
         *
         * @param target {@link ResolvedAddress} to which connection was created.
         */
        void onConnectionClose(ResolvedAddress target);

        /**
         * Create a {@link Throwable} representing a connection attempt refused, typically  as a result of returning
         * {@code false} from {@link #isConnectAllowed(Object)}.
         *
         * @param target {@link ResolvedAddress} for which connection was refused.
         * @return {@link Throwable} representing a connection attempt was refused.
         */
        default Throwable newConnectionRefusedException(ResolvedAddress target) {
            return new ConnectionLimitReachedException("No more connections allowed for the host: " + target);
        }
    }

    private static final class LimitingFilter<ResolvedAddress, C extends ListenableAsyncCloseable> extends
            DelegatingConnectionFactory<ResolvedAddress, C> {

        private final ConnectionLimiter<ResolvedAddress, C> limiter;

        private LimitingFilter(final ConnectionFactory<ResolvedAddress, C> original,
                               final ConnectionLimiter<ResolvedAddress, C> limiter) {
            super(original);
            this.limiter = limiter;
        }

        @Override
        public Single<C> newConnection(final ResolvedAddress resolvedAddress,
                                       @Nullable final ContextMap context,
                                       @Nullable final TransportObserver observer) {
            return new SubscribableSingle<C>() {
                @Override
                protected void handleSubscribe(final Subscriber<? super C> subscriber) {
                    if (limiter.isConnectAllowed(resolvedAddress)) {
                        toSource(delegate().newConnection(resolvedAddress, context, observer))
                                .subscribe(new CountingSubscriber<>(subscriber, limiter, resolvedAddress));
                    } else {
                        deliverErrorFromSource(subscriber, limiter.newConnectionRefusedException(resolvedAddress));
                    }
                }
            };
        }
    }

    private static final class MaxConnectionsLimiter<ResolvedAddress, C extends ListenableAsyncCloseable>
            implements ConnectionLimiter<ResolvedAddress, C> {

        private static final AtomicIntegerFieldUpdater<MaxConnectionsLimiter> countUpdater =
                newUpdater(MaxConnectionsLimiter.class, "count");

        @SuppressWarnings("unused")
        private volatile int count;
        private final int maxAllowed;

        MaxConnectionsLimiter(int maxAllowed) {
            this.maxAllowed = maxAllowed;
        }

        @Override
        public boolean isConnectAllowed(final ResolvedAddress target) {
            for (;;) {
                final int c = count;
                if (c == maxAllowed) {
                    return false;
                }
                if (countUpdater.compareAndSet(this, c, c + 1)) {
                    return true;
                }
            }
        }

        @Override
        public void onConnectionClose(final ResolvedAddress target) {
            countUpdater.decrementAndGet(this);
        }
    }

    private static final class CountingSubscriber<A, C extends ListenableAsyncCloseable>
            implements Subscriber<C> {

        private static final AtomicIntegerFieldUpdater<CountingSubscriber> doneUpdater =
                newUpdater(CountingSubscriber.class, "done");

        @SuppressWarnings("unused")
        private volatile int done;
        private final Subscriber<? super C> original;
        private final ConnectionLimiter<A, ? extends C> limiter;
        private final A address;

        CountingSubscriber(final Subscriber<? super C> original, final ConnectionLimiter<A, ? extends C> limiter,
                           final A address) {
            this.original = original;
            this.limiter = limiter;
            this.address = address;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            original.onSubscribe(() -> {
                try {
                    sendCloseCallback();
                } finally {
                    cancellable.cancel();
                }
            });
        }

        @Override
        public void onSuccess(@Nullable final C result) {
            if (result == null) {
                try {
                    sendCloseCallback();
                } finally {
                    original.onError(new ConnectException("Null connection received"));
                }
            } else {
                result.onClose().whenFinally(this::sendCloseCallback).subscribe();
                original.onSuccess(result);
            }
        }

        @Override
        public void onError(final Throwable t) {
            try {
                sendCloseCallback();
            } finally {
                original.onError(t);
            }
        }

        private void sendCloseCallback() {
            if (doneUpdater.compareAndSet(this, 0, 1)) {
                limiter.onConnectionClose(address);
            }
        }
    }
}
