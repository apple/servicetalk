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

import java.util.Map;

/**
 * Listener for tracing events.
 */
public interface InMemorySpanEventListener {
    /**
     * Called when a span has started.
     *
     * @param span the span
     */
    void onSpanStarted(InMemorySpan span);

    /**
     * Called when an event was logged.
     *
     * @param span        the span the event was associated with
     * @param epochMicros timestamp epoch in microseconds
     * @param eventName   event name
     */
    void onEventLogged(InMemorySpan span, long epochMicros, String eventName);

    /**
     * Called when an event was logged.
     *
     * @param span        the span the event was associated with
     * @param epochMicros timestamp epoch in microseconds
     * @param fields      fields as a map
     */
    void onEventLogged(InMemorySpan span, long epochMicros, Map<String, ?> fields);

    /**
     * Called when a span has finished.
     *
     * @param span           the span
     * @param durationMicros duration in microseconds
     */
    void onSpanFinished(InMemorySpan span, long durationMicros);
}
