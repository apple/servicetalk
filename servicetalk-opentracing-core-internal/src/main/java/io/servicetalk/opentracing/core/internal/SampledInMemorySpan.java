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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import static java.lang.System.nanoTime;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

final class SampledInMemorySpan extends AbstractInMemorySpan {
    private static final Logger logger = LoggerFactory.getLogger(SampledInMemorySpan.class);

    private final Map<String, Object> tags;
    private final int maxTagSize;
    private final long startEpochMicros;
    private final long startSystemNanos;
    private final InMemorySpanEventListener listeners;
    @Nullable
    private final List<Log> logs;
    private boolean started;

    SampledInMemorySpan(String operationName, List<InMemoryReference> references,
                        String traceIdHex, String spanIdHex, @Nullable String parentSpanIdHex,
                        @Nullable Map<String, Object> tags, int maxTagSize, long startEpochMicros,
                        InMemorySpanEventListener listeners, boolean persistLogs) {
        super(operationName, references, new DefaultInMemoryTraceState(traceIdHex, spanIdHex, parentSpanIdHex, true));
        this.tags = tags == null ? new HashMap<>(4) : new HashMap<>(tags); // size is guesstimate
        this.maxTagSize = maxTagSize;
        this.startEpochMicros = startEpochMicros;
        startSystemNanos = nanoTime();
        this.listeners = requireNonNull(listeners);
        logs = persistLogs ? new ArrayList<>(4) : null; // size is guesstimate
    }

    @Override
    public Map<String, Object> tags() {
        return unmodifiableMap(tags);
    }

    @Nullable
    @Override
    public List<Log> logs() {
        return logs == null ? null : unmodifiableList(logs);
    }

    @Override
    public Span setTag(String key, String value) {
        putTag(key, value);
        return this;
    }

    @Override
    public Span setTag(String key, boolean value) {
        putTag(key, value);
        return this;
    }

    @Override
    public Span setTag(String key, Number value) {
        putTag(key, value);
        return this;
    }

    @Override
    public Span log(Map<String, ?> fields) {
        return log(safeEpochMicros(), fields);
    }

    @Override
    public Span log(long epochMicros, Map<String, ?> fields) {
        checkStarted();
        listeners.onEventLogged(this, epochMicros, fields);
        if (logs != null) {
            logs.add(new Log(epochMicros, "key-value-event", fields));
        }
        return this;
    }

    @Override
    public Span log(String event) {
        return log(safeEpochMicros(), event);
    }

    @Override
    public Span log(long epochMicros, String event) {
        checkStarted();
        listeners.onEventLogged(this, epochMicros, event);
        if (logs != null) {
            logs.add(new Log(epochMicros, event, null));
        }
        return this;
    }

    @Override
    public long startEpochMicros() {
        return startEpochMicros;
    }

    @Override
    public void finish() {
        checkStarted();
        notifyFinish((nanoTime() - startSystemNanos) / 1000);
    }

    @Override
    public void finish(long finishEpochMicros) {
        checkStarted();
        notifyFinish(finishEpochMicros - startEpochMicros);
    }

    void start() {
        if (started) {
            throw new IllegalStateException("Span has already started " + this);
        }
        started = true;
        listeners.onSpanStarted(this);
    }

    private void checkStarted() {
        if (!started) {
            throw new IllegalStateException("Span has not yet started " + this);
        }
    }

    private void putTag(String key, Object value) {
        if (tags.size() < maxTagSize) {
            tags.put(key, value);
        } else {
            logger.warn("Tag {} ignored since maxTagSize={} is reached", key, maxTagSize);
        }
    }

    private void notifyFinish(long durationMicros) {
        listeners.onSpanFinished(this, durationMicros);
    }

    private long safeEpochMicros() {
        return startEpochMicros + (nanoTime() - startSystemNanos) / 1000;
    }
}
