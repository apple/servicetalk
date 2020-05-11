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

import io.servicetalk.concurrent.api.BufferStrategy.Accumulator;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.hamcrest.Matcher;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

public class PublisherBufferTest {
    private static final int EMPTY_ACCUMULATOR_VAL = -1;
    public static final int BUFFER_SIZE_HINT = 8;
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final TestPublisher<Integer> original = new TestPublisher<>();
    private final TestPublisher<SumAccumulator> boundaries = new TestPublisher<>();
    private final TestPublisherSubscriber<Integer> bufferSubscriber = new TestPublisherSubscriber<>();

    public PublisherBufferTest() {
        toSource(original.buffer(new BufferStrategy<Integer, SumAccumulator, Integer>() {
            @Override
            public Publisher<SumAccumulator> boundaries() {
                return boundaries;
            }

            @Override
            public int bufferSizeHint() {
                return BUFFER_SIZE_HINT;
            }
        })).subscribe(bufferSubscriber);
        assertThat("Subscription not received for buffer subscriber.", bufferSubscriber.subscriptionReceived(),
                is(true));
        bufferSubscriber.request(1); // get first boundary
        boundaries.onNext(new SumAccumulator(boundaries));
        assertThat("Unexpected boundary received.", bufferSubscriber.takeItems(), hasSize(0));
    }

    @Test
    public void emptyBuffer() {
        bufferSubscriber.request(1);
        verifyNoBuffersNoTerminal();

        emitBoundary();
        verifyEmptyBufferReceived();
        verifyNoBuffersNoTerminal();
    }

    @Test
    public void originalCompleteBeforeBufferRequested() {
        verifyNoBuffersNoTerminal();

        original.onComplete();
        bufferSubscriber.request(1);

        emitBoundary();
        verifyEmptyBufferReceived();
        verifyBufferSubCompleted();
    }

    @Test
    public void originalFailedBeforeBufferRequested() {
        verifyNoBuffersNoTerminal();

        original.onError(DELIBERATE_EXCEPTION);
        bufferSubscriber.request(1);

        emitBoundary();
        verifyEmptyBufferReceived();
        verifyBufferSubFailed(sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void bufferContainsItemsBeforeBoundaryClose() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        bufferSubscriber.request(1);

        emitBoundary();
        verifyBufferReceived(contains(1 + 2 + 3 + 4));
    }

    @Test
    public void itemCompletionCancelsBoundaries() {
        verifyNoBuffersNoTerminal();
        original.onComplete();
        emitBoundary();
        verifyBufferSubCompleted();
        verifyCancelled(boundaries);
    }

    @Test
    public void itemFailureCancelsBoundaries() {
        verifyNoBuffersNoTerminal();
        original.onError(DELIBERATE_EXCEPTION);
        emitBoundary();
        verifyBufferSubFailed(sameInstance(DELIBERATE_EXCEPTION));
        verifyCancelled(boundaries);
    }

    @Test
    public void multipleBoundaries() {
        verifyNoBuffersNoTerminal();
        bufferSubscriber.request(2);

        original.onNext(1, 2, 3, 4);

        emitBoundary();
        verifyBufferReceived(contains(1 + 2 + 3 + 4));

        original.onNext(1, 2, 3, 4);
        original.onComplete();

        emitBoundary();
    }

    @Test
    public void bufferSubCancel() {
        bufferSubscriber.cancel();
        verifyCancelled(original);
        verifyCancelled(boundaries);
    }

    @Test
    public void itemsBufferedTillBoundariesRequested() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        bufferSubscriber.request(1);
        emitBoundary();
        verifyBufferReceived(contains(1 + 2 + 3 + 4));
    }

