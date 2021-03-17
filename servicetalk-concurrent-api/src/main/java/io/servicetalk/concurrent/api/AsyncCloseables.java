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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.CompletableSource;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.Completable.failed;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;
import static java.util.function.Function.identity;

/**
 * A utility class to create {@link AsyncCloseable}s.
 */
public final class AsyncCloseables {

    private AsyncCloseables() {
        // No instances.
    }

    /**
     * Invokes {@link AsyncCloseable#closeAsyncGracefully()} on the {@code closable}, applies a timeout, and if the
     * timeout fires forces a call to {@link AsyncCloseable#closeAsync()}.
     *
     * @param closable The {@link AsyncCloseable} to initiate {@link AsyncCloseable#closeAsyncGracefully()} on.
     * @param timeout The timeout duration to wait for {@link AsyncCloseable#closeAsyncGracefully()} to complete.
     * @param timeoutUnit The time unit applied to {@code timeout}.
     * @return A {@link Completable} that is notified once the close is complete.
     */
    public static Completable closeAsyncGracefully(AsyncCloseable closable, long timeout, TimeUnit timeoutUnit) {
        return closable.closeAsyncGracefully().timeout(timeout, timeoutUnit).onErrorResume(
                t -> t instanceof TimeoutException ? closable.closeAsync() : failed(t)
        );
    }

    /**
     * Creates an empty {@link ListenableAsyncCloseable} that does nothing when
     * {@link ListenableAsyncCloseable#closeAsync()} apart from completing the
     * {@link ListenableAsyncCloseable#onClose()}.
     *
     * @return A new {@link ListenableAsyncCloseable}.
     */
    public static ListenableAsyncCloseable emptyAsyncCloseable() {
        return new ListenableAsyncCloseable() {

            private final CompletableProcessor onClose = new CompletableProcessor();
            private final Completable closeAsync = new SubscribableCompletable() {
                @Override
                protected void handleSubscribe(final Subscriber subscriber) {
                    onClose.onComplete();
                    onClose.subscribeInternal(subscriber);
                }
            };

            @Override
            public Completable closeAsync() {
                return closeAsync;
            }

            @Override
            public Completable onClose() {
                return onClose;
            }
        };
    }

    /**
     * Wraps the passed {@link AsyncCloseable} and creates a new {@link ListenableAsyncCloseable}.
     * This method owns the passed {@link AsyncCloseable} after this method returns and hence it should not be used by
     * the caller after this method returns.
     *
     * @param asyncCloseable {@link AsyncCloseable} to convert to {@link ListenableAsyncCloseable}.
     * @return A new {@link ListenableAsyncCloseable}.
     */
    public static ListenableAsyncCloseable toListenableAsyncCloseable(AsyncCloseable asyncCloseable) {
        return toListenableAsyncCloseable(asyncCloseable, identity());
    }

    /**
     * Wraps the passed {@link AsyncCloseable} and creates a new {@link ListenableAsyncCloseable}.
     * This method owns the passed {@link AsyncCloseable} after this method returns and hence it should not be used by
     * the caller after this method returns.
     *
     * @param asyncCloseable {@link AsyncCloseable} to convert to {@link ListenableAsyncCloseable}.
     * @param onCloseDecorator {@link Function} that can decorate the {@link Completable} returned from the returned
     * {@link ListenableAsyncCloseable#onClose()}.
     * @return A new {@link ListenableAsyncCloseable}.
     */
    public static ListenableAsyncCloseable toListenableAsyncCloseable(
            AsyncCloseable asyncCloseable, Function<Completable, Completable> onCloseDecorator) {
        return new ListenableAsyncCloseable() {

            private final CompletableProcessor onCloseProcessor = new CompletableProcessor();
            private final Completable onClose = onCloseDecorator.apply(onCloseProcessor);

            @Override
            public Completable closeAsyncGracefully() {
                return new SubscribableCompletable() {
                    @Override
                    protected void handleSubscribe(final Subscriber subscriber) {
                        asyncCloseable.closeAsyncGracefully().subscribeInternal(onCloseProcessor);
                        onClose.subscribeInternal(subscriber);
                    }
                };
            }

            @Override
            public Completable onClose() {
                return onClose;
            }

            @Override
            public Completable closeAsync() {
                return new SubscribableCompletable() {
                    @Override
                    protected void handleSubscribe(final Subscriber subscriber) {
                        asyncCloseable.closeAsync().subscribeInternal(onCloseProcessor);
                        onClose.subscribeInternal(subscriber);
                    }
                };
            }
        };
    }

