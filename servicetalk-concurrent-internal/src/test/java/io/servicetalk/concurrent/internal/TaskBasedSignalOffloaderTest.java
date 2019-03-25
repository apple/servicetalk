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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

public class TaskBasedSignalOffloaderTest extends AbstractSignalOffloaderTest<TaskBasedSignalOffloader> {

    private Cancellable cancellable;
    private Subscription subscription;

    @Before
    public void setUp() throws Exception {
        doSetup();
        cancellable = mock(Cancellable.class);
        subscription = mock(Subscription.class);
    }

    @Override
    protected TaskBasedSignalOffloader newOffloader(MockExecutor executor) {
        return new TaskBasedSignalOffloader(executor, 2);
    }

    @Test
    public void offloadSubscribePublisher() {
        Consumer<Subscriber<? super Integer>> handleSubscribe = uncheckedMock(Consumer.class);
        offloader.offloadSubscribe(pubSub, handleSubscribe);
        verify(executor.mock).execute(any());
        verifyZeroInteractions(pubSub);
        verifyZeroInteractions(handleSubscribe);
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verify(handleSubscribe).accept(pubSub);
    }

    @Test
    public void offloadSubscribeSingle() {
        Consumer<SingleSource.Subscriber<? super Integer>> handleSubscribe = uncheckedMock(Consumer.class);
        offloader.offloadSubscribe(singleSub, handleSubscribe);
        verify(executor.mock).execute(any());
        verifyZeroInteractions(singleSub);
        verifyZeroInteractions(handleSubscribe);
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verify(handleSubscribe).accept(singleSub);
    }

    @Test
    public void offloadSubscribeCompletable() {
        Consumer<CompletableSource.Subscriber> handleSubscribe = uncheckedMock(Consumer.class);
        offloader.offloadSubscribe(completableSub, handleSubscribe);
        verify(executor.mock).execute(any());
        verifyZeroInteractions(completableSub);
        verifyZeroInteractions(handleSubscribe);
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verify(handleSubscribe).accept(completableSub);
    }

    @Test
    public void offloadedSingleSubscriberNoSignalOverlap() {
        SingleSource.Subscriber<? super Integer> offloaded = offloader.offloadSubscriber(singleSub);
        verifyNoMoreInteractions(executor.mock);
        verifyNoMoreInteractions(singleSub);

        offloaded.onSubscribe(cancellable);
        verify(executor.mock).execute(any());
        verifyNoMoreInteractions(singleSub);
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verify(singleSub).onSubscribe(cancellable);

        offloaded.onSuccess(1);
        verify(executor.mock, times(2)).execute(any());
        verifyNoMoreInteractions(singleSub);
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verify(singleSub).onSuccess(1);
    }

    @Test
    public void offloadedSingleSubscriberNoSignalOverlapError() {
        SingleSource.Subscriber<? super Integer> offloaded = offloader.offloadSubscriber(singleSub);
        verifyNoMoreInteractions(executor.mock);
        verifyNoMoreInteractions(singleSub);

        offloaded.onSubscribe(cancellable);
        verify(executor.mock).execute(any());
        verifyNoMoreInteractions(singleSub);
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verify(singleSub).onSubscribe(cancellable);

        offloaded.onError(DELIBERATE_EXCEPTION);
        verify(executor.mock, times(2)).execute(any());
        verifyNoMoreInteractions(singleSub);
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verify(singleSub).onError(DELIBERATE_EXCEPTION);
    }

    @Test
    public void offloadedSingleSubscriberSignalOverlap() {
        SingleSource.Subscriber<? super Integer> offloaded = offloader.offloadSubscriber(singleSub);
        verifyNoMoreInteractions(executor.mock);
        verifyNoMoreInteractions(singleSub);

        offloaded.onSubscribe(cancellable);
        verify(executor.mock).execute(any());
        verifyNoMoreInteractions(singleSub);
        offloaded.onSuccess(1);
        verifyNoMoreInteractions(executor.mock);

        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verify(singleSub).onSubscribe(cancellable);
        verify(singleSub).onSuccess(1);
        verifyNoMoreInteractions(singleSub);
        verifyNoMoreInteractions(executor.mock);
    }

