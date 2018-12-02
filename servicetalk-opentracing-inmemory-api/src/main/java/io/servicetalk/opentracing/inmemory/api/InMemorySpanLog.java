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
package io.servicetalk.opentracing.inmemory.api;

import io.opentracing.Span;

import java.util.Map;
import javax.annotation.Nullable;

/**
 * A entry that corresponds to {@link Span#log(long, String)} events.
 */
public interface InMemorySpanLog {
    /**
     * Get the microseconds since epoch for this {@link InMemorySpanLog}.
     * @return the microseconds since epoch for this {@link InMemorySpanLog}.
     */
    long epochMicros();

    /**
     * Get the name of the event for this {@link InMemorySpanLog}.
     * @return the name of the event for this {@link InMemorySpanLog}.
     */
    String eventName();

    /**
     * Get the fields associated with this {@link InMemorySpanLog}.
     * @return the fields associated with this {@link InMemorySpanLog}.
     */
    @Nullable
    Map<String, ?> fields();
}
