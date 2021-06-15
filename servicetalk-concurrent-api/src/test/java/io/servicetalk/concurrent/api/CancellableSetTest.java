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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

class CancellableSetTest extends AbstractCompositeCancellableTest<CancellableSet> {
    @Override
    protected CancellableSet newCompositeCancellable() {
        return new CancellableSet();
    }

    @Override
    protected boolean add(final CancellableSet composite, final Cancellable c) {
        return composite.add(c);
    }

    @Test
    void testAddAndRemove() {
        CancellableSet c = newCompositeCancellable();
        Cancellable cancellable = mock(Cancellable.class);
        add(c, cancellable);
        c.remove(cancellable);
        c.cancel();
        verifyZeroInteractions(cancellable);
    }

    @Test
    void duplicateAddDoesNotCancel() {
        CancellableSet c = newCompositeCancellable();
        Cancellable cancellable = mock(Cancellable.class);
        int addCount = 0;
        if (add(c, cancellable)) {
            ++addCount;
        }
        if (add(c, cancellable)) {
            ++addCount;
        }
        verify(cancellable, never()).cancel();

        for (int i = 0; i < addCount; ++i) {
            assertTrue(c.remove(cancellable));
        }
        c.cancel();
        verify(cancellable, never()).cancel();
    }
}
