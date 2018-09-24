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
package io.servicetalk.opentracing.core.internal;

import io.opentracing.Span;

import java.util.Map;
import javax.annotation.Nullable;

/**
 * A entry that corresponds to {@link Span#log(long, String)} events.
 */
public final class Log {
    private final long epochMicros;
    private final String eventName;
    @Nullable
    private final Map<String, ?> fields;

    /**
     * Instantiates a new {@link Log}.
     *
     * @param epochMicros the microseconds since epoch.
     * @param eventName the name of the event.
     * @param fields the fields of the event, or {@code null} if none available.
     */
    public Log(long epochMicros, String eventName, @Nullable Map<String, ?> fields) {
        this.epochMicros = epochMicros;
        this.eventName = eventName;
        this.fields = fields;
    }

    /**
     * Get the microseconds since epoch for this {@link Log}.
     * @return the microseconds since epoch for this {@link Log}.
     */
    public long epochMicros() {
        return epochMicros;
    }

    /**
     * Get the name of the event for this {@link Log}.
     * @return the name of the event for this {@link Log}.
     */
    public String eventName() {
        return eventName;
    }

    /**
     * Get the fields associated with this {@link Log}.
     * @return the fields associated with this {@link Log}.
     */
    @Nullable
    public Map<String, ?> fields() {
        return fields;
    }
}
