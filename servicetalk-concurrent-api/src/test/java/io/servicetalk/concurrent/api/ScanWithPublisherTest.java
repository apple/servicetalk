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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

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

public class ScanWithPublisherTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

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

    @Test
    public void scanOnNextOnCompleteNoConcat() {
        scanOnNextTerminalNoConcat(true, true);
    }

    @Test
    public void scanOnNextOnErrorNoConcat() {
        scanOnNextTerminalNoConcat(true, false);
    }

    @Test
    public void scanOnCompleteNoConcat() {
        scanOnNextTerminalNoConcat(false, true);
    }

    @Test
    public void scanOnErrorNoConcat() {
        scanOnNextTerminalNoConcat(false, false);
    }

    private static void scanOnNextTerminalNoConcat(boolean onNext, boolean onComplete) {
        PublisherSource.Processor<Integer, Integer> processor = newPublisherProcessor();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(fromSource(processor).<Integer>scanWith(() -> null, (state, t) -> t,
                state -> false, (state, cause) -> null, state -> state)).subscribe(subscriber);
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
    }

    @Test
    public void onCompleteConcatUpfrontDemand() {
        terminalConcatWithDemand(true, true);
    }

    @Test
    public void onErrorConcatWithUpfrontDemand() {
        terminalConcatWithDemand(true, false);
    }

    @Test
    public void onCompleteConcatDelayedDemand() {
        terminalConcatWithDemand(false, true);
    }

    @Test
    public void onErrorConcatDelayedDemand() {
        terminalConcatWithDemand(false, false);
    }

    private static void terminalConcatWithDemand(boolean demandUpFront, boolean onComplete) {
        PublisherSource.Processor<Integer, Integer> processor = newPublisherProcessor();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(fromSource(processor).scanWith(() -> 0, Integer::sum, state -> true, (state, cause) -> ++state,
                state -> ++state)).subscribe(subscriber);
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
    }

    @Test
    public void onCompleteThrowsHandled() {
        terminalThrowsHandled(true);
    }

    @Test
    public void onErrorThrowsHandled() {
        terminalThrowsHandled(false);
    }

    private static void terminalThrowsHandled(boolean onComplete) {
        PublisherSource.Processor<Integer, Integer> processor = newPublisherProcessor();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(fromSource(processor).<Integer>scanWith(() -> null, (state, t) -> t, state -> true,
                (state, cause) -> {
                    throwException(cause);
                    return state;
                }, state -> {
                    throw DELIBERATE_EXCEPTION;
                })).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        if (onComplete) {
            processor.onComplete();
        } else {
            processor.onError(DELIBERATE_EXCEPTION);
        }
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void mapOnCompleteThrows() {
        mapTerminalSignalThrows(true);
    }

    @Test
    public void mapOnErrorThrows() {
        mapTerminalSignalThrows(false);
    }

    private static void mapTerminalSignalThrows(boolean onComplete) {
        PublisherSource.Processor<Integer, Integer> processor = newPublisherProcessor();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(fromSource(processor).<Integer>scanWith(() -> null, (state, t) -> t, state -> {
            throw DELIBERATE_EXCEPTION;
        }, (state, cause) -> state, state -> state)).subscribe(subscriber);
        subscriber.awaitSubscription();
        if (onComplete) {
            processor.onComplete();
        } else {
            processor.onError(new DeliberateException());
        }
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void invalidDemandAllowsError() {
        PublisherSource.Processor<Integer, Integer> processor = newPublisherProcessor();
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(fromSource(processor).<Integer>scanWith(() -> null, (state, t) -> t,
                state -> false, (state, cause) -> null, state -> state)).subscribe(subscriber);
        subscriber.awaitSubscription().request(-1);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    public void invalidDemandWithOnNextAllowsError() throws InterruptedException {
        TestSubscription upstreamSubscription = new TestSubscription();
        TestPublisher<Integer> publisher = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe()
                .build(subscriber1 -> {
                    subscriber1.onSubscribe(upstreamSubscription);
                    return subscriber1;
                });
        TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
        toSource(publisher.<Integer>scanWith(() -> null, (state, t) -> t,
                state -> true, (state, cause) -> null, state -> state)).subscribe(subscriber);
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
    }
}
