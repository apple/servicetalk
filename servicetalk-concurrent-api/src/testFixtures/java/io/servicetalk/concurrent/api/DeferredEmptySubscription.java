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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.TerminalNotification;

/**
 * An {@link Subscription} that only emits a {@link TerminalNotification} only upon the first call to
 * {@link #request(long)}.
 */
public final class DeferredEmptySubscription implements Subscription {

    private final Subscriber<?> subscriber;
    private final TerminalNotification terminalNotification;

    private boolean done;

    /**
     * New instance.
     *
     * @param subscriber {@link Subscriber} to send the {@code terminalNotification}.
     * @param terminalNotification {@link TerminalNotification} to send.
     */
    public DeferredEmptySubscription(final Subscriber<?> subscriber, final TerminalNotification terminalNotification) {
        this.subscriber = subscriber;
        this.terminalNotification = terminalNotification;
    }

    @Override
    public void request(final long n) {
        if (!done) {
            done = true;
            terminalNotification.terminate(subscriber);
        }
    }

    @Override
    public void cancel() {
        done = true;
    }
}
