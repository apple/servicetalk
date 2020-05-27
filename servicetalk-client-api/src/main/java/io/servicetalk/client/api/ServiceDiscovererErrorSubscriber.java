/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.ThrowableUtils;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;

/**
 * Designed to listen for {@link Throwable}s from {@link ServiceDiscoverer#discover(Object) discovery publisher} and
 * provide notification when it emits any error or terminates.
 */
final class ServiceDiscovererErrorSubscriber extends DelayedCancellable implements Subscriber<Throwable> {
    @Nullable
    private volatile Processor onSdError = newCompletableProcessor();

    /**
     * Get {@link Completable} that will complete when a {@link ServiceDiscoverer#discover(Object) discovery publisher}
     * emits any error or terminates.
     *
     * @return A {@link Completable} that will complete when a
     * {@link ServiceDiscoverer#discover(Object) discovery publisher} emits any error or terminates
     */
    Completable onSdError() {
        Processor onSdError = this.onSdError;
        return onSdError == null ? failed(terminatedException("onSdError()")) : fromSource(onSdError);
    }

    @Override
    public void onSubscribe(final Subscription s) {
        s.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(final Throwable t) {
        Processor onSdError = this.onSdError;
        if (onSdError != null) {
            this.onSdError = newCompletableProcessor();
            onSdError.onError(t);
        }
    }

    @Override
    public void onError(final Throwable t) {
        Processor onSdError = this.onSdError;
        if (onSdError != null) {
            this.onSdError = null;
            onSdError.onError(t);
        }
    }

    @Override
    public void onComplete() {
        Processor onSdError = this.onSdError;
        if (onSdError != null) {
            this.onSdError = null;
            onSdError.onError(terminatedException("onComplete()"));
        }
    }

    private static Throwable terminatedException(final String method) {
        return StacklessIllegalStateException.newInstance("ServiceDiscoverer is terminated",
                ServiceDiscovererErrorSubscriber.class, method);
    }

    private static final class StacklessIllegalStateException extends IllegalStateException {
        private StacklessIllegalStateException(final String message) {
            super(message);
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }

        public static StacklessIllegalStateException newInstance(final String message, final Class<?> clazz,
                                                                 final String method) {
            return ThrowableUtils.unknownStackTrace(new StacklessIllegalStateException(message), clazz, method);
        }
    }
}
