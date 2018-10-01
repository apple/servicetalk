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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Single;

import java.util.function.Function;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;

final class FunctionToSingle<T, R> extends Single<R> {
    private final Function<T, R> func;
    private final T orig;

    FunctionToSingle(final Function<T, R> func, final T orig) {
        this.func = func;
        this.orig = orig;
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super R> subscriber) {
        subscriber.onSubscribe(IGNORE_CANCEL);
        R result;
        try {
            result = func.apply(orig);
        } catch (Throwable t) {
            subscriber.onError(t);
            return;
        }
        subscriber.onSuccess(result);
    }
}
