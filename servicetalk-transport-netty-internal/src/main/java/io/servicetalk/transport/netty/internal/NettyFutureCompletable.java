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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;

import io.netty.util.concurrent.Future;

import java.util.function.Supplier;

/**
 * A {@link Completable} that wraps a netty {@link Future}.
 */
public final class NettyFutureCompletable extends SubscribableCompletable {

    private final Supplier<Future<?>> futureSupplier;

    /**
     * New instance.
     *
     * @param futureSupplier A {@link Supplier} that is invoked every time this {@link Completable} is subscribed.
     */
    public NettyFutureCompletable(Supplier<Future<?>> futureSupplier) {
        this.futureSupplier = futureSupplier;
    }

    @Override
    protected void handleSubscribe(Subscriber subscriber) {
        Future<?> future = futureSupplier.get();
        subscriber.onSubscribe(() -> future.cancel(true));
        connectToSubscriber(subscriber, future);
    }

    static void connectToSubscriber(final Subscriber subscriber, final Future<?> future) {
        future.addListener(f -> {
            Throwable cause = f.cause();
            if (cause == null) {
                subscriber.onComplete();
            } else {
                subscriber.onError(cause);
            }
        });
    }
}
