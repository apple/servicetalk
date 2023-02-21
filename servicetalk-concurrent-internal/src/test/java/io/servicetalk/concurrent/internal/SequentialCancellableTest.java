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

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SequentialCancellableTest {

    @Test
    void testWithIgnoreCancel() {
        SequentialCancellable sc = new SequentialCancellable();
        sc.nextCancellable(IGNORE_CANCEL);
        Cancellable next = mock(Cancellable.class);
        sc.nextCancellable(next);
        verifyNoInteractions(next);

        assertThat(sc.isCancelled(), is(false));
        sc.cancel();
        assertThat(sc.isCancelled(), is(true));
        verify(next).cancel();
    }

    @Test
    void testOnlyLastIsCancelled() {
        SequentialCancellable sc = new SequentialCancellable();
        Cancellable first = mock(Cancellable.class);
        sc.nextCancellable(first);
        Cancellable second = mock(Cancellable.class);
        sc.nextCancellable(second);

        verifyNoInteractions(first);
        verifyNoInteractions(second);

        assertThat(sc.isCancelled(), is(false));
        sc.cancel();
        assertThat(sc.isCancelled(), is(true));
        verify(second).cancel();
        verifyNoInteractions(first);
    }

    @Test
    void cancelCurrent() {
        SequentialCancellable sc = new SequentialCancellable();
        Cancellable first = mock(Cancellable.class);
        sc.nextCancellable(first);
        sc.cancelCurrent();
        verify(first).cancel();
        assertThat(sc.isCancelled(), is(false));

        Cancellable second = mock(Cancellable.class);
        sc.nextCancellable(second);
        verifyNoInteractions(second);
        sc.cancelCurrent();
        verify(second).cancel();
        assertThat(sc.isCancelled(), is(false));

        sc.cancel();
        assertThat(sc.isCancelled(), is(true));
    }

    @Test
    void allNextCancelledAfterCancel() {
        SequentialCancellable sc = new SequentialCancellable();
        assertThat(sc.isCancelled(), is(false));
        sc.cancel();
        assertThat(sc.isCancelled(), is(true));

        Cancellable first = mock(Cancellable.class);
        sc.nextCancellable(first);
        verify(first).cancel();

        Cancellable second = mock(Cancellable.class);
        sc.nextCancellable(second);
        verify(second).cancel();

        assertThat(sc.isCancelled(), is(true));
    }
}
