/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.ContextStorageProvider;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class ServiceTalkContextStoreProviderTest {

    @Test
    void testScopeIsStored() {
        ContextStorageProvider contextStoreProvider = new ServiceTalkContextStoreProvider();
        ContextStorage contextStorage = contextStoreProvider.get();
        Context mockSpan = mock(Context.class);
        Scope attached = contextStorage.attach(mockSpan);
        assertNotNull(attached);
        assertEquals(mockSpan, contextStorage.current());
        attached.close();
    }

    @Test
    void testScopeIsStoredTwice() {
        ContextStorageProvider contextStoreProvider = new ServiceTalkContextStoreProvider();
        ContextStorage contextStorage = contextStoreProvider.get();
        Context mockSpan = mock(Context.class);
        Scope attached = contextStorage.attach(mockSpan);
        assertNotNull(attached);
        assertEquals(mockSpan, contextStorage.current());
        assertNotEquals(Scope.noop(), attached);
        Scope attached2 = contextStorage.attach(mockSpan);
        assertEquals(Scope.noop(), attached2);
        attached2.close();
    }
}
