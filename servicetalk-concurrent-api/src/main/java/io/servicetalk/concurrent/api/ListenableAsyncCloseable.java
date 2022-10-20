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

/**
 * Provides a way to subscribe when {@link #closeAsync()} is completed.
 */
public interface ListenableAsyncCloseable extends AsyncCloseable {

    /**
     * Returns a {@link Completable} that is notified once the {@link ListenableAsyncCloseable} was closed.
     * @return the {@code Completable} that is notified on close.
     */
    Completable onClose();

    /**
     * Returns a {@link Completable} that is notified when closing begins.
     * <p>
     * Closing begin might be when a close operation is initiated locally (e.g. subscribing to {@link #closeAsync()}) or
     * it could also be a transport event received from a remote peer (e.g. read a {@code connection: close} header).
     * <p>
     * For backwards compatibility this method maybe functionally equivalent to {@link #onClose()}. Therefore, provides
     * a best-effort leading edge notification of closing, but may fall back to notification on trailing edge.
     * <p>
     * The goal of this method is often to notify asap when closing so this method may not be offloaded and care must
     * be taken to avoid blocking if subscribing to the return {@link Completable}.
     * @return a {@link Completable} that is notified when closing begins.
     */
    default Completable onClosing() {
        return onClose();
    }
}
