/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource.Subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link PublisherSource.Subscriber} that invokes {@link Subscription#cancel()} in
 * {@link PublisherSource.Subscriber#onSubscribe(Subscription)}.
 */
public final class CancelImmediatelySubscriber implements PublisherSource.Subscriber<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CancelImmediatelySubscriber.class);
    /**
     * Singleton instance.
     */
    public static final CancelImmediatelySubscriber INSTANCE = new CancelImmediatelySubscriber();

    private CancelImmediatelySubscriber() {
        // Singleton
    }

    @Override
    public void onSubscribe(final Subscription s) {
        // Cancel immediately so that the connection can handle this as required.
        s.cancel();
    }

    @Override
    public void onNext(final Object obj) {
        // Can not be here since we never request.
    }

    @Override
    public void onError(final Throwable t) {
        LOGGER.debug("Ignoring error, since subscriber has already cancelled.", t);
    }

    @Override
    public void onComplete() {
        // Ignore.
    }
}
