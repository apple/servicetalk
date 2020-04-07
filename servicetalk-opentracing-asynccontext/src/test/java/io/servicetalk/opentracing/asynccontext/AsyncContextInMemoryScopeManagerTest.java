/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentracing.asynccontext;

import io.servicetalk.opentracing.inmemory.api.InMemoryScope;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static io.servicetalk.opentracing.asynccontext.AsyncContextInMemoryScopeManager.SCOPE_MANAGER;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.MockitoAnnotations.initMocks;

public class AsyncContextInMemoryScopeManagerTest {
    @Mock
    private InMemorySpan mockSpan;
    @Mock
    private InMemorySpan mockSpan2;

    @Before
    public void setup() {
        initMocks(this);
        InMemoryScope previousScope = SCOPE_MANAGER.active();
        if (previousScope != null) {
            previousScope.close();
        }
    }

    @Test
    public void closedScopeNotReturnedByActive() {
        InMemoryScope scope = SCOPE_MANAGER.activate(mockSpan, true);
        assertSame(scope, SCOPE_MANAGER.active());
        assertSame(mockSpan, scope.span());
        scope.close();
        assertNull(SCOPE_MANAGER.active());
    }

    @Test
    public void previousScopeRestoredAfterCurrentScopeClosed() {
        InMemoryScope previousScope = SCOPE_MANAGER.activate(mockSpan2, true);
        InMemoryScope scope = SCOPE_MANAGER.activate(mockSpan, true);
        assertSame(scope, SCOPE_MANAGER.active());
        assertSame(mockSpan, scope.span());
        scope.close();
        assertSame(previousScope, SCOPE_MANAGER.active());
    }
}
