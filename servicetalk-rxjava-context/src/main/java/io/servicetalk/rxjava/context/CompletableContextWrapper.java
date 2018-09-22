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
package io.servicetalk.rxjava.context;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;

import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.CompletableSource;
import io.reactivex.internal.fuseable.HasUpstreamCompletableSource;

import static java.util.Objects.requireNonNull;

final class CompletableContextWrapper extends Completable implements HasUpstreamCompletableSource {
    private final Completable source;

    CompletableContextWrapper(Completable source) {
        this.source = requireNonNull(source);
    }

    @Override
    protected void subscribeActual(CompletableObserver actual) {
        AsyncContextMap saved = AsyncContext.current();
        try {
            source.subscribe(new ContextPreservingCompletableObserver(saved, actual));
        } finally {
            AsyncContext.replace(saved);
        }
    }

    @Override
    public CompletableSource source() {
        return source;
    }
}
