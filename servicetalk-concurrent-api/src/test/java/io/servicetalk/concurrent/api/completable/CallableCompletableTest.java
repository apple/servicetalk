/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.Completable;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class CallableCompletableTest {

    @ParameterizedTest(name = "{displayName} [{index}] cancel={0}")
    @ValueSource(booleans = {false, true})
    void fromCallableOnSubscribeThrows(boolean cancel) {
        verifyOnSubscribeThrows(cancel, invoked -> Completable.fromCallable(() -> {
            invoked.set(true);
            return null;
        }));
    }

    @ParameterizedTest(name = "{displayName} [{index}] cancel={0}")
    @ValueSource(booleans = {false, true})
    void fromRunnableOnSubscribeThrows(boolean cancel) {
        verifyOnSubscribeThrows(cancel, invoked -> Completable.fromRunnable(() -> invoked.set(true)));
    }

    private static void verifyOnSubscribeThrows(boolean cancel,
                                                Function<AtomicBoolean, Completable> sourceFactory) {
        final AtomicBoolean invoked = new AtomicBoolean();
        final Completable source = sourceFactory.apply(invoked);

        final CompletableSource.Subscriber subscriber = mock(CompletableSource.Subscriber.class);
        // Cancelling from onSubscribe is legal. Throwing afterwards violates the Reactive Streams spec, but the
        // interrupt delivered by cancel() must still not be left behind on this (typically pooled) thread.
        doAnswer(invocation -> {
            if (cancel) {
                invocation.<Cancellable>getArgument(0).cancel();
            }
            throw DELIBERATE_EXCEPTION;
        }).when(subscriber).onSubscribe(any());

        try {
            toSource(source).subscribe(subscriber);

            verify(subscriber).onSubscribe(any());
            verify(subscriber).onError(DELIBERATE_EXCEPTION);
            verifyNoMoreInteractions(subscriber);
            assertThat("source work was invoked even though onSubscribe threw", invoked.get(), is(false));
            assertThat("a throwing onSubscribe left an interrupt behind", Thread.interrupted(), is(false));
        } finally {
            // A leaked interrupt would otherwise bleed into the next test sharing this thread.
            Thread.interrupted();
        }
    }
}
