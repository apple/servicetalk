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

import io.servicetalk.concurrent.context.AsyncContext;
import io.servicetalk.concurrent.context.AsyncContextMap;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.internal.fuseable.HasUpstreamObservableSource;

import static java.util.Objects.requireNonNull;

final class ObservableContextWrapper<T> extends Observable<T> implements HasUpstreamObservableSource<T> {
    private final Observable<T> source;

    ObservableContextWrapper(Observable<T> source) {
        this.source = requireNonNull(source);
    }

    @Override
    protected void subscribeActual(Observer<? super T> actual) {
        AsyncContextMap saved = AsyncContext.current();
        try {
            source.subscribe(new ContextPreservingObserver<>(saved, actual));
        } finally {
            AsyncContext.replace(saved);
        }
    }

    @Override
    public ObservableSource<T> source() {
        return source;
    }
}
