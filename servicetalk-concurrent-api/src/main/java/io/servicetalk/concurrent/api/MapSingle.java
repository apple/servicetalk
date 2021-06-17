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

import io.servicetalk.concurrent.Cancellable;

import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class MapSingle<T, R> extends AbstractSynchronousSingleOperator<T, R> {

    private final Function<? super T, ? extends R> mapper;

    MapSingle(Single<T> source, Function<? super T, ? extends R> mapper) {
        super(source);
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super R> subscriber) {
        return new Subscriber<T>() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                subscriber.onSubscribe(cancellable);
            }

            @Override
            public void onSuccess(@Nullable T result) {
                final R mappedResult;
                try {
                    mappedResult = mapper.apply(result);
                } catch (Throwable cause) {
                    subscriber.onError(cause);
                    return;
                }
                subscriber.onSuccess(mappedResult);
            }

            @Override
            public void onError(Throwable t) {
                subscriber.onError(t);
            }
        };
    }
}