    /**
     * Creates a new {@link ListenableAsyncCloseable} which uses the passed {@link Supplier} to get the implementation
     * of close.
     *
     * @param closeableResource {@link CloseableResource} that is to be wrapped into a {@link ListenableAsyncCloseable}.
     * {@link CloseableResource#doClose(boolean)} will be called when the returned {@link ListenableAsyncCloseable} is
     * {@link ListenableAsyncCloseable#closeAsync() closed} or
     * {@link ListenableAsyncCloseable#closeAsyncGracefully() gracefully closed} for the first time.
     * @return A new {@link ListenableAsyncCloseable}.
     */
    public static ListenableAsyncCloseable toAsyncCloseable(CloseableResource closeableResource) {
        return new DefaultAsyncCloseable(closeableResource);
    }

    /**
     * Creates a new {@link CompositeCloseable}.
     *
     * @return A new {@link CompositeCloseable}.
     */
    public static CompositeCloseable newCompositeCloseable() {
        return new DefaultCompositeCloseable();
    }

    /**
     * A resource that can be converted to an {@link AsyncCloseable}.
     */
    @FunctionalInterface
    public interface CloseableResource {

        /**
         * Supplies the {@link Completable} representing the close.
         *
         * @param graceful {@code true} if the returned {@link Completable} should attempt to close gracefully.
         * @return {@link Completable} representing close of the resource.
         */
        Completable doClose(boolean graceful);
    }

    private static final class DefaultAsyncCloseable implements ListenableAsyncCloseable {

        private static final int IDLE = 0;
        private static final int CLOSED_GRACEFULLY = 1;
        private static final int HARD_CLOSE = 2;
        private static final AtomicIntegerFieldUpdater<DefaultAsyncCloseable> closedUpdater =
                newUpdater(DefaultAsyncCloseable.class, "closed");
        private final CloseableResource closeableResource;
        private final CompletableProcessor onClose = new CompletableProcessor();

        @SuppressWarnings("unused")
        private volatile int closed;

        DefaultAsyncCloseable(final CloseableResource closeableResource) {
            this.closeableResource = closeableResource;
        }

        @Override
        public Completable closeAsync() {
            return new SubscribableCompletable() {
                @Override
                protected void handleSubscribe(final Subscriber subscriber) {
                    onClose.subscribeInternal(subscriber);
                    if (closedUpdater.getAndSet(DefaultAsyncCloseable.this, HARD_CLOSE) != HARD_CLOSE) {
                        closeableResource.doClose(false).subscribeInternal(onClose);
                    }
                }
            };
        }

        @Override
        public Completable closeAsyncGracefully() {
            return new SubscribableCompletable() {
                @Override
                protected void handleSubscribe(final Subscriber subscriber) {
                    onClose.subscribeInternal(subscriber);
                    if (closedUpdater.compareAndSet(DefaultAsyncCloseable.this, IDLE, CLOSED_GRACEFULLY)) {
                        closeableResource.doClose(true).subscribeInternal(onClose);
                    }
                }
            };
        }

        @Override
        public Completable onClose() {
            return onClose;
        }
    }

    private abstract static class SubscribableCompletable extends Completable implements CompletableSource {

        @Override
        public final void subscribe(final Subscriber subscriber) {
            subscribeInternal(subscriber);
        }
    }
}
