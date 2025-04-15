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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.PublisherSource;

import javax.annotation.Nullable;

/**
 * Utility instances for Subscribers which don't do anything on events.
 */
public class NoopSubscribers {

    /**
     * An instance of a {@link PublisherSource.Subscriber} that doesn't handle any events.
     */
    public static final PublisherSource.Subscriber<Object> NOOP_PUBLISHER_SUBSCRIBER = new NoopPublisherSubscriber();

    private static final class NoopPublisherSubscriber implements PublisherSource.Subscriber<Object> {

        static final NoopPublisherSubscriber INSTANCE = new NoopPublisherSubscriber();

        private NoopPublisherSubscriber() {
            // Singleton
        }

        @Override
        public void onSubscribe(final PublisherSource.Subscription subscription) {
        }

        @Override
        public void onNext(@Nullable final Object o) {
        }

        @Override
        public void onError(final Throwable t) {
        }

        @Override
        public void onComplete() {
        }
    }
}
