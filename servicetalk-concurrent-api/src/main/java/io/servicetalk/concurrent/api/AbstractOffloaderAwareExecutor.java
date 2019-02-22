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

import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.internal.SignalOffloaderFactory;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

abstract class AbstractOffloaderAwareExecutor implements SignalOffloaderFactory, Executor {

    private static final AtomicReferenceFieldUpdater<AbstractOffloaderAwareExecutor, CompletableProcessor>
            onCloseUpdater = newUpdater(AbstractOffloaderAwareExecutor.class, CompletableProcessor.class, "onClose");

    @SuppressWarnings("unused")
    @Nullable
    private volatile CompletableProcessor onClose;

    @Override
    public Completable onClose() {
        return getOrCreateOnClose();
    }

    @Override
    public Completable closeAsync() {
        return new Completable() {
            @Override
            protected void handleSubscribe(Subscriber subscriber) {
                CompletableProcessor onClose = getOrCreateOnClose();
                onClose.subscribe(subscriber);
                try {
                    // If closeAsync() is subscribed multiple times, we will call this method as many times.
                    // Since doClose() is idempotent and usually cheap, it is OK as compared to implementing at most
                    // once semantics.
                    doClose();
                } catch (Throwable cause) {
                    onClose.onError(cause);
                    return;
                }
                onClose.onComplete();
            }
        };
    }

    private CompletableProcessor getOrCreateOnClose() {
        CompletableProcessor onClose = this.onClose;
        if (onClose != null) {
            return onClose;
        }
        final CompletableProcessor newOnClose = new CompletableProcessor();
        if (onCloseUpdater.compareAndSet(this, null, newOnClose)) {
            return newOnClose;
        }
        onClose = this.onClose;
        assert onClose != null;
        return onClose;
    }

    /**
     * Do any close actions required for this {@link Executor}.
     * This method MUST be idempotent.
     */
    abstract void doClose();
}