    @Test
    public void offloadedCompletableSubscriberNoSignalOverlap() {
        CompletableSource.Subscriber offloaded = offloader.offloadSubscriber(completableSub);
        verifyNoMoreInteractions(executor.mock);
        verifyNoMoreInteractions(completableSub);

        offloaded.onSubscribe(cancellable);
        verify(executor.mock).execute(any());
        verifyNoMoreInteractions(completableSub);
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verify(completableSub).onSubscribe(cancellable);

        offloaded.onComplete();
        verify(executor.mock, times(2)).execute(any());
        verifyNoMoreInteractions(completableSub);
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verify(completableSub).onComplete();
    }

    @Test
    public void offloadedCompletableSubscriberNoSignalOverlapError() {
        CompletableSource.Subscriber offloaded = offloader.offloadSubscriber(completableSub);
        verifyNoMoreInteractions(executor.mock);
        verifyNoMoreInteractions(completableSub);

        offloaded.onSubscribe(cancellable);
        verify(executor.mock).execute(any());
        verifyNoMoreInteractions(completableSub);
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verify(completableSub).onSubscribe(cancellable);

        offloaded.onError(DELIBERATE_EXCEPTION);
        verify(executor.mock, times(2)).execute(any());
        verifyNoMoreInteractions(completableSub);
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verify(completableSub).onError(DELIBERATE_EXCEPTION);
    }

    @Test
    public void offloadedCompletableSubscriberSignalOverlap() {
        CompletableSource.Subscriber offloaded = offloader.offloadSubscriber(completableSub);
        verifyNoMoreInteractions(executor.mock);
        verifyNoMoreInteractions(completableSub);

        offloaded.onSubscribe(cancellable);
        verify(executor.mock).execute(any());
        verifyNoMoreInteractions(completableSub);
        offloaded.onComplete();
        verifyNoMoreInteractions(executor.mock);

        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verify(completableSub).onSubscribe(cancellable);
        verify(completableSub).onComplete();
        verifyNoMoreInteractions(completableSub);
        verifyNoMoreInteractions(executor.mock);
    }

    @Test
    public void offloadSingleCancellable() {
        SingleSource.Subscriber<? super Integer> offloaded = offloader.offloadCancellable(singleSub);
        verifyNoMoreInteractions(executor.mock);
        verifyNoMoreInteractions(singleSub);

        offloaded.onSubscribe(cancellable);
        verifyNoMoreInteractions(executor.mock);
        ArgumentCaptor<Cancellable> captor = forClass(Cancellable.class);
        verify(singleSub).onSubscribe(captor.capture());

        captor.getValue().cancel();

        verify(executor.mock).execute(any());
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verifyNoMoreInteractions(singleSub);
        verify(cancellable).cancel();
        verifyNoMoreInteractions(executor.mock);
    }

    @Test
    public void offloadCompletableCancellable() {
        CompletableSource.Subscriber offloaded = offloader.offloadCancellable(completableSub);
        verifyNoMoreInteractions(executor.mock);
        verifyNoMoreInteractions(completableSub);

        offloaded.onSubscribe(cancellable);
        verifyNoMoreInteractions(executor.mock);
        ArgumentCaptor<Cancellable> captor = forClass(Cancellable.class);
        verify(completableSub).onSubscribe(captor.capture());

        captor.getValue().cancel();

        verify(executor.mock).execute(any());
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verifyNoMoreInteractions(completableSub);
        verify(cancellable).cancel();
        verifyNoMoreInteractions(executor.mock);
    }

    @Test
    public void offloadSubscriptionRequestN() {
        Subscription offloaded = offloadSubscription();

        requestNAndVerify(offloaded);
    }

