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
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanBuilder;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanContext;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tag;
import io.opentracing.tag.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import static io.opentracing.References.CHILD_OF;
import static io.opentracing.References.FOLLOWS_FROM;
import static java.util.Objects.requireNonNull;

abstract class AbstractInMemorySpanBuilder implements InMemorySpanBuilder {
    private static final Logger logger = LoggerFactory.getLogger(AbstractInMemorySpanBuilder.class);

    private final String operationName;
    private final List<InMemoryReference> references = new ArrayList<>(2);
    private final Map<String, Object> tags = new HashMap<>(4);
    private final int maxTagSize;
    private long startTimestampMicros = -1;
    private boolean ignoreActiveSpan;

    protected AbstractInMemorySpanBuilder(String operationName, int maxTagSize) {
        this.operationName = requireNonNull(operationName);
        this.maxTagSize = maxTagSize;
    }

    @Override
    public final InMemorySpanBuilder asChildOf(SpanContext parent) {
        addReference(CHILD_OF, parent);
        return this;
    }

    @Override
    public final InMemorySpanBuilder asChildOf(Span parent) {
        addReference(CHILD_OF, parent.context());
        return this;
    }

    @Override
    public final InMemorySpanBuilder addReference(String referenceType, InMemorySpanContext referencedContext) {
        references.add(new DefaultInMemoryReference(referenceType, referencedContext));
        return this;
    }

    @Override
    public final InMemorySpanBuilder withTag(String key, String value) {
        putTag(key, value);
        return this;
    }

    @Override
    public final InMemorySpanBuilder withTag(String key, boolean value) {
        putTag(key, value);
        return this;
    }

    @Override
    public final InMemorySpanBuilder withTag(String key, Number value) {
        putTag(key, value);
        return this;
    }

    @Override
    public <T> Tracer.SpanBuilder withTag(Tag<T> tag, T value) {
        putTag(tag.getKey(), value);
        return this;
    }

    @Override
    public final InMemorySpanBuilder withStartTimestamp(long microseconds) {
        startTimestampMicros = microseconds;
        return this;
    }

    @Override
    public final InMemorySpan start() {
        if (startTimestampMicros == -1) {
            startTimestampMicros = System.currentTimeMillis() * 1000;
        }
        return createSpan((String) tags.get(Tags.SPAN_KIND.getKey()), operationName, references, tags, maxTagSize,
                ignoreActiveSpan, startTimestampMicros);
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    @Nullable
    protected final InMemorySpanContext parent() {
        // Use old-style for loop to save a bit of garbage creation
        for (int i = 0; i < references.size(); i++) {
            final InMemoryReference reference = references.get(i);
            if (CHILD_OF.equals(reference.type()) || FOLLOWS_FROM.equals(reference.type())) {
                return reference.referredTo();
            }
        }
        return null;
    }

    private void putTag(String key, Object value) {
        if (tags.size() < maxTagSize) {
            tags.put(key, value);
        } else {
            logger.warn("Tag {} ignored since maxTagSize={} is reached", key, maxTagSize);
        }
    }

    @Override
    public InMemorySpanBuilder ignoreActiveSpan() {
        ignoreActiveSpan = true;
        return this;
    }

    /**
     * Create a span with current builder settings.
     *
     * @param kind Value of the {@code span.kind} tag if specified, could be null.
     * @param operationName the operation name.
     * @param references references for the span.
     * @param tags tags for the span.
     * @param maxTagSize max tag size.
     * @param ignoreActiveSpan the value set by {@link #ignoreActiveSpan()}.
     * @param startTimestampMicros the span start time in micro seconds.
     * @return newly created span
     */
    protected abstract InMemorySpan createSpan(@Nullable String kind, String operationName,
                                               List<InMemoryReference> references, Map<String, Object> tags,
                                               int maxTagSize, boolean ignoreActiveSpan, long startTimestampMicros);
}
