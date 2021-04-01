/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

public class PublisherBufferTest {
    private static final int EMPTY_ACCUMULATOR_VAL = -1;
    public static final int BUFFER_SIZE_HINT = 8;
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
        bufferSubscriber.awaitSubscription().request(1); // get first boundary
        boundaries.onNext(new SumAccumulator(boundaries));
        assertThat(bufferSubscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    public void invalidBufferSizeHint() {
        TestPublisherSubscriber<Integer> bufferSubscriber = new TestPublisherSubscriber<>();
        toSource(Publisher.<Integer>empty()
                .buffer(new BufferStrategy<Integer, Accumulator<Integer, Integer>, Integer>() {
                    @Override
                    public Publisher<Accumulator<Integer, Integer>> boundaries() {
                        return never();
                    }

                    @Override
                    public int bufferSizeHint() {
                        return 0;
                    }
                })).subscribe(bufferSubscriber);
        bufferSubscriber.awaitSubscription();
        assertThat(bufferSubscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    public void emptyBuffer() {
        bufferSubscriber.awaitSubscription().request(1);
        verifyNoBuffersNoTerminal();

        emitBoundary();
        verifyEmptyBufferReceived();
        verifyNoBuffersNoTerminal();
    }

    @Test
    public void originalCompleteBeforeBufferRequested() {
        verifyNoBuffersNoTerminal();

        original.onComplete();
        bufferSubscriber.awaitSubscription().request(1);

        emitBoundary();
        verifyEmptyBufferReceived();
        verifyBufferSubCompleted();
    }

    @Test
    public void originalFailedBeforeBufferRequested() {
        verifyNoBuffersNoTerminal();

        original.onError(DELIBERATE_EXCEPTION);
        bufferSubscriber.awaitSubscription().request(1);

        emitBoundary();
        verifyEmptyBufferReceived();
        verifyBufferSubFailed(sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void bufferContainsItemsBeforeBoundaryClose() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        bufferSubscriber.awaitSubscription().request(1);

        emitBoundary();
        assertThat(bufferSubscriber.takeOnNext(), is(1 + 2 + 3 + 4));
    }

    @Test
    public void itemCompletionCancelsBoundaries() {
        verifyNoBuffersNoTerminal();
        original.onComplete();
        emitBoundary();
        assertThat(bufferSubscriber.takeOnNext(), is(-1));
        verifyBufferSubCompleted();
        verifyCancelled(boundaries);
    }

    @Test
    public void itemFailureCancelsBoundaries() {
        verifyNoBuffersNoTerminal();
        original.onError(DELIBERATE_EXCEPTION);
        emitBoundary();
        assertThat(bufferSubscriber.takeOnNext(), is(-1));
        verifyBufferSubFailed(sameInstance(DELIBERATE_EXCEPTION));
        verifyCancelled(boundaries);
    }

    @Test
    public void multipleBoundaries() {
        verifyNoBuffersNoTerminal();
        bufferSubscriber.awaitSubscription().request(2);

        original.onNext(1, 2, 3, 4);

        emitBoundary();
        assertThat(bufferSubscriber.takeOnNext(), is(1 + 2 + 3 + 4));

        original.onNext(1, 2, 3, 4);
        original.onComplete();

        emitBoundary();
    }

    @Test
    public void bufferSubCancel() {
        bufferSubscriber.awaitSubscription().cancel();
        verifyCancelled(original);
        verifyCancelled(boundaries);
    }

    @Test
    public void itemsBufferedTillBoundariesRequested() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        bufferSubscriber.awaitSubscription().request(1);
        emitBoundary();
        assertThat(bufferSubscriber.takeOnNext(), is(1 + 2 + 3 + 4));
    }

    @Test
    public void itemsAndCompletionBufferedTillBoundariesRequested() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        original.onComplete();
        bufferSubscriber.awaitSubscription().request(1);
        emitBoundary();
        assertThat(bufferSubscriber.takeOnNext(), is(1 + 2 + 3 + 4));
        verifyBufferSubCompleted();
        verifyCancelled(boundaries);
    }

    @Test
    public void itemsAndFailureBufferedTillBoundariesRequested() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        original.onError(DELIBERATE_EXCEPTION);
        bufferSubscriber.awaitSubscription().request(1);
        emitBoundary();
        assertThat(bufferSubscriber.takeOnNext(), is(1 + 2 + 3 + 4));
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

    @Disabled("Accumulator will not emit boundary ATM")
    @Test
    public void accumulateEmitsBoundary() {
        bufferSubscriber.awaitSubscription().request(1);
        boundaries.onNext(new SumAccumulator(boundaries, true));
        verifyEmptyBufferReceived();
        original.onNext(1);
        assertThat(bufferSubscriber.takeOnNext(), is(1));
    }

    private void verifyCancelled(TestPublisher<?> source) {
        TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        assertThat("Original source not cancelled.", subscription.isCancelled(), is(true));
    }

    private void verifyEmptyBufferReceived() {
        assertThat("Unexpected buffers received.", bufferSubscriber.takeOnNext(), is(EMPTY_ACCUMULATOR_VAL));
    }

    private void verifyNoBuffersNoTerminal() {
        assertThat(bufferSubscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(bufferSubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    private void emitBoundary() {
        boundaries.onNext(new SumAccumulator(boundaries));
    }

    private void verifyBufferSubCompleted() {
        bufferSubscriber.awaitOnComplete();
    }

    private void verifyBufferSubFailed(final Matcher<Throwable> causeMatcher) {
        assertThat(bufferSubscriber.awaitOnError(), causeMatcher);
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