    @Test
    public void offloadSubscriptionRequestNReentrant() {
        Subscription offloaded = offloadSubscription();
        doAnswer(invocation -> {
            pubSub.onNext(1);
            return null;
        }).when(subscription).request(1L);

        doAnswer(invocation -> {
            offloaded.request(2);
            return null;
        }).when(pubSub).onNext(1);

        offloaded.request(1);
        verify(executor.mock).execute(any());
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verify(pubSub).onNext(1);
        verify(subscription).request(1);
        verify(subscription).request(2);
        verifyNoMoreInteractions(executor.mock);
    }

    @Test
    public void offloadSubscriptionRequestNMany() {
        Subscription offloaded = offloadSubscription();

        requestNAndVerify(offloaded);

        offloaded.request(1);
        verify(executor.mock, times(2)).execute(any());
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verifyNoMoreInteractions(pubSub);
        verify(subscription, times(2)).request(1);
        verifyNoMoreInteractions(executor.mock);
    }

    @Test
    public void offloadSubscriptionCancel() {
        Subscription offloaded = offloadSubscription();

        cancelAndVerify(offloaded);
    }

    @Test
    public void offloadSubscriptionRequestNThenCancel() {
        Subscription offloaded = offloadSubscription();
        requestNAndVerify(offloaded);

        offloaded.cancel();
        verify(executor.mock, times(2)).execute(any());
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verifyNoMoreInteractions(pubSub);
        verify(subscription).cancel();
        verifyNoMoreInteractions(executor.mock);
    }

    @Test
    public void offloadSubscriptionInvalidRequestN() {
        Subscription offloaded = offloadSubscription();

        offloaded.request(-4);
        verifyNoMoreInteractions(pubSub);
        verify(executor.mock).execute(any());
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verifyNoMoreInteractions(pubSub);
        verify(subscription).request(-4);
        verifyNoMoreInteractions(executor.mock);
    }

    @Test
    public void offloadSubscriberAllOverlappingSignals() {
        Subscriber<? super Integer> offloaded = offloader.offloadSubscriber(pubSub);
        sendSubscriptionAndVerify(offloaded);
        sendOverlappingSignals(1, offloaded, 1, 2, 3, complete());
    }

    @Test
    public void offloadSubscriberOverlappingOnNextSignals() {
        Subscriber<? super Integer> offloaded = offloader.offloadSubscriber(pubSub);
        sendSubscriptionAndVerify(offloaded);
        sendOverlappingSignals(1, offloaded, 1, 2, 3);
        sendOverlappingSignals(2, offloaded, 4, complete());
    }

    @Test
    public void offloadSubscriberAllOverlappingSignalsError() {
        Subscriber<? super Integer> offloaded = offloader.offloadSubscriber(pubSub);
        sendSubscriptionAndVerify(offloaded);
        sendOverlappingSignals(1, offloaded, 1, 2, 3, error(DELIBERATE_EXCEPTION));
    }

    @Test
    public void offloadSubscriberOverlappingOnNextSignalsError() {
        Subscriber<? super Integer> offloaded = offloader.offloadSubscriber(pubSub);
        sendSubscriptionAndVerify(offloaded);
        sendOverlappingSignals(1, offloaded, 1, 2, 3);
        sendOverlappingSignals(2, offloaded, 4, error(DELIBERATE_EXCEPTION));
    }

    @Test
    public void offloadSubscriberNoOverlappingSignals() {
        sendNonOverlappingSignals(offloader.offloadSubscriber(pubSub), 1, 2, 3, complete());
    }

    @Test
    public void offloadSubscriberNoOverlappingSignalsWithError() {
        sendNonOverlappingSignals(offloader.offloadSubscriber(pubSub),
                1, 2, 3, error(DELIBERATE_EXCEPTION));
    }

    @Test
    public void offloadSubscriberComplete() {
        sendNonOverlappingSignals(offloader.offloadSubscriber(pubSub), complete());
    }

    @Test
    public void offloadSubscriberError() {
        sendNonOverlappingSignals(offloader.offloadSubscriber(pubSub), error(DELIBERATE_EXCEPTION));
    }

