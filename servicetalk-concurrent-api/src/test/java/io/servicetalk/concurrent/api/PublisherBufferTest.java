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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.BufferStrategy.Accumulator;
import io.servicetalk.concurrent.internal.DelayedSubscription;
import io.servicetalk.concurrent.internal.TerminalNotification;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.lang.Long.MAX_VALUE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class PublisherBufferTest {

    private static final int EMPTY_ACCUMULATOR_VAL = -1;
    static final int BUFFER_SIZE_HINT = 8;
    private final TestSubscription tSubscription = new TestSubscription("tSubscription");
    private final TestPublisher<Integer> original = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe()
            .build(sub -> {
                sub.onSubscribe(tSubscription);
                return sub;
            });
    private final TestSubscription bSubscription = new TestSubscription("bSubscription");
    private final TestPublisher<Accumulator<Integer, Integer>> boundaries =
            new TestPublisher.Builder<Accumulator<Integer, Integer>>().disableAutoOnSubscribe().build(sub -> {
                sub.onSubscribe(bSubscription);
                return sub;
            });
    private final TestPublisherSubscriber<Integer> bufferSubscriber = new TestPublisherSubscriber<>();

    PublisherBufferTest() throws Exception {
        toSource(original.buffer(new TestBufferStrategy(boundaries, BUFFER_SIZE_HINT))).subscribe(bufferSubscriber);
        bufferSubscriber.awaitSubscription().request(1); // get first boundary
        bSubscription.awaitRequestN(1);
        emitBoundary();
        assertThat(bufferSubscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    void invalidBufferSizeHint() {
        TestPublisherSubscriber<Integer> bufferSubscriber = new TestPublisherSubscriber<>();
        toSource(Publisher.<Integer>empty()
                .buffer(new TestBufferStrategy(never(), 0))).subscribe(bufferSubscriber);
        bufferSubscriber.awaitSubscription();
        assertThat(bufferSubscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void noBoundaries() {
        TestPublisher<Integer> publisher = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build();
        TestSubscription subscription = new TestSubscription();
        TestPublisherSubscriber<Integer> bufferSubscriber = new TestPublisherSubscriber<>();
        toSource(publisher.buffer(new TestBufferStrategy(never(), BUFFER_SIZE_HINT))).subscribe(bufferSubscriber);
        assertThat(publisher.isSubscribed(), is(true));
        publisher.onSubscribe(subscription);
        bufferSubscriber.awaitSubscription().request(MAX_VALUE);
        assertThat(subscription.requested(), is(0L));
        assertThat(subscription.requestedEquals(0L), is(false));
    }

    @Test
    void subscriberThrowsFromOnNext() {
        TestPublisher<Integer> tPublisher = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build();
        TestPublisher<Accumulator<Integer, Integer>> bPublisher =
                new TestPublisher.Builder<Accumulator<Integer, Integer>>().disableAutoOnSubscribe().build();
        TestSubscription tSubscription = new TestSubscription();
        TestSubscription bSubscription = new TestSubscription();
        AtomicReference<TerminalNotification> terminal = new AtomicReference<>();
        AtomicInteger onNextCounter = new AtomicInteger();
        toSource(tPublisher.buffer(new TestBufferStrategy(bPublisher, BUFFER_SIZE_HINT)))
                .subscribe(new Subscriber<Integer>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(MAX_VALUE);
                    }

                    @Override
                    public void onNext(@Nullable Integer integer) {
                        onNextCounter.incrementAndGet();
                        throw DELIBERATE_EXCEPTION;
                    }

                    @Override
                    public void onError(Throwable t) {
                        terminal.set(error(t));
                    }

                    @Override
                    public void onComplete() {
                        terminal.set(complete());
                    }
                });
        assertThat(tPublisher.isSubscribed(), is(true));
        tPublisher.onSubscribe(tSubscription);
        assertThat(bPublisher.isSubscribed(), is(true));
        bPublisher.onSubscribe(bSubscription);

        assertThat(tSubscription.requested(), is(0L));
        assertThat(bSubscription.requested(), is(MAX_VALUE));

        bPublisher.onNext(new SumAccumulator());
        assertThat((int) tSubscription.requested(), is(BUFFER_SIZE_HINT));
        tPublisher.onNext(1);
        bPublisher.onNext(new SumAccumulator());

        assertThat(onNextCounter.get(), is(1));
        assertThat(terminal.get().cause(), is(DELIBERATE_EXCEPTION));
        verifyCancelled(tSubscription);
        // Verify that further items are ignored
        terminal.set(null);
        tPublisher.onNext(2);
        bPublisher.onNext(new SumAccumulator());
        tPublisher.onComplete();
        assertThat(onNextCounter.get(), is(1));
        assertThat(terminal.get(), is(nullValue()));
    }

    @Test
    void subscriberThrowsFromOnNextBeforeTermination() {
        TestPublisher<Integer> tPublisher = new TestPublisher<>();
        TestPublisher<Accumulator<Integer, Integer>> bPublisher = new TestPublisher<>();
        TestSubscription bSubscription = new TestSubscription();
        AtomicReference<TerminalNotification> terminal = new AtomicReference<>();
        AtomicInteger onNextCounter = new AtomicInteger();
        toSource(tPublisher.buffer(new TestBufferStrategy(bPublisher, BUFFER_SIZE_HINT)))
                .subscribe(new Subscriber<Integer>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(MAX_VALUE);
                    }

                    @Override
                    public void onNext(@Nullable Integer integer) {
                        onNextCounter.incrementAndGet();
                        throw DELIBERATE_EXCEPTION;
                    }

                    @Override
                    public void onError(Throwable t) {
                        terminal.set(error(t));
                    }

                    @Override
                    public void onComplete() {
                        terminal.set(complete());
                    }
                });
        assertThat(bPublisher.isSubscribed(), is(true));
        bPublisher.onSubscribe(bSubscription);
        assertThat(bSubscription.requested(), is(MAX_VALUE));

        bPublisher.onNext(new SumAccumulator());
        tPublisher.onNext(1);
        tPublisher.onComplete();

        assertThat(onNextCounter.get(), is(1));
        assertThat(terminal.get().cause(), is(DELIBERATE_EXCEPTION));
        verifyCancelled(bSubscription);
        // Verify that further items are ignored
        terminal.set(null);
        tPublisher.onNext(2);
        bPublisher.onNext(new SumAccumulator());
        tPublisher.onComplete();
        assertThat(onNextCounter.get(), is(1));
        assertThat(terminal.get(), is(nullValue()));
    }

    @Test
    void emptyBuffer() {
        bufferSubscriber.awaitSubscription().request(1);
        verifyNoBuffersNoTerminal();

        emitBoundary();
        verifyEmptyBufferReceived();
        verifyNoBuffersNoTerminal();
    }

    @Test
    void originalCompleteBeforeBufferRequested() {
        verifyNoBuffersNoTerminal();

        original.onComplete();
        bufferSubscriber.awaitSubscription().request(1);

        emitBoundary();
        assertThat(bufferSubscriber.pollAllOnNext(), empty());
        verifyBufferSubCompleted();
    }

    @Test
    void originalFailedBeforeBufferRequested() {
        verifyNoBuffersNoTerminal();

        original.onError(DELIBERATE_EXCEPTION);
        bufferSubscriber.awaitSubscription().request(1);

        emitBoundary();
        assertThat(bufferSubscriber.pollAllOnNext(), empty());
        verifyBufferSubFailed(sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void bufferContainsItemsBeforeBoundaryClose() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        bufferSubscriber.awaitSubscription().request(1);

        emitBoundary();
        assertThat(bufferSubscriber.takeOnNext(), is(1 + 2 + 3 + 4));
    }

    @Test
    void itemCompletionCancelsBoundaries() {
        verifyNoBuffersNoTerminal();
        original.onComplete();
        emitBoundary();
        assertThat(bufferSubscriber.pollAllOnNext(), empty());
        verifyBufferSubCompleted();
        verifyCancelled(bSubscription);
    }

    @Test
    void itemFailureCancelsBoundaries() {
        verifyNoBuffersNoTerminal();
        original.onError(DELIBERATE_EXCEPTION);
        emitBoundary();
        assertThat(bufferSubscriber.pollAllOnNext(), empty());
        verifyBufferSubFailed(sameInstance(DELIBERATE_EXCEPTION));
        verifyCancelled(bSubscription);
    }

    @Test
    void multipleBoundaries() {
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
    void bufferSubCancel() {
        bufferSubscriber.awaitSubscription().cancel();
        verifyCancelled(tSubscription);
        verifyCancelled(bSubscription);
    }

    @Test
    void itemsBufferedTillBoundariesRequested() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        bufferSubscriber.awaitSubscription().request(1);
        emitBoundary();
        assertThat(bufferSubscriber.takeOnNext(), is(1 + 2 + 3 + 4));
    }

    @Test
    void itemsAndCompletionBufferedTillBoundariesRequested() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        original.onComplete();
        bufferSubscriber.awaitSubscription().request(1);
        emitBoundary();
        assertThat(bufferSubscriber.takeOnNext(), is(1 + 2 + 3 + 4));
        verifyBufferSubCompleted();
        verifyCancelled(bSubscription);
    }

    @Test
    void itemsAndFailureBufferedTillBoundariesRequested() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        original.onError(DELIBERATE_EXCEPTION);
        bufferSubscriber.awaitSubscription().request(1);
        emitBoundary();
        assertThat(bufferSubscriber.takeOnNext(), is(1 + 2 + 3 + 4));
        verifyBufferSubFailed(sameInstance(DELIBERATE_EXCEPTION));
        verifyCancelled(bSubscription);
    }

    @Test
    void boundariesCompletion() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        boundaries.onComplete();
        verifyBufferSubFailed(instanceOf(IllegalStateException.class));

        verifyCancelled(tSubscription);
    }

    @Test
    void boundariesFailure() {
        verifyNoBuffersNoTerminal();

        original.onNext(1, 2, 3, 4);
        boundaries.onError(DELIBERATE_EXCEPTION);
        verifyBufferSubFailed(sameInstance(DELIBERATE_EXCEPTION));
        verifyCancelled(tSubscription);
    }

    @Test
    void accumulateEmitsBoundary() {
        bufferSubscriber.awaitSubscription().request(1);
        boundaries.onNext(new SumAccumulator(boundaries));
        verifyEmptyBufferReceived();
        original.onNext(1);
        assertThat(bufferSubscriber.takeOnNext(), is(1));
    }

    @Test
    void nextAccumulatorsAreIgnoredWhileAccumulating() {
        boundaries.onNext(new Accumulator<Integer, Integer>() {
            private int sum;

            @Override
            public void accumulate(@Nullable final Integer item) {
                if (item == null) {
                    return;
                }
                sum += item;
                emitBoundary();
                // Emit two more boundaries while accumulating
                emitBoundary();
                emitBoundary();
            }

            @Override
            public Integer finish() {
                return sum;
            }
        });
        verifyEmptyBufferReceived();    // discard first boundary
        bufferSubscriber.awaitSubscription().request(1);    // request one more boundary
        original.onNext(1);
        original.onComplete();
        // 2 requested + 1 for `null` state + 2 requests for `NextAccumulatorHolder` state from `accumulate`
        assertThat(bSubscription.requested(), is(5L));
        assertThat(bufferSubscriber.pollAllOnNext(), contains(1));
    }

    @Test
    void nextAccumulatorsAreIgnoredWhileDeliveringOnNext() {
        TestPublisher<Integer> tPublisher = new TestPublisher<>();
        TestPublisher<Accumulator<Integer, Integer>> bPublisher = new TestPublisher<>();
        TestSubscription bSubscription = new TestSubscription();
        AtomicReference<TerminalNotification> terminal = new AtomicReference<>();
        BlockingQueue<Integer> buffers = new LinkedBlockingDeque<>();
        toSource(tPublisher.buffer(new TestBufferStrategy(bPublisher, 1)))
                .subscribe(new Subscriber<Integer>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(1);
                    }

                    @Override
                    public void onNext(@Nullable Integer integer) {
                        assert integer != null;
                        buffers.add(integer);
                        // Emit more than one boundary while accumulating
                        bPublisher.onNext(new SumAccumulator());
                        bPublisher.onNext(new SumAccumulator());
                        bPublisher.onNext(new SumAccumulator());
                    }

                    @Override
                    public void onError(Throwable t) {
                        terminal.set(error(t));
                    }

                    @Override
                    public void onComplete() {
                        terminal.set(complete());
                    }
                });
        assertThat(bPublisher.isSubscribed(), is(true));
        bPublisher.onSubscribe(bSubscription);
        assertThat(bSubscription.requested(), is(1L));

        bPublisher.onNext(new SumAccumulator(bPublisher));
        assertThat(bSubscription.requested(), is(2L));
        tPublisher.onNext(1);

        // 1 requested + 1 for `null` state + 3 requests for `NextAccumulatorHolder` state from `onNext`
        assertThat(bSubscription.requested(), is(5L));

        assertThat(buffers, hasSize(1));
        assertThat(buffers.poll(), is(1));
        assertThat(terminal.get(), is(nullValue()));
        assertThat(bSubscription.isCancelled(), is(false));
    }

    @Test
    void nextItemToAccumulateWhileDeliveringOnNext() {
        TestPublisher<Integer> tPublisher = new TestPublisher<>();
        TestPublisher<Accumulator<Integer, Integer>> bPublisher = new TestPublisher<>();
        AtomicReference<TerminalNotification> terminal = new AtomicReference<>();
        BlockingQueue<Integer> buffers = new LinkedBlockingDeque<>();
        toSource(tPublisher.buffer(new TestBufferStrategy(bPublisher, BUFFER_SIZE_HINT)))
                .subscribe(new Subscriber<Integer>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(2);
                    }

                    @Override
                    public void onNext(@Nullable Integer integer) {
                        assert integer != null;
                        buffers.add(integer);
                        tPublisher.onNext(1);
                    }

                    @Override
                    public void onError(Throwable t) {
                        terminal.set(error(t));
                    }

                    @Override
                    public void onComplete() {
                        terminal.set(complete());
                    }
                });
        bPublisher.onNext(new SumAccumulator());
        bPublisher.onNext(new SumAccumulator());
        tPublisher.onComplete();

        assertThat(buffers, hasSize(2));
        assertThat(buffers, contains(-1, 1));
        assertThat(terminal.get(), is(complete()));
    }

    @Test
    void itemsTerminatedWhileDeliveringOnNext() {
        TestPublisher<Integer> tPublisher = new TestPublisher<>();
        TestPublisher<Accumulator<Integer, Integer>> bPublisher = new TestPublisher<>();
        AtomicReference<TerminalNotification> terminal = new AtomicReference<>();
        BlockingQueue<Integer> buffers = new LinkedBlockingDeque<>();
        toSource(tPublisher.buffer(new TestBufferStrategy(bPublisher, BUFFER_SIZE_HINT)))
                .subscribe(new Subscriber<Integer>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(1);
                    }

                    @Override
                    public void onNext(@Nullable Integer integer) {
                        assert integer != null;
                        buffers.add(integer);
                        tPublisher.onComplete();
                    }

                    @Override
                    public void onError(Throwable t) {
                        terminal.set(error(t));
                    }

                    @Override
                    public void onComplete() {
                        terminal.set(complete());
                    }
                });
        bPublisher.onNext(new SumAccumulator());
        bPublisher.onNext(new SumAccumulator());

        assertThat(buffers, hasSize(1));
        assertThat(buffers.poll(), is(-1));
        assertThat(terminal.get(), is(complete()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void itemsTerminatedBeforeRequestN(boolean failed) {
        TestPublisher<Integer> original = new TestPublisher<>();
        TestPublisher<Accumulator<Integer, Integer>> boundaries = new TestPublisher<>();
        TestPublisherSubscriber<Integer> bufferSubscriber = new TestPublisherSubscriber<>();
        toSource(original.buffer(new TestBufferStrategy(boundaries, BUFFER_SIZE_HINT))).subscribe(bufferSubscriber);
        bufferSubscriber.awaitSubscription();
        assertThat(boundaries.isSubscribed(), is(true));
        assertThat(original.isSubscribed(), is(true));
        if (failed) {
            original.onError(DELIBERATE_EXCEPTION);
            assertThat(bufferSubscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            original.onComplete();
            bufferSubscriber.awaitOnComplete();
        }
        assertThat(bufferSubscriber.pollAllOnNext(), empty());
    }

    @Test
    void terminalBeforeRequestedDelivers() throws Exception {
        Executor executor = Executors.newCachedThreadExecutor();
        try {
            executor.submit(() -> {
                try {
                    tSubscription.awaitRequestN(3);
                } catch (Exception e) {
                    throw new AssertionError(e);
                }

                original.onNext(1);

                try {
                    bSubscription.awaitRequestN(2); // 1 original + 1 synthetic for `null` state
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                emitBoundary();

                original.onNext(2);
                original.onComplete();
            }).toFuture().get();

            assertThat(bufferSubscriber.pollAllOnNext(), contains(1));
            assertThat(bSubscription.requested(), is(2L));   // 1 original + 1 synthetic for `null` state
            verifyCancelled(bSubscription);
            verifyNoBuffersNoTerminal();
            bufferSubscriber.awaitSubscription().request(1);    // request the last buffer
            assertThat(bufferSubscriber.pollAllOnNext(), contains(2));
            verifyBufferSubCompleted();
            // issue one more request-1 to verify we don't terminate twice:
            bufferSubscriber.awaitSubscription().request(1);
        } finally {
            executor.closeAsync().toFuture().get();
        }
    }

    @Test
    void originalSourceIsRetriedIfSubscriberThrows() {
        TestPublisher<Accumulator<Integer, Integer>> bPublisher = new TestPublisher<>();
        DelayedSubscription bSubscription = new DelayedSubscription();
        AtomicReference<TerminalNotification> terminal = new AtomicReference<>();
        BlockingQueue<Integer> items = new LinkedBlockingDeque<>();
        BlockingQueue<Integer> buffers = new LinkedBlockingDeque<>();
        AtomicInteger counter = new AtomicInteger();
        toSource(defer(() -> from(counter.incrementAndGet()))
                .whenOnNext(items::add)
                .retry((i, t) -> i < 3 && t == DELIBERATE_EXCEPTION)
                .buffer(new TestBufferStrategy(bPublisher, 1)))
                .subscribe(new Subscriber<Integer>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        bSubscription.delayedSubscription(s);
                        bSubscription.request(1);
                    }

                    @Override
                    public void onNext(@Nullable Integer integer) {
                        assert integer != null;
                        buffers.add(integer);
                        throw DELIBERATE_EXCEPTION;
                    }

                    @Override
                    public void onError(Throwable t) {
                        terminal.set(error(t));
                    }

                    @Override
                    public void onComplete() {
                        terminal.set(complete());
                    }
                });
        bPublisher.onNext(new SumAccumulator(bPublisher));  // it will generate a new boundary on each accumulation
        assertThat(items, hasSize(1));
        assertThat(items, contains(1));
        assertThat(buffers, hasSize(1));
        assertThat(buffers, contains(1));
        bSubscription.request(MAX_VALUE);

        assertThat(items, hasSize(3));
        assertThat(items, contains(1, 2, 3));
        assertThat(buffers, hasSize(3));
        assertThat(buffers, contains(1, 2, 3));
        assertThat(terminal.get().cause(), is(DELIBERATE_EXCEPTION));
    }

    private static void verifyCancelled(TestSubscription subscription) {
        assertThat("Original source not cancelled: " + subscription, subscription.isCancelled(), is(true));
    }

    private void verifyEmptyBufferReceived() {
        assertThat("Unexpected buffers received.", bufferSubscriber.takeOnNext(), is(EMPTY_ACCUMULATOR_VAL));
    }

    private void verifyNoBuffersNoTerminal() {
        assertThat(bufferSubscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(bufferSubscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    private void emitBoundary() {
        boundaries.onNext(new SumAccumulator());
    }

    private void verifyBufferSubCompleted() {
        bufferSubscriber.awaitOnComplete();
    }

    private void verifyBufferSubFailed(final Matcher<Throwable> causeMatcher) {
        assertThat(bufferSubscriber.awaitOnError(), causeMatcher);
    }

    private static final class SumAccumulator implements Accumulator<Integer, Integer> {
        @Nullable
        private final TestPublisher<Accumulator<Integer, Integer>> boundaries;
        private int sum = EMPTY_ACCUMULATOR_VAL;

        SumAccumulator() {
            this(null);
        }

        SumAccumulator(@Nullable final TestPublisher<Accumulator<Integer, Integer>> boundaries) {
            this.boundaries = boundaries;
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
            if (boundaries != null) {
                boundaries.onNext(new SumAccumulator(boundaries));
            }
        }

        @Override
        public Integer finish() {
            return sum;
        }
    }

    private static final class TestBufferStrategy
            implements BufferStrategy<Integer, Accumulator<Integer, Integer>, Integer> {

        private final Publisher<Accumulator<Integer, Integer>> boundaries;
        private final int bufferSizeHint;

        private TestBufferStrategy(Publisher<Accumulator<Integer, Integer>> boundaries, int bufferSizeHint) {
            this.boundaries = boundaries;
            this.bufferSizeHint = bufferSizeHint;
        }

        @Override
        public Publisher<Accumulator<Integer, Integer>> boundaries() {
            return boundaries;
        }

        @Override
        public int bufferSizeHint() {
            return bufferSizeHint;
        }
    }
}
