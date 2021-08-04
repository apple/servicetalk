/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
 * A span that allows reading values at runtime.
 */
public interface InMemorySpan extends Span {
    @Override
    InMemorySpanContext context();

    /**
     * Returns the operation name.
     *
     * @return operation name
     */
    String operationName();

    /**
     * Returns an immutable list of references.
     *
     * @return list of references
     */
    Iterable<? extends InMemoryReference> references();

    /**
     * Returns an unmodifiable view of the tags.
     *
     * @return the tags
     */
    Map<String, Object> tags();

    /**
     * Returns an unmodifiable view of logs. This may return null if the logs are not persisted.
     *
     * @return the logs
     */
    @Nullable
    Iterable<? extends InMemorySpanLog> logs();

    /**
     * Returns the starting epoch in milliseconds. May return -1 if the span is not sampled.
     *
     * @return starting epoch in milliseconds
     */
    long startEpochMicros();
}
