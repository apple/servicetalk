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

import io.servicetalk.concurrent.Completable.Subscriber;
import io.servicetalk.concurrent.internal.CompletablePlugin;
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.function.BiConsumer;

final class AsyncContextCompletablePlugin implements CompletablePlugin {
    static final CompletablePlugin COMPLETABLE_PLUGIN = new AsyncContextCompletablePlugin();

    private AsyncContextCompletablePlugin() {
        // singleton
    }

    @Override
    public void handleSubscribe(final Subscriber subscriber,
                                final SignalOffloader offloader,
                                final BiConsumer<? super Subscriber, SignalOffloader> handleSubscribe) {
        AsyncContextMap saved = AsyncContext.current();
        try {
            handleSubscribe.accept(DefaultAsyncContextProvider.INSTANCE.wrap(subscriber, saved), offloader);
        } finally {
            AsyncContext.replace(saved);
        }
    }
}
