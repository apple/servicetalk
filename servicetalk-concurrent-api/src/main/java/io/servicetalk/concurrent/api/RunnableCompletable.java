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

import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnComplete;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

final class RunnableCompletable extends AbstractSynchronousCompletable {
    private final Runnable runnable;

    RunnableCompletable(final Runnable runnable) {
        this.runnable = requireNonNull(runnable);
    }

    @Override
    void doSubscribe(final Subscriber subscriber) {
        final ThreadInterruptingCancellable cancellable = new ThreadInterruptingCancellable(currentThread());
        try {
            subscriber.onSubscribe(cancellable);
            runnable.run();
        } catch (Throwable cause) {
            cancellable.setDone(cause);
            subscriber.onError(cause);
            return;
        }
        // It is safe to set this outside the scope of the try/catch above because we don't do any blocking
        // operations which may be interrupted between the completion of the blockingHttpService call and
        // here.
        cancellable.setDone();
        safeOnComplete(subscriber);
    }
}
