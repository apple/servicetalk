/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import static java.util.Objects.requireNonNull;

final class LiftSynchronousPublisherToSingle<T, R> extends AbstractNoHandleSubscribeSingle<R> {
    private final Publisher<T> original;
    private final PublisherToSingleOperator<? super T, ? extends R> customOperator;

    LiftSynchronousPublisherToSingle(Publisher<T> original,
                                     PublisherToSingleOperator<? super T, ? extends R> customOperator) {
        this.original = original;
        this.customOperator = requireNonNull(customOperator);
    }

    @Override
    void handleSubscribe(final Subscriber<? super R> subscriber,
                         final CapturedContext capturedContext, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(customOperator.apply(subscriber), capturedContext, contextProvider);
    }
}
