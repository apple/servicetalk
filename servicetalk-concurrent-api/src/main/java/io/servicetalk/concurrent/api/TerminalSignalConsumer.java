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

/**
 * Callback interface for {@link Publisher} and {@link Completable} on which only a single method is ever called
 * matching the terminal outcome of the associated {@code Source} and {@code Subscription}.
 */
public interface TerminalSignalConsumer {

    /**
     * Callback to signal completion of the {@code Subscription} for this {@code Subscriber}.
     */
    void onComplete();

    /**
     * Callback to signal an {@link Throwable error} of the {@code Subscription} for this {@code Subscriber}.
     *
     * @param throwable the observed {@link Throwable}.
     */
    void onError(Throwable throwable);

    /**
     * Callback to signal cancellation of the {@code Subscription} by this {@code Subscriber}.
     */
    void cancel();
}
