/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.opentelemetry.asynccontext;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.CapturedContext;
import io.servicetalk.concurrent.api.CapturedContextProvider;
import io.servicetalk.context.api.ContextMap;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtelCapturedContextProviderTest {
    private static final ContextKey<String> STRING_CONTEXT_KEY = ContextKey.named("string-key");

    private final CapturedContextProvider provider = new OtelCapturedContextProvider();
    private final ContextMap contextMap = mock(ContextMap.class);
    private final io.servicetalk.concurrent.api.Scope stScope = mock(io.servicetalk.concurrent.api.Scope.class);
    private final CapturedContext underlying = mock(CapturedContext.class);

    private void setup() {
        when(underlying.captured()).thenReturn(contextMap);
        when(underlying.attachContext()).thenReturn(stScope);
    }

    @Test
    void testContextIsCaptured() {
        setup();
        assertNotEquals(contextMap, AsyncContext.context());

        Context fooContext = Context.root().with(STRING_CONTEXT_KEY, "foo");
        CapturedContext captured1;
        try (Scope otelOuterScope = fooContext.makeCurrent()) {
            assertEquals(fooContext, Context.current());
            captured1 = provider.captureContext(underlying);
        }
        assertEquals(contextMap, captured1.captured());

        verify(underlying, never()).attachContext();
        try (io.servicetalk.concurrent.api.Scope attachedStScope = captured1.attachContext()) {
            assertEquals(fooContext, Context.current());
            verify(underlying).attachContext();
        }
        verify(stScope).close();
        assertEquals(Context.root(), Context.current());
    }

    @Test
    void testContextCaptureWhenNested() {
        setup();
        List<CapturedContext> results = recurseCapture(10);
        assertEquals(10, results.size());
        int i = 1;
        for (CapturedContext capturedContext : results) {
            try (io.servicetalk.concurrent.api.Scope scope = capturedContext.attachContext()) {
                assertEquals("depth-" + i, Context.current().get(STRING_CONTEXT_KEY));
            }
            ++i;
        }
        verify(stScope, times(10)).close();
    }

    @Test
    void testContextCaptureWhenRestoreNested() {
        setup();
        List<CapturedContext> results = recurseCapture(10);
        assertEquals(10, results.size());
        recurseRestore(results, 0);
        verify(stScope, times(10)).close();
    }

    private List<CapturedContext> recurseCapture(int depth) {
        try (Scope scope = Context.current().with(STRING_CONTEXT_KEY, "depth-" + depth).makeCurrent()) {
            CapturedContext captured = provider.captureContext(underlying);
            List<CapturedContext> result = depth == 1 ? new ArrayList<>() : recurseCapture(depth - 1);
            result.add(captured);
            return result;
        }
    }

    private void recurseRestore(List<CapturedContext> capturedContexts, int i) {
        if (i == capturedContexts.size()) {
            return;
        }
        try (io.servicetalk.concurrent.api.Scope scope = capturedContexts.get(i).attachContext()) {
            assertEquals("depth-" + (i + 1), Context.current().get(STRING_CONTEXT_KEY));
            recurseRestore(capturedContexts, i + 1);
            assertEquals("depth-" + (i + 1), Context.current().get(STRING_CONTEXT_KEY));
        }
    }
}
