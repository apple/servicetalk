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

import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

/**
 * Runnable used internally for {@link Completable#fromRunnable(Runnable)}.
 * @deprecated Use {@link Completable#fromRunnable(Runnable)}.
 */
@Deprecated
public class RunnableCompletable extends AbstractSynchronousCompletable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RunnableCompletable.class);
    private final Runnable runnable;

    RunnableCompletable(final Runnable runnable) {
        this.runnable = requireNonNull(runnable);
    }

    @Override
    void doSubscribe(final Subscriber subscriber) {
        final ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(currentThread());
        try {
            subscriber.onSubscribe(cancellable);
        } catch (Throwable t) {
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }

        try {
            runnable.run();

            try {
                cancellable.setDone();
                subscriber.onComplete();
            } catch (Throwable t) {
                LOGGER.info("Ignoring exception from onComplete of Subscriber {}.", subscriber, t);
            }
        } catch (Throwable t) {
            cancellable.setDone(t);
            subscriber.onError(t);
        }
    }
}
