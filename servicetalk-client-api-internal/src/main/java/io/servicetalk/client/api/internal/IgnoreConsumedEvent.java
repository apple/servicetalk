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
package io.servicetalk.client.api.internal;

import io.servicetalk.client.api.ConsumableEvent;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ConsumableEvent} which ignores {@link #eventConsumed()}.
 *
 * @param <T> The type of event.
 * @deprecated This class is not used by ServiceTalk internal code anymore and will be removed in the future releases.
 * If you depend on it, consider replica ting this implementation in your codebase.
 */
@Deprecated // FIXME: 0.43 - remove deprecated class
public final class IgnoreConsumedEvent<T> implements ConsumableEvent<T> {
    private final T event;

    /**
     * Create a new instance.
     *
     * @param event The event to return from {@link #event()}.
     */
    public IgnoreConsumedEvent(final T event) {
        this.event = requireNonNull(event);
    }

    @Override
    public T event() {
        return event;
    }

    @Override
    public void eventConsumed() {
    }
}
