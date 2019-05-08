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
package io.servicetalk.concurrent.internal;

import javax.annotation.Nullable;

/**
 * Used in scenarios where a subscribe to an asynchronous source is subscribed to, but there is already a subscriber
 * and the source doesn't support multiple subscribers.
 */
public final class DuplicateSubscribeException extends RejectedSubscribeException {
    private static final long serialVersionUID = 8437101886051361471L;

    /**
     * Create a new instance.
     * @param existingSubscriber The existing subscriber, or state that prevented the subscribe from completing.
     * @param attemptedSubscriber The subscriber which failed to subscribe.
     */
    public DuplicateSubscribeException(@Nullable Object existingSubscriber, Object attemptedSubscriber) {
        super("Duplicate subscribes are not supported. Existing: " + existingSubscriber + " Attempted: " +
                attemptedSubscriber);
    }

    /**
     * Create a new instance.
     * @param existingSubscriber The existing subscriber, or state that prevented the subscribe from completing.
     * @param attemptedSubscriber The subscriber which failed to subscribe.
     * @param message An optional message clarifying the situation.
     */
    public DuplicateSubscribeException(@Nullable Object existingSubscriber,
                                       Object attemptedSubscriber,
                                       String message) {
        super("Duplicate subscribes are not supported. Existing: " + existingSubscriber +
                " Attempted: " + attemptedSubscriber + ": " + message);
    }
}
