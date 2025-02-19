/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.SingleSource;

final class SubscribableSources {

    private SubscribableSources() {
        // No instances
    }

    /**
     * A {@link Completable} that is also a {@link CompletableSource} and hence can be subscribed.
     * <p>
     * Typically, this will be used to implement a {@link Completable} that does not require an additional allocation
     * when converting to a {@link CompletableSource} via {@link SourceAdapters#toSource(Completable)}.
     */
    abstract static class SubscribableCompletable extends Completable implements CompletableSource {

        @Override
        public final void subscribe(final Subscriber subscriber) {
            subscribeInternal(subscriber);
        }
    }

    /**
     * A {@link Single} that is also a {@link SingleSource} and hence can be subscribed.
     * <p>
     * Typically, this will be used to implement a {@link Single} that does not require an additional allocation
     * when converting to a {@link SingleSource} via {@link SourceAdapters#toSource(Single)}.
     *
     * @param <T> Type of result of this {@link SubscribableSingle}.
     */
    abstract static class SubscribableSingle<T> extends Single<T> implements SingleSource<T> {

        @Override
        public final void subscribe(final Subscriber<? super T> subscriber) {
            subscribeInternal(subscriber);
        }
    }

    /**
     * A {@link Publisher} that is also a {@link PublisherSource} and hence can be subscribed.
     * <p>
     * Typically, this will be used to implement a {@link Publisher} that does not require an additional allocation
     * when converting to a {@link PublisherSource} via {@link SourceAdapters#toSource(Publisher)}.
     *
     * @param <T> Type of the items emitted by this {@link SubscribablePublisher}.
     */
    abstract static class SubscribablePublisher<T> extends Publisher<T> implements PublisherSource<T> {

        @Override
        public final void subscribe(final Subscriber<? super T> subscriber) {
            subscribeInternal(subscriber);
        }
    }
}
