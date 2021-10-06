/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class ScanWithPublisherTest {

    @Test
    void scanWithComplete() {
        scanWithNoTerminalMapper(true);
    }

    @Test
    void scanWithError() {
        scanWithNoTerminalMapper(false);
    }

    private static void scanWithNoTerminalMapper(boolean onComplete) {
        PublisherSource.Processor<Integer, Integer> processor = newPublisherProcessor();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(fromSource(processor).scanWith(() -> 0, Integer::sum)).subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(3);
        processor.onNext(1);
        assertThat(subscriber.takeOnNext(), is(1));
        processor.onNext(2);
        assertThat(subscriber.takeOnNext(), is(3));
        processor.onNext(3);
        assertThat(subscriber.takeOnNext(), is(6));
        if (onComplete) {
            processor.onComplete();
            subscriber.awaitOnComplete();
        } else {
            processor.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        }
    }

    @Test
    void scanWithLifetimeSignalReentry() throws InterruptedException {
        AtomicInteger finalizations = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(1);
        PublisherSource<Integer> syncNoReentryProtectionSource =
                subscriber -> subscriber.onSubscribe(new Subscription() {
            int count;

            @Override
            public void request(final long n) {
                if (count == 2) {
                    subscriber.onComplete();
                } else {
                    subscriber.onNext(count++);
                }
            }

            @Override
            public void cancel() {
            }
        });
        toSource(fromSource(syncNoReentryProtectionSource).scanWithLifetime(()
                -> new ScanWithLifetimeMapper<Integer, Integer>() {
            @Override
            public void afterFinally() {
                finalizations.incrementAndGet();
                completed.countDown();
            }

            @Nullable
            @Override
            public Integer mapOnNext(@Nullable final Integer next) {
                return next;
            }

            @Nullable
            @Override
            public Integer mapOnError(final Throwable cause) {
                return null;
            }

            @Nullable
            @Override
            public Integer mapOnComplete() {
                return null;
            }

            @Override
            public boolean mapTerminal() {
                return false;
            }
        })).subscribe(new PublisherSource.Subscriber<Integer>() {
            Subscription subscription;

            @Override
            public void onSubscribe(final Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(@Nullable final Integer integer) {
                subscription.request(1);
            }

            @Override
            public void onError(final Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });

        completed.await();
        assertThat(finalizations.get(), is(1));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {true, false})
    void scanOnNextOnCompleteNoConcat(boolean withLifetime) {
        scanOnNextTerminalNoConcat(true, true, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {true, false})
    void scanOnNextOnErrorNoConcat(boolean withLifetime) {
        scanOnNextTerminalNoConcat(true, false, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {true, false})
    void scanOnCompleteNoConcat(boolean withLifetime) {
        scanOnNextTerminalNoConcat(false, true, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {true, false})
    void scanOnErrorNoConcat(boolean withLifetime) {
        scanOnNextTerminalNoConcat(false, false, withLifetime);
    }

    private static void scanOnNextTerminalNoConcat(boolean onNext, boolean onComplete, boolean withLifetime) {
        final AtomicInteger finalizations = new AtomicInteger(0);
        PublisherSource.Processor<Integer, Integer> processor = newPublisherProcessor();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(scanWithOperator(fromSource(processor), withLifetime, new ScanWithLifetimeMapper<Integer, Integer>() {
            @Nullable
            @Override
            public Integer mapOnNext(@Nullable final Integer next) {
                return next;
            }

            @Nullable
            @Override
            public Integer mapOnError(final Throwable cause) {
                throw new UnsupportedOperationException();
            }

            @Nullable
            @Override
            public Integer mapOnComplete() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean mapTerminal() {
                return false;
            }

            @Override
            public void afterFinally() {
                finalizations.incrementAndGet();
            }
        })).subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        if (onNext) {
            s.request(1);
            processor.onNext(1);
            assertThat(subscriber.takeOnNext(), is(1));
        }
        if (onComplete) {
            processor.onComplete();
            subscriber.awaitOnComplete();
        } else {
            processor.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        }

        if (withLifetime) {
            assertThat(finalizations.get(), is(1));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {true, false})
    void onCompleteConcatUpfrontDemand(boolean withLifetime) {
        terminalConcatWithDemand(true, true, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {true, false})
    void onErrorConcatWithUpfrontDemand(boolean withLifetime) {
        terminalConcatWithDemand(true, false, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {true, false})
    void onCompleteConcatDelayedDemand(boolean withLifetime) {
        terminalConcatWithDemand(false, true, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {true, false})
    void onErrorConcatDelayedDemand(boolean withLifetime) {
        terminalConcatWithDemand(false, false, withLifetime);
    }

    private static void terminalConcatWithDemand(boolean demandUpFront, boolean onComplete, boolean withLifetime) {
        final AtomicInteger finalizations = new AtomicInteger(0);
        PublisherSource.Processor<Integer, Integer> processor = newPublisherProcessor();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(scanWithOperator(fromSource(processor), withLifetime, new ScanWithLifetimeMapper<Integer, Integer>() {
            private int sum;
            @Override
            public Integer mapOnNext(@Nullable final Integer next) {
                if (next != null) {
                    sum += next;
                }
                return sum;
            }

            @Override
            public Integer mapOnError(final Throwable cause) {
                return ++sum;
            }

            @Override
            public Integer mapOnComplete() {
                return ++sum;
            }

            @Override
            public boolean mapTerminal() {
                return true;
            }

            @Override
            public void afterFinally() {
                finalizations.incrementAndGet();
            }
        })).subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(demandUpFront ? 4 : 3);
        processor.onNext(1);
        assertThat(subscriber.takeOnNext(), is(1));
        processor.onNext(2);
        assertThat(subscriber.takeOnNext(), is(3));
        processor.onNext(3);
        assertThat(subscriber.takeOnNext(), is(6));
        if (onComplete) {
            processor.onComplete();
        } else {
            processor.onError(DELIBERATE_EXCEPTION);
        }
        if (!demandUpFront) {
            assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
            s.request(1);
        }
        assertThat(subscriber.takeOnNext(), is(7));
        subscriber.awaitOnComplete();

        if (withLifetime) {
            assertThat(finalizations.get(), is(1));
        }
    }

    @Test
    void scanWithFinalizationOnCancel() {
        final AtomicInteger finalizations = new AtomicInteger(0);
        PublisherSource.Processor<Integer, Integer> processor = newPublisherProcessor();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(scanWithOperator(fromSource(processor), true, new ScanWithLifetimeMapper<Integer, Integer>() {
            @Override
            public Integer mapOnNext(@Nullable final Integer next) {
                return next;
            }

            @Override
            public Integer mapOnError(final Throwable cause) throws Throwable {
                throw cause;
            }

            @Override
            public Integer mapOnComplete() {
                return 5;
            }

            @Override
            public boolean mapTerminal() {
                return true;
            }

            @Override
            public void afterFinally() {
                finalizations.incrementAndGet();
            }
        })).subscribe(subscriber);

        Subscription s = subscriber.awaitSubscription();
        s.request(1);
        processor.onNext(1);
        processor.onComplete();
        s.cancel();

        assertThat(finalizations.get(), is(1));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {true, false})
    void scanWithFinalizationOnCancelDifferentThreads(final boolean interleaveCancellation) {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        try {
            final AtomicInteger finalizations = new AtomicInteger(0);
            PublisherSource.Processor<Integer, Integer> processor = newPublisherProcessor();

            final CountDownLatch checkpoint = new CountDownLatch(1);
            final CountDownLatch requested = new CountDownLatch(1);
            final CountDownLatch resume = new CountDownLatch(1);

            final CountDownLatch nextDelivered = new CountDownLatch(1);
            final CountDownLatch nextDeliveredResume = new CountDownLatch(1);

            final PublisherSource<Integer> source = toSource(scanWithOperator(fromSource(processor), true,
                    new ScanWithLifetimeMapper<Integer, Integer>() {
                        @Override
                        public Integer mapOnNext(@Nullable final Integer next) {
                            return next;
                        }

                        @Override
                        public Integer mapOnError(final Throwable cause) throws Throwable {
                            throw cause;
                        }

                        @Override
                        public Integer mapOnComplete() {
                            return 5;
                        }

                        @Override
                        public boolean mapTerminal() {
                            if (interleaveCancellation) {
                                checkpoint.countDown();
                                try {
                                    resume.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throwException(e);
                                }
                            }
                            return true;
                        }

                        @Override
                        public void afterFinally() {
                            finalizations.incrementAndGet();
                        }
                    }));

            Future<Void> f = executorService.submit(() -> {
                try {
                    TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
                    source.subscribe(subscriber);
                    Subscription s = subscriber.awaitSubscription();

                    s.request(2);
                    requested.countDown();

                    if (interleaveCancellation) {
                        checkpoint.await();
                    } else {
                        nextDelivered.await();
                    }

                    s.cancel();
                    if (!interleaveCancellation) {
                        nextDeliveredResume.countDown();
                    }
                    resume.countDown();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                return null;
            });

            requested.await();
            processor.onNext(1);
            if (!interleaveCancellation) {
                nextDelivered.countDown();
                nextDeliveredResume.await();
            }
            processor.onComplete();

            f.get();
            assertThat(finalizations.get(), is(1));
        } catch (Throwable e) {
            throw new AssertionError(e);
        } finally {
            executorService.shutdown();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {true, false})
    void onCompleteThrowsHandled(boolean withLifetime) {
        terminalThrowsHandled(true, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {true, false})
    void onErrorThrowsHandled(boolean withLifetime) {
        terminalThrowsHandled(false, withLifetime);
    }

    private static void terminalThrowsHandled(boolean onComplete, boolean withLifetime) {
        final AtomicInteger finalizations = new AtomicInteger(0);
        PublisherSource.Processor<Integer, Integer> processor = newPublisherProcessor();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(scanWithOperator(fromSource(processor), withLifetime, new ScanWithLifetimeMapper<Integer, Integer>() {
            @Nullable
            @Override
            public Integer mapOnNext(@Nullable final Integer next) {
                return next;
            }

            @Nullable
            @Override
            public Integer mapOnError(final Throwable cause) throws Throwable {
                throw cause;
            }

            @Nullable
            @Override
            public Integer mapOnComplete() {
                throw DELIBERATE_EXCEPTION;
            }

            @Override
            public boolean mapTerminal() {
                return true;
            }

            @Override
            public void afterFinally() {
                finalizations.incrementAndGet();
            }
        })).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        if (onComplete) {
            processor.onComplete();
        } else {
            processor.onError(DELIBERATE_EXCEPTION);
        }
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));

        if (withLifetime) {
            assertThat(finalizations.get(), is(1));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {true, false})
    void mapOnCompleteThrows(boolean withLifetime) {
        mapTerminalSignalThrows(true, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {true, false})
    void mapOnErrorThrows(boolean withLifetime) {
        mapTerminalSignalThrows(false, withLifetime);
    }

    private static void mapTerminalSignalThrows(boolean onComplete, boolean withLifetime) {
        final AtomicInteger finalizations = new AtomicInteger(0);
        PublisherSource.Processor<Integer, Integer> processor = newPublisherProcessor();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(scanWithOperator(fromSource(processor), withLifetime, new ScanWithLifetimeMapper<Integer, Integer>() {
            @Nullable
            @Override
            public Integer mapOnNext(@Nullable final Integer next) {
                return null;
            }

            @Nullable
            @Override
            public Integer mapOnError(final Throwable cause) {
                return null;
            }

            @Nullable
            @Override
            public Integer mapOnComplete() {
                return null;
            }

            @Override
            public boolean mapTerminal() {
                throw DELIBERATE_EXCEPTION;
            }

            @Override
            public void afterFinally() {
                finalizations.incrementAndGet();
            }
        })).subscribe(subscriber);
        subscriber.awaitSubscription();
        if (onComplete) {
            processor.onComplete();
        } else {
            processor.onError(new DeliberateException());
        }
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));

        if (withLifetime) {
            assertThat(finalizations.get(), is(1));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {true, false})
    void invalidDemandAllowsError(boolean withLifetime) {
        final AtomicInteger finalizations = new AtomicInteger(0);
        PublisherSource.Processor<Integer, Integer> processor = newPublisherProcessor();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(scanWithOperator(fromSource(processor), withLifetime, noopMapper(finalizations)))
                .subscribe(subscriber);
        subscriber.awaitSubscription().request(-1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));

        if (withLifetime) {
            assertThat(finalizations.get(), is(1));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @ValueSource(booleans = {true, false})
    void invalidDemandWithOnNextAllowsError(boolean withLifetime) throws InterruptedException {
        final AtomicInteger finalizations = new AtomicInteger(0);
        TestSubscription upstreamSubscription = new TestSubscription();
        TestPublisher<Integer> publisher = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe()
                .build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(scanWithOperator(publisher, withLifetime, noopMapper(finalizations))).subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(100);
        upstreamSubscription.awaitRequestN(100);
        publisher.onNext(1);
        s.request(-1);
        upstreamSubscription.awaitRequestN(-1);
        publisher.onNext(2);
        assertThat(subscriber.takeOnNext(2), contains(1, 2));
        publisher.onError(newExceptionForInvalidRequestN(-1));
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));

        if (withLifetime) {
            assertThat(finalizations.get(), is(1));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @MethodSource("cancelStillAllowsMapsParams")
    void cancelStillAllowsMaps(boolean onError, boolean cancelBefore, boolean withLifetime) {
        final AtomicInteger finalizations = new AtomicInteger(0);
        TestPublisher<Integer> publisher = new TestPublisher<>();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(scanWithOperator(publisher, withLifetime, new ScanWithLifetimeMapper<Integer, Integer>() {
            private int sum;
            @Nullable
            @Override
            public Integer mapOnNext(@Nullable final Integer next) {
                if (next != null) {
                    sum += next;
                }
                return next;
            }

            @Override
            public Integer mapOnError(final Throwable cause) {
                return sum;
            }

            @Override
            public Integer mapOnComplete() {
                return sum;
            }

            @Override
            public boolean mapTerminal() {
                return true;
            }

            @Override
            public void afterFinally() {
                finalizations.incrementAndGet();
            }
        })).subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();

        if (cancelBefore) {
            s.request(4);
            s.cancel();
        } else {
            s.request(3);
        }

        publisher.onNext(1, 2, 3);

        if (!cancelBefore) {
            s.cancel();
            s.request(1);
        }
        if (onError) {
            publisher.onError(DELIBERATE_EXCEPTION);
        } else {
            publisher.onComplete();
        }

        if (!withLifetime) {
            assertThat(subscriber.takeOnNext(4), contains(1, 2, 3, 6));
            subscriber.awaitOnComplete();
        }

        if (withLifetime) {
            assertThat(finalizations.get(), is(1));
        }
    }

    private static Stream<Arguments> cancelStillAllowsMapsParams() {
        return Stream.of(
                Arguments.of(false, false, false),
                Arguments.of(false, false, true),
                Arguments.of(false, true, false),
                Arguments.of(false, true, true),
                Arguments.of(true, false, false),
                Arguments.of(true, false, true),
                Arguments.of(true, true, false),
                Arguments.of(true, true, true));
    }

    private static ScanWithLifetimeMapper<Integer, Integer> noopMapper(final AtomicInteger finalizations) {
        return new ScanWithLifetimeMapper<Integer, Integer>() {
            @Nullable
            @Override
            public Integer mapOnNext(@Nullable final Integer next) {
                return next;
            }

            @Nullable
            @Override
            public Integer mapOnError(final Throwable cause) {
                return null;
            }

            @Nullable
            @Override
            public Integer mapOnComplete() {
                return null;
            }

            @Override
            public boolean mapTerminal() {
                return true;
            }

            @Override
            public void afterFinally() {
                finalizations.incrementAndGet();
            }
        };
    }

    private static Publisher<Integer> scanWithOperator(final Publisher<Integer> source, final boolean withLifetime,
                                                       final ScanWithLifetimeMapper<Integer, Integer> mapper) {
        return withLifetime ? source.scanWithLifetime(() -> mapper) : source.scanWith(() -> mapper);
    }
}
