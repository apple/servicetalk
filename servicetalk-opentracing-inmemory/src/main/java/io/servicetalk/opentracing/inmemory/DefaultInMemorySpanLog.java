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
package io.servicetalk.opentracing.inmemory;

import io.servicetalk.opentracing.inmemory.api.InMemorySpanLog;

import java.util.Map;
import javax.annotation.Nullable;

final class DefaultInMemorySpanLog implements InMemorySpanLog {
    private final long epochMicros;
    private final String eventName;
    @Nullable
    private final Map<String, ?> fields;

    /**
     * Create a new instance.
     *
     * @param epochMicros the microseconds since epoch.
     * @param eventName the name of the event.
     * @param fields the fields of the event, or {@code null} if none available.
     */
    DefaultInMemorySpanLog(long epochMicros, String eventName, @Nullable Map<String, ?> fields) {
        this.epochMicros = epochMicros;
        this.eventName = eventName;
        this.fields = fields;
    }

    @Override
    public long epochMicros() {
        return epochMicros;
    }

    @Override
    public String eventName() {
        return eventName;
    }

    @Override
    @Nullable
    public Map<String, ?> fields() {
        return fields;
    }
}
