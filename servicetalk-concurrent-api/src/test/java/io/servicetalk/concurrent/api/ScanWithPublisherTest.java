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

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class ScanWithPublisherTest {

    private boolean withLifetime;

    private enum Flag {
        TRUE(true),
        FALSE(false);

        private final boolean bool;

        Flag(boolean flag) {
            this.bool = flag;
        }
    }

    public void init(final Flag flag) {
        this.withLifetime = flag.bool;
    }

    @Test
    public void scanWithComplete() {
        scanWithNoTerminalMapper(true);
    }

    @Test
    public void scanWithError() {
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

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(Flag.class)
    public void scanOnNextOnCompleteNoConcat(Flag flag) {
        init(flag);
        scanOnNextTerminalNoConcat(true, true, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(Flag.class)
    public void scanOnNextOnErrorNoConcat(Flag flag) {
        init(flag);
        scanOnNextTerminalNoConcat(true, false, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(Flag.class)
    public void scanOnCompleteNoConcat(Flag flag) {
        init(flag);
        scanOnNextTerminalNoConcat(false, true, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(Flag.class)
    public void scanOnErrorNoConcat(Flag flag) {
        init(flag);
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
            public void onFinalize() {
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
    @EnumSource(Flag.class)
    public void onCompleteConcatUpfrontDemand(Flag flag) {
        init(flag);
        terminalConcatWithDemand(true, true, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(Flag.class)
    public void onErrorConcatWithUpfrontDemand(Flag flag) {
        init(flag);
        terminalConcatWithDemand(true, false, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(Flag.class)
    public void onCompleteConcatDelayedDemand(Flag flag) {
        init(flag);
        terminalConcatWithDemand(false, true, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(Flag.class)
    public void onErrorConcatDelayedDemand(Flag flag) {
        init(flag);
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
            public void onFinalize() {
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
    public void scanWithFinalizationOnCancel() {
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
                return null;
            }

            @Override
            public boolean mapTerminal() {
                return true;
            }

            @Override
            public void onFinalize() {
                finalizations.incrementAndGet();
            }
        })).subscribe(subscriber);

        Subscription s = subscriber.awaitSubscription();
        s.request(1);
        processor.onNext(1);
        s.cancel();

        assertThat(finalizations.get(), is(1));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(Flag.class)
    public void onCompleteThrowsHandled(Flag flag) {
        init(flag);
        terminalThrowsHandled(true, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(Flag.class)
    public void onErrorThrowsHandled(Flag flag) {
        init(flag);
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
            public void onFinalize() {
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
    @EnumSource(Flag.class)
    public void mapOnCompleteThrows(Flag flag) {
        init(flag);
        mapTerminalSignalThrows(true, withLifetime);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(Flag.class)
    public void mapOnErrorThrows(Flag flag) {
        init(flag);
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
            public void onFinalize() {
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
    @EnumSource(Flag.class)
    public void invalidDemandAllowsError(Flag flag) {
        init(flag);
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
    @EnumSource(Flag.class)
    public void invalidDemandWithOnNextAllowsError(Flag flag) throws InterruptedException {
        init(flag);
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

    @ParameterizedTest
    @MethodSource("cancelStillAllowsMapsParams")
    public void cancelStillAllowsMaps(boolean onError, boolean cancelBefore) {
        TestPublisher<Integer> publisher = new TestPublisher<>();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(publisher.scanWith(() -> new ScanWithMapper<Integer, Integer>() {
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

        assertThat(subscriber.takeOnNext(4), contains(1, 2, 3, 6));
        subscriber.awaitOnComplete();
    }

    private static Stream<Arguments> cancelStillAllowsMapsParams() {
        return Stream.of(
                Arguments.of(true, true),
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(false, false));
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
            public void onFinalize() {
                finalizations.incrementAndGet();
            }
        };
    }

    private static Publisher<Integer> scanWithOperator(final Publisher<Integer> source, final boolean withLifetime,
                                                       final ScanWithLifetimeMapper<Integer, Integer> mapper) {
        return withLifetime ? source.scanWithLifetime(() -> mapper) : source.scanWith(() -> mapper);
    }
}
