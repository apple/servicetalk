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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Cancellable;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

class SequentialCancellableTest {

    @Test
    void testWithIgnoreCancel() {
        SequentialCancellable sc = new SequentialCancellable();
        sc.nextCancellable(Cancellable.IGNORE_CANCEL);
        Cancellable next = mock(Cancellable.class);
        sc.nextCancellable(next);
        verifyZeroInteractions(next);

        sc.cancel();
        verify(next).cancel();
    }

    @Test
    void testWithCancel() {
        SequentialCancellable sc = new SequentialCancellable();
        Cancellable first = mock(Cancellable.class);
        sc.nextCancellable(first);
        Cancellable second = mock(Cancellable.class);
        sc.nextCancellable(second);

        verifyZeroInteractions(first);
        verifyZeroInteractions(second);

        sc.cancel();
        verify(second).cancel();
        verifyZeroInteractions(first);
    }
}
