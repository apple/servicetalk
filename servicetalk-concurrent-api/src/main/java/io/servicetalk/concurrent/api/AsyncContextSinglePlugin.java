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

import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.Single;
import io.servicetalk.concurrent.Single.Subscriber;
import io.servicetalk.concurrent.internal.SignalOffloader;
import io.servicetalk.concurrent.internal.SinglePlugin;

import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

final class AsyncContextSinglePlugin implements SinglePlugin {
    static final SinglePlugin SINGLE_PLUGIN = new AsyncContextSinglePlugin();

    private AsyncContextSinglePlugin() {
        // singleton
    }

    @Override
    public void handleSubscribe(final Subscriber<?> subscriber,
                                final SignalOffloader offloader,
                                final BiConsumer<? super Subscriber, SignalOffloader> handleSubscribe) {
        AsyncContextMap saved = AsyncContext.current();
        try {
            handleSubscribe.accept(DefaultAsyncContextProvider.INSTANCE.wrap(subscriber, saved), offloader);
        } finally {
            AsyncContext.replace(saved);
        }
    }

    @Override
    public <X> CompletionStage<X> toCompletionStage(final Single<X> single,
                                                    final Executor executor,
                                                    final BiFunction<Single<X>, Executor,
                                                            CompletionStage<X>> completionStageFactory) {
        return DefaultAsyncContextProvider.INSTANCE.wrap(
                completionStageFactory.apply(single, DefaultAsyncContextProvider.INSTANCE.unwrap(executor)));
    }

    @Override
    public <X> CompletionStage<X> fromCompletionStage(final CompletionStage<X> completionStage) {
        // make a best effort to unwrap the CompletionStage because the AsyncContext will be preserved by the Single
        // after conversion.
        return DefaultAsyncContextProvider.INSTANCE.unwrap(completionStage);
    }
}