    private void sendNonOverlappingSignals(Subscriber<? super Integer> offloaded, Object... signals) {
        sendSubscriptionAndVerify(offloaded);
        for (int i = 0; i < signals.length; i++) {
            final Object signal = signals[i];
            int execInvocationCount = i + 2;
            if (signal instanceof Integer || signal == null) {
                Integer next = signal == null ? null : (Integer) signal;
                offloaded.onNext(next);
                verifyNoMoreInteractions(pubSub);
                verify(executor.mock, times(execInvocationCount)).execute(any());

                assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
                verify(pubSub).onNext(next);
                verifyNoMoreInteractions(executor.mock);
                verifyNoMoreInteractions(pubSub);
            } else if (signal instanceof TerminalNotification) {
                TerminalNotification terminalNotification = (TerminalNotification) signal;
                terminalNotification.terminate(offloaded);
                verifyNoMoreInteractions(pubSub);
                verify(executor.mock, times(execInvocationCount)).execute(any());

                assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
                if (terminalNotification.cause() != null) {
                    verify(pubSub).onError(terminalNotification.cause());
                } else {
                    verify(pubSub).onComplete();
                }
                verifyNoMoreInteractions(executor.mock);
                verifyNoMoreInteractions(pubSub);
            }
        }
    }

    private void sendOverlappingSignals(int previousExecutorInvocations, Subscriber<? super Integer> offloaded,
                                        Object... signals) {
        for (int i = 0; i < signals.length; i++) {
            final Object signal = signals[i];
            if (signal instanceof Integer || signal == null) {
                Integer next = signal == null ? null : (Integer) signal;
                offloaded.onNext(next);
            } else if (signal instanceof TerminalNotification) {
                TerminalNotification terminalNotification = (TerminalNotification) signal;
                terminalNotification.terminate(offloaded);
            }
            verifyNoMoreInteractions(pubSub);
            if (i == 0) {
                verify(executor.mock, times(previousExecutorInvocations + 1)).execute(any());
            }
        }
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        for (final Object signal : signals) {
            if (signal instanceof Integer || signal == null) {
                verify(pubSub).onNext(signal == null ? null : (Integer) signal);
            } else if (signal instanceof TerminalNotification) {
                TerminalNotification terminalNotification = (TerminalNotification) signal;
                if (terminalNotification.cause() != null) {
                    verify(pubSub).onError(terminalNotification.cause());
                } else {
                    verify(pubSub).onComplete();
                }
            }
        }
        verifyNoMoreInteractions(pubSub);
    }

    private void sendSubscriptionAndVerify(Subscriber<? super Integer> offloaded) {
        offloaded.onSubscribe(subscription);
        verify(executor.mock).execute(any());
        verifyNoMoreInteractions(pubSub);

        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verify(pubSub).onSubscribe(subscription);
        verifyNoMoreInteractions(executor.mock);
        verifyNoMoreInteractions(pubSub);
    }

    private void requestNAndVerify(final Subscription offloaded) {
        offloaded.request(1);
        verify(executor.mock).execute(any());
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verifyNoMoreInteractions(pubSub);
        verify(subscription).request(1);
        verifyNoMoreInteractions(executor.mock);
    }

    private void cancelAndVerify(final Subscription offloaded) {
        offloaded.cancel();
        verify(executor.mock).execute(any());
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        verifyNoMoreInteractions(pubSub);
        verify(subscription).cancel();
        verifyNoMoreInteractions(executor.mock);
    }

    private Subscription offloadSubscription() {
        Subscriber<? super Integer> offloaded = offloader.offloadSubscription(pubSub);
        verifyNoMoreInteractions(executor.mock);
        verifyNoMoreInteractions(pubSub);

        offloaded.onSubscribe(subscription);
        verifyNoMoreInteractions(executor.mock);
        ArgumentCaptor<Subscription> captor = forClass(Subscription.class);
        verify(pubSub).onSubscribe(captor.capture());
        return captor.getValue();
    }
}
