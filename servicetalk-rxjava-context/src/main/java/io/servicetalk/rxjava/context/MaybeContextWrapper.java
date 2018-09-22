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

import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import io.reactivex.MaybeSource;
import io.reactivex.internal.fuseable.HasUpstreamMaybeSource;

import static java.util.Objects.requireNonNull;

final class MaybeContextWrapper<T> extends Maybe<T> implements HasUpstreamMaybeSource<T> {
    private final Maybe<T> source;

    MaybeContextWrapper(Maybe<T> source) {
        this.source = requireNonNull(source);
    }

    @Override
    protected void subscribeActual(MaybeObserver<? super T> actual) {
        AsyncContextMap saved = AsyncContext.current();
        try {
            source.subscribe(new ContextPreservingMaybeObserver<>(saved, actual));
        } finally {
            AsyncContext.replace(saved);
        }
    }

    @Override
    public MaybeSource<T> source() {
        return source;
    }
}
