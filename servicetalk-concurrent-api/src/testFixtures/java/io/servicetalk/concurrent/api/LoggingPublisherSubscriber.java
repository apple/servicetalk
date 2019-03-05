/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Subscriber} that wraps another {@link Subscriber}, logging all signals received by the {@link Subscriber},
 * or sent via the {@link Subscription}.
 *
 * @param <T> Type of items received by the {@code Subscriber}.
 */
public class LoggingPublisherSubscriber<T> implements Subscriber<T> {
    private final Logger logger;
    private final Subscriber<T> delegate;

    /**
     * Create a {@link LoggingPublisherSubscriber} that wraps the {@code delegate}, and uses the specified {@code name}
     * for logging.
     *
     * @param name the logging name.
     * @param delegate the {@link Subscriber} to delegate calls to.
     */
    public LoggingPublisherSubscriber(final String name, final Subscriber<T> delegate) {
        this.logger = LoggerFactory.getLogger(name);
        this.delegate = delegate;
    }

    @Override
    public void onSubscribe(final Subscription s) {
        logger.info("onSubscribe({})", s);
        delegate.onSubscribe(new Subscription() {
            @Override
            public void request(final long n) {
                logger.info("request({})", n);
                s.request(n);
            }

            @Override
            public void cancel() {
                logger.info("cancel()");
                s.cancel();
            }
        });
    }

    @Override
    public void onNext(final T t) {
        logger.info("onNext({})", t);
        delegate.onNext(t);
    }

    @Override
    public void onError(final Throwable t) {
        logger.info("onError({})", t, null); // Pass null so that `t` gets treated as an arg to fill the `{}` with.
        delegate.onError(t);
    }

    @Override
    public void onComplete() {
        logger.info("onComplete()");
        delegate.onComplete();
    }
}
