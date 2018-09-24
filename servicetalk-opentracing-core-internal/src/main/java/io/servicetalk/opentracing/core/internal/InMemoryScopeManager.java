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

import io.opentracing.ScopeManager;
import io.opentracing.Span;

import javax.annotation.Nullable;

/**
 * A {@link ScopeManager} that works with {@link InMemoryScope} instances.
 */
public interface InMemoryScopeManager extends ScopeManager {
    /**
     * {@inheritDoc}
     * @throws ClassCastException if {@code span} is not of type {@link InMemorySpan}.
     */
    @Override
    default InMemoryScope activate(Span span, boolean finishSpanOnClose) {
        return activate((InMemorySpan) span, finishSpanOnClose);
    }

    /**
     * Same as {@link #activate(Span, boolean)} but the span must be of type {@link InMemorySpan}.
     * @param span the {@link InMemorySpan} to active.
     * @param finishSpanOnClose {@code true} to call {@link InMemorySpan#finish()} when {@link InMemoryScope#close()} is
     * called.
     * @return A {@link InMemoryScope} that has been activated.
     */
    InMemoryScope activate(InMemorySpan span, boolean finishSpanOnClose);

    @Nullable
    @Override
    InMemoryScope active();
}
