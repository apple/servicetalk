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

import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.TerminalNotification;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublisherProcessorTest {
    private final BlockingQueue<Integer> bufferQueue = new LinkedBlockingQueue<>();
    private final PublisherProcessor<Integer> processor;
    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();

    @Nullable
    private volatile TerminalNotification terminalNotification;

    PublisherProcessorTest() {
        @SuppressWarnings("unchecked")
        final PublisherProcessorSignalsHolder<Integer> buffer = mock(PublisherProcessorSignalsHolder.class);
        doAnswer(invocation -> {
            bufferQueue.add(invocation.getArgument(0));
            return null;
        }).when(buffer).add(anyInt());
        when(buffer.tryConsume(any())).thenAnswer(invocation -> {
            ProcessorSignalsConsumer<Integer> consumer = invocation.getArgument(0);
            Integer item = bufferQueue.poll();
            if (item == null) {
                return terminateConsumeFromMock(consumer);
            }
            consumer.consumeItem(item);
            return true;
        });
        when(buffer.tryConsumeTerminal(any()))
                .thenAnswer(invocation -> {
                    if (bufferQueue.peek() == null) {
                        return terminateConsumeFromMock(invocation.getArgument(0));
                    }
                    return false;
                });
        doAnswer(__ -> {
            terminalNotification = TerminalNotification.complete();
            return null;
        }).when(buffer).terminate();
        doAnswer(invocation -> {
            terminalNotification = TerminalNotification.error(invocation.getArgument(0));
            return null;
        }).when(buffer).terminate(any(Throwable.class));
        processor = new PublisherProcessor<>(buffer);
        processor.onSubscribe(subscription);
        assertThat("Unexpected items requested from subscription.", subscription.requested(), is(0L));
        assertThat("Subscription cancelled.", subscription.isCancelled(), is(false));
    }

    private boolean terminateConsumeFromMock(final ProcessorSignalsConsumer<Integer> consumer) {
        TerminalNotification notification = terminalNotification;
        if (notification == null) {
            return false;
        }
        if (notification.cause() == null) {
            consumer.consumeTerminal();
        } else {
            consumer.consumeTerminal(notification.cause());
        }
        return true;
    }

    @Test
    void itemBeforeSubscriber() {
        processor.onNext(1);
        toSource(processor).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        assertThat("Unexpected items requested from subscription.", subscription.requested(), is(1L));
        assertThat("Unexpected items received.", subscriber.takeOnNext(), is(1));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    void itemBeforeRequest() {
        toSource(processor).subscribe(subscriber);
        processor.onNext(1);
        subscriber.awaitSubscription().request(1);
        assertThat("Unexpected items requested from subscription.", subscription.requested(), is(1L));
        assertThat("Unexpected items received.", subscriber.takeOnNext(), is(1));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    void terminalBeforeSubscriber() {
        processor.onComplete();
        toSource(processor).subscribe(subscriber);
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    void duplicateTerminalFiltered(boolean firstComplete, boolean secondComplete) {
        toSource(processor).subscribe(subscriber);
        if (firstComplete) {
            processor.onComplete();
            subscriber.awaitOnComplete();
        } else {
            processor.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        }

        // TestPublisherSubscriber will throw on delivery after terminal.
        processor.onNext(1);
        if (secondComplete) {
            processor.onComplete();
        } else {
            processor.onError(DELIBERATE_EXCEPTION);
        }
    }

    @Test
    void terminalAfterSubscriber() {
        toSource(processor).subscribe(subscriber);
        processor.onComplete();
        subscriber.awaitOnComplete();
    }

    @Test
    void terminalErrorBeforeSubscriber() {
        processor.onError(DELIBERATE_EXCEPTION);
        toSource(processor).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void terminalErrorAfterSubscriber() {
        toSource(processor).subscribe(subscriber);
        processor.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void completeDeliveredAfterBuffer() {
        toSource(processor).subscribe(subscriber);
        processor.onNext(1);
        processor.onComplete();
        subscriber.awaitSubscription().request(1);
        assertThat("Unexpected items requested from subscription.", subscription.requested(), is(1L));
        assertThat("Unexpected items received.", subscriber.takeOnNext(), is(1));
        subscriber.awaitOnComplete();
    }

    @Test
    void completeNotDeliveredWithoutRequestWhenAfterBuffer() {
        toSource(processor).subscribe(subscriber);
        processor.onNext(1);
        processor.onComplete();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));

        subscriber.awaitSubscription().request(1);
        assertThat("Unexpected items requested from subscription.", subscription.requested(), is(1L));
        assertThat("Unexpected items received.", subscriber.takeOnNext(), is(1));
        subscriber.awaitOnComplete();
    }

    @Test
    void errorDeliveredAfterBuffer() {
        toSource(processor).subscribe(subscriber);
        processor.onNext(1);
        processor.onError(DELIBERATE_EXCEPTION);
        subscriber.awaitSubscription().request(1);
        assertThat("Unexpected items requested from subscription.", subscription.requested(), is(1L));
        assertThat("Unexpected items received.", subscriber.takeOnNext(), is(1));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void errorNotDeliveredWithoutRequestWhenAfterBuffer() {
        toSource(processor).subscribe(subscriber);
        processor.onNext(1);
        processor.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));

        subscriber.awaitSubscription().request(1);
        assertThat("Unexpected items requested from subscription.", subscription.requested(), is(1L));
        assertThat("Unexpected items received.", subscriber.takeOnNext(), is(1));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void duplicateSubscriber() {
        toSource(processor).subscribe(subscriber);
        TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        toSource(processor).subscribe(subscriber2);
        assertThat(subscriber2.awaitOnError(), instanceOf(DuplicateSubscribeException.class));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    void duplicateSubscriberPostCancel() {
        toSource(processor).subscribe(subscriber);
        subscriber.awaitSubscription().cancel();
        TestPublisherSubscriber<Integer> subscriber2 = new TestPublisherSubscriber<>();
        toSource(processor).subscribe(subscriber2);
        assertThat(subscriber2.awaitOnError(), instanceOf(DuplicateSubscribeException.class));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }
}
