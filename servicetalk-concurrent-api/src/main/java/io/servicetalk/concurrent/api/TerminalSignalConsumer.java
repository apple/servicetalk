/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;

/**
 * A contract that provides discrete callbacks for various ways in which a {@link PublisherSource.Subscriber} or a
 * {@link CompletableSource.Subscriber} can terminate.
 */
public interface TerminalSignalConsumer {
    /**
     * Callback to indicate termination via {@link PublisherSource.Subscriber#onComplete()} or
     * {@link CompletableSource.Subscriber#onComplete()}.
     */
    void onComplete();

    /**
     * Callback to indicate termination via {@link PublisherSource.Subscriber#onError(Throwable)} or
     * {@link CompletableSource.Subscriber#onError(Throwable)}.
     *
     * @param throwable the observed {@link Throwable}.
     */
    void onError(Throwable throwable);

    /**
     * Callback to indicate termination via {@link Cancellable#cancel()}.
     */
    void cancel();

    /**
     * Create a {@link TerminalSignalConsumer} where each method executes a {@link Runnable#run()}.
     * @param runnable The {@link Runnable} which is invoked in each method of the returned
     * {@link TerminalSignalConsumer}.
     * @return a {@link TerminalSignalConsumer} where each method executes a {@link Runnable#run()}.
     */
    static TerminalSignalConsumer from(Runnable runnable) {
        return new RunnableTerminalSignalConsumer(runnable);
    }
}
