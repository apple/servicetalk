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
package io.servicetalk.client.api;

/**
 * A container for an event that requires acknowledgement when the event is consumed via {@link #eventConsumed()}.
 *
 * @param <T> The type of event.
 */
public interface ConsumableEvent<T> {
    /**
     * Get the event.
     *
     * @return the event.
     */
    T event();

    /**
     * Signify the {@link #event()} has been consumed and any side effects have taken place.
     * <p>
     * Implementations of this method are expected to be idempotent, meaning that if the event is already consumed then
     * invoking this method has no effect.
     */
    void eventConsumed();
}