    @Test
    public void itemsAndCompletionBufferedTillBoundariesRequested() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        original.onComplete();
        bufferSubscriber.request(1);
        emitBoundary();
        verifyBufferReceived(contains(1 + 2 + 3 + 4));
        verifyBufferSubCompleted();
        verifyCancelled(boundaries);
    }

    @Test
    public void itemsAndFailureBufferedTillBoundariesRequested() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        original.onError(DELIBERATE_EXCEPTION);
        bufferSubscriber.request(1);
        emitBoundary();
        verifyBufferReceived(contains(1 + 2 + 3 + 4));
        verifyBufferSubFailed(sameInstance(DELIBERATE_EXCEPTION));
        verifyCancelled(boundaries);
    }

    @Test
    public void boundariesCompletion() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        boundaries.onComplete();
        verifyBufferSubFailed(instanceOf(IllegalStateException.class));

        verifyCancelled(original);
    }

    @Test
    public void boundariesFailure() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        boundaries.onError(DELIBERATE_EXCEPTION);
        verifyBufferSubFailed(sameInstance(DELIBERATE_EXCEPTION));
        verifyCancelled(original);
    }

    @Ignore("Accumulator will not emit boundary ATM")
    @Test
    public void accumulateEmitsBoundary() {
        bufferSubscriber.request(1);
        boundaries.onNext(new SumAccumulator(boundaries, true));
        verifyEmptyBufferReceived();
        original.onNext(1);
        verifyBufferReceived(contains(1));
    }

    private void verifyCancelled(TestPublisher<?> source) {
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        assertThat("Original source not cancelled.", subscription.isCancelled(), is(true));
    }

    private void verifyBufferReceived(final Matcher<Iterable<? extends Integer>> bufferMatcher) {
        assertThat("Unexpected buffers received.", bufferSubscriber.takeItems(), bufferMatcher);
    }

    private void verifyEmptyBufferReceived() {
        assertThat("Unexpected buffers received.", bufferSubscriber.takeItems(), contains(EMPTY_ACCUMULATOR_VAL));
    }

    private void verifyNoBuffersNoTerminal() {
        assertThat("Unexpected buffers received.", bufferSubscriber.items(), hasSize(0));
        assertThat("Unexpected termination of buffer subscriber.", bufferSubscriber.isTerminated(), is(false));
    }

    private void emitBoundary() {
        boundaries.onNext(new SumAccumulator(boundaries));
    }

    private void verifyBufferSubCompleted() {
        TerminalNotification term = bufferSubscriber.takeTerminal();
        assertThat("Unexpected termination of buffer subscriber.", term, is(notNullValue()));
        assertThat("Unexpected termination of buffer subscriber.", term.cause(), is(nullValue()));
    }

    private void verifyBufferSubFailed(final Matcher<Throwable> causeMatcher) {
        TerminalNotification term = bufferSubscriber.takeTerminal();
        assertThat("Unexpected termination of buffer subscriber.", term, is(notNullValue()));
        assertThat("Unexpected termination of buffer subscriber.", term.cause(), causeMatcher);
    }

    private static final class SumAccumulator implements Accumulator<Integer, Integer> {
        private final TestPublisher<SumAccumulator> boundaries;
        private int sum = EMPTY_ACCUMULATOR_VAL;
        private final boolean emitBoundaryOnAccumulate;

        SumAccumulator(final TestPublisher<SumAccumulator> boundaries) {
            this(boundaries, false);
        }

        SumAccumulator(final TestPublisher<SumAccumulator> boundaries, final boolean emitBoundaryOnAccumulate) {
            this.boundaries = boundaries;
            this.emitBoundaryOnAccumulate = emitBoundaryOnAccumulate;
        }

        @Override
        public void accumulate(@Nullable final Integer integer) {
            if (integer == null) {
                return;
            }
            if (sum == EMPTY_ACCUMULATOR_VAL) {
                sum = 0;
            }
            sum += integer;
            if (emitBoundaryOnAccumulate) {
                boundaries.onNext(new SumAccumulator(boundaries));
            }
        }

        @Override
        public Integer finish() {
            return sum;
        }
    }
}
