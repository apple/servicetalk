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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * A {@link ConnectionFactory} that limits the total number of active connections created using this
 * {@link ConnectionFactory}. A connection is considered active if {@link ListenableAsyncCloseable#onClose()} has
 * not yet terminated.
 *
 * @param <ResolvedAddress> The type of a resolved address that can be used for connecting.
 * @param <C> The type of connections created by this factory.
 */
public final class LimitingActiveConnectionFactoryFilter<ResolvedAddress, C extends ListenableAsyncCloseable>
  implements ConnectionFactory<ResolvedAddress, C> {

    private final ConnectionFactory<ResolvedAddress, C> original;
    private final ConnectionLimiter<ResolvedAddress> limiter;

    private LimitingActiveConnectionFactoryFilter(final ConnectionFactory<ResolvedAddress, C> original,
                                                  final ConnectionLimiter<ResolvedAddress> limiter) {
        this.original = original;
        this.limiter = limiter;
    }

    @Override
    public Single<C> newConnection(final ResolvedAddress resolvedAddress) {
        return new Single<C>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super C> subscriber) {
                if (limiter.isConnectAllowed(resolvedAddress)) {
                    original.newConnection(resolvedAddress)
                            .subscribe(new CountingSubscriber<>(subscriber, limiter, resolvedAddress));
                } else {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onError(limiter.newConnectionRefusedException(resolvedAddress));
                }
            }
        };
    }

    @Override
    public Completable onClose() {
        return original.onClose();
    }

    @Override
    public Completable closeAsync() {
        return original.closeAsync();
    }

    /**
     * Create a new {@link ConnectionFactory} that only creates a maximum of {@code maxConnections} active connections.
     *
     * @param original {@link ConnectionFactory} used to create the connections.
     * @param maxConnections Maximum number of active connections to create.
     * @param <A> The type of a resolved address that can be used for connecting.
     * @param <C> The type of connections created by the returned factory.
     * @return A new {@link ConnectionFactory} that limits the number of active connections.
     */
    public static <A, C extends ListenableAsyncCloseable> ConnectionFactory<A, C> withMaxConnections(
            ConnectionFactory<A, C> original, int maxConnections) {
        return withLimiter(original, new MaxConnectionsLimiter<>(maxConnections));
    }

    /**
     * Create a new {@link ConnectionFactory} that limits the created connections using the passed
     * {@link ConnectionLimiter}.
     *
     * @param original {@link ConnectionFactory} used to create the connections.
     * @param limiter {@link ConnectionLimiter} to use.
     * @param <A> The type of a resolved address that can be used for connecting.
     * @param <C> The type of connections created by the returned factory.
     * @return A new {@link ConnectionFactory} that limits the number of active connections.
     */
    public static <A, C extends ListenableAsyncCloseable> ConnectionFactory<A, C> withLimiter(
            ConnectionFactory<A, C> original, ConnectionLimiter<A> limiter) {
        return new LimitingActiveConnectionFactoryFilter<>(original, limiter);
    }

    /**
     * A contract to limit number of connections created by {@link LimitingActiveConnectionFactoryFilter}.
     * <p>
     * The following rules apply:
     * <ul>
     *     <li>{@link #isConnectAllowed(Object)} <em>MUST</em> be called before calling
     *     {@link #onConnectionClose(Object)}.</li>
     *     <li>{@link #onConnectionClose(Object)} <em>MAY</em> be called at most once for each call to
     *     {@link #isConnectAllowed(Object)}.</li>
     * </ul>
     *
     * @param <ResolvedAddress>  The type of a resolved address that can be used for connecting.
     */
    public interface ConnectionLimiter<ResolvedAddress> {

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
            return new ConnectException("No more connections allowed for the host: " + target);
        }
    }

    private static class MaxConnectionsLimiter<ResolvedAddress> implements ConnectionLimiter<ResolvedAddress> {

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
            implements Single.Subscriber<C> {

        private static final AtomicIntegerFieldUpdater<CountingSubscriber> doneUpdater =
                newUpdater(CountingSubscriber.class, "done");

        @SuppressWarnings("unused")
        private volatile int done;
        private final Single.Subscriber<? super C> original;
        private final ConnectionLimiter<A> limiter;
        private final A address;

        CountingSubscriber(final Single.Subscriber<? super C> original, final ConnectionLimiter<A> limiter,
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
                    original.onError(new ConnectException("Null connection received."));
                }
            } else {
                result.onClose().doFinally(this::sendCloseCallback).subscribe();
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
