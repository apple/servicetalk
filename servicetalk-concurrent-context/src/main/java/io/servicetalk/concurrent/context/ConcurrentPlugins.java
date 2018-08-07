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
package io.servicetalk.concurrent.context;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import org.reactivestreams.Subscriber;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static io.servicetalk.concurrent.context.DefaultAsyncContextProvider.INSTANCE;

/**
 * Install the {@link AsyncContext} into the {@link io.servicetalk.concurrent.api} primitives.
 */
public final class ConcurrentPlugins {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private ConcurrentPlugins() {
        // no instances
    }

    /**
     * Install the {@link AsyncContext} into the {@link io.servicetalk.concurrent.api} primitives.
     */
    public static void install() {
        if (INSTALLED.compareAndSet(false, true)) {
            Publisher.addSubscribePlugin(ConcurrentPlugins::applyAsyncContext);
            Completable.addSubscribePlugin(ConcurrentPlugins::applyAsyncContext);
            Single.addSubscribePlugin(ConcurrentPlugins::applyAsyncContext);
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyAsyncContext(Subscriber subscriber, Consumer<? super Subscriber> handleSubscribe) {
        AsyncContextMap saved = AsyncContext.current();
        try {
            handleSubscribe.accept(INSTANCE.wrap(subscriber, saved));
        } finally {
            AsyncContext.replace(saved);
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyAsyncContext(Single.Subscriber subscriber, Consumer<? super Single.Subscriber> handleSubscribe) {
        AsyncContextMap saved = AsyncContext.current();
        try {
            handleSubscribe.accept(INSTANCE.wrap(subscriber, saved));
        } finally {
            AsyncContext.replace(saved);
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyAsyncContext(Completable.Subscriber subscriber, Consumer<? super Completable.Subscriber> handleSubscribe) {
        AsyncContextMap saved = AsyncContext.current();
        try {
            handleSubscribe.accept(INSTANCE.wrap(subscriber, saved));
        } finally {
            AsyncContext.replace(saved);
        }
    }
}
