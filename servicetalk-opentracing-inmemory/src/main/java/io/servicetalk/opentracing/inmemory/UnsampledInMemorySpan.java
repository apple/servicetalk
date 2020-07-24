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

import io.servicetalk.opentracing.inmemory.api.InMemoryReference;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanLog;

import io.opentracing.Span;
import io.opentracing.tag.Tag;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

final class UnsampledInMemorySpan extends AbstractInMemorySpan {
    UnsampledInMemorySpan(String operationName, List<InMemoryReference> references,
                          String traceIdHex, String spanIdHex, @Nullable String parentSpanIdHex) {
        super(operationName, references, new DefaultInMemoryTraceState(traceIdHex, spanIdHex, parentSpanIdHex, false));
    }

    @Override
    public Map<String, Object> tags() {
        return Collections.emptyMap();
    }

    @Nullable
    @Override
    public List<InMemorySpanLog> logs() {
        return null;
    }

    @Override
    public void finish() {
    }

    @Override
    public void finish(long finishMicros) {
    }

    @Override
    public Span setTag(String key, String value) {
        return this;
    }

    @Override
    public Span setTag(String key, boolean value) {
        return this;
    }

    @Override
    public Span setTag(String key, Number value) {
        return this;
    }

    @Override
    public <T> Span setTag(Tag<T> tag, T value) {
        return this;
    }

    @Override
    public Span log(Map<String, ?> fields) {
        return this;
    }

    @Override
    public Span log(long epochMicros, Map<String, ?> fields) {
        return this;
    }

    @Override
    public Span log(String event) {
        return this;
    }

    @Override
    public Span log(long epochMicros, String event) {
        return this;
    }

    @Override
    public long startEpochMicros() {
        return -1;
    }
}
