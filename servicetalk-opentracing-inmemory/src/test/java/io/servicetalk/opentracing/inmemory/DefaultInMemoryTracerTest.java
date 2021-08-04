/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.opentracing.inmemory.api.InMemoryScopeManager;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanBuilder;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanContext;
import io.servicetalk.opentracing.inmemory.api.InMemoryTracer;

import org.junit.jupiter.api.Test;

import static io.opentracing.References.CHILD_OF;
import static io.opentracing.References.FOLLOWS_FROM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultInMemoryTracerTest {
    @Test
    void childOfReferenceRespected() {
        verifyParentReference("childOfReferenceRespected", true);
    }

    @Test
    void followsFromReferenceRespected() {
        verifyParentReference("followsFromReferenceRespected", false);
    }

    private static void verifyParentReference(final String parentTraceIdHex, boolean childOf) {
        InMemoryScopeManager mockScopeManager = mock(InMemoryScopeManager.class);
        InMemorySpanContext mockParentContext = mock(InMemorySpanContext.class);
        when(mockParentContext.toTraceId()).thenReturn(parentTraceIdHex);
        InMemoryTracer tracer = new DefaultInMemoryTracer.Builder(mockScopeManager).build();
        InMemorySpanBuilder spanBuilder = tracer.buildSpan("foo");
        spanBuilder.addReference(childOf ? CHILD_OF : FOLLOWS_FROM, mockParentContext);
        InMemorySpan span = spanBuilder.start();
        assertEquals(parentTraceIdHex, span.context().toTraceId());
    }
}
