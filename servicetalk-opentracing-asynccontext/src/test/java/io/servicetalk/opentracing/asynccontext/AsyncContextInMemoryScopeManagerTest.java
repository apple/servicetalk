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

import io.servicetalk.opentracing.inmemory.api.InMemorySpan;

import io.opentracing.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.servicetalk.opentracing.asynccontext.AsyncContextInMemoryScopeManager.SCOPE_MANAGER;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.MockitoAnnotations.initMocks;

@ExtendWith(MockitoExtension.class)
class AsyncContextInMemoryScopeManagerTest {
    @Mock
    private InMemorySpan mockSpan;
    @Mock
    private InMemorySpan mockSpan2;

    private AsyncContextInMemoryScopeManager scopeManager = (AsyncContextInMemoryScopeManager) SCOPE_MANAGER;

    @BeforeEach
    public void setup() {
        initMocks(this);
        AsyncContextInMemoryScopeManager.AsyncContextInMemoryScope previousScope = scopeManager.active();
        if (previousScope != null) {
            previousScope.close();
        }
    }

    @Test
    void closedScopeNotReturnedByActive() {
        Scope scope = SCOPE_MANAGER.activate(mockSpan);
        assertSame(scope, scopeManager.active());
        scope.close();
        assertNull(scopeManager.active());
    }

    @Test
    void previousScopeRestoredAfterCurrentScopeClosed() {
        Scope previousScope = SCOPE_MANAGER.activate(mockSpan2);
        Scope scope = SCOPE_MANAGER.activate(mockSpan);
        assertSame(scope, scopeManager.active());
        scope.close();
        assertSame(previousScope, scopeManager.active());
    }
}
