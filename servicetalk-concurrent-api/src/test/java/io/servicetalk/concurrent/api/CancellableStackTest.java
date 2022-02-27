/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CancellableStackTest extends AbstractCompositeCancellableTest<CancellableStack> {
    @Override
    protected CancellableStack newCompositeCancellable(int maxCancellables) {
        return new CancellableStack(maxCancellables);
    }

    @Override
    protected boolean add(final CancellableStack composite, final Cancellable c) {
        return composite.add(c);
    }

    @Test
    void maxCancellableCountExceeded() {
        CancellableStack stack = newCompositeCancellable(1);
        Cancellable cancellable1 = mock(Cancellable.class);
        Cancellable cancellable2 = mock(Cancellable.class);
        stack.add(cancellable1);
        assertThrows(IllegalStateException.class, () -> stack.add(cancellable2));
        verify(cancellable1, never()).cancel();
        verify(cancellable2, never()).cancel();
        stack.cancel();
        verify(cancellable1).cancel();
        verify(cancellable2, never()).cancel();
    }
}
