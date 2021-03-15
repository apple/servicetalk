/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.lang.Long.MAX_VALUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

/**
 * Test for {@link MulticastPublisher} when the source terminates from within
 * {@link Subscriber#onSubscribe(Subscription)}.
 */

public class MulticastRealizedSourcePublisherTest {

    @Test
    public void testOnSubscribeErrors() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        Publisher<Integer> multicast = new TerminateFromOnSubscribePublisher(error(DELIBERATE_EXCEPTION))
                .multicastToExactly(2);
        MulticastSubscriber subscriber1 = new MulticastSubscriber(latch);
        MulticastSubscriber subscriber2 = new MulticastSubscriber(latch);
        toSource(multicast).subscribe(subscriber1);
        toSource(multicast).subscribe(subscriber2);
        latch.await();
        subscriber1.verifyNoFailedAssertions().verifyOnError(DELIBERATE_EXCEPTION);
        subscriber2.verifyNoFailedAssertions().verifyOnError(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testOnSubscribeCompletesNoItems() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        Publisher<Integer> multicast = new TerminateFromOnSubscribePublisher(complete()).multicastToExactly(2);
        MulticastSubscriber subscriber1 = new MulticastSubscriber(latch);
        MulticastSubscriber subscriber2 = new MulticastSubscriber(latch);
        toSource(multicast).subscribe(subscriber1);
        toSource(multicast).subscribe(subscriber2);
        latch.await();
        subscriber1.verifyNoFailedAssertions().verifyOnComplete();
        subscriber2.verifyNoFailedAssertions().verifyOnComplete();
    }

    @Test
    public void testOnSubscribeCompletesWithItems() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        Publisher<Integer> multicast = from(1, 2).multicastToExactly(2);
        MulticastSubscriber subscriber1 = new MulticastSubscriber(latch);
        MulticastSubscriber subscriber2 = new MulticastSubscriber(latch);
        toSource(multicast).subscribe(subscriber1);
        toSource(multicast).subscribe(subscriber2);
        latch.await();
        subscriber1.verifyNoFailedAssertions().verifyItems(1, 2).verifyOnComplete();
        subscriber2.verifyNoFailedAssertions().verifyItems(1, 2).verifyOnComplete();
    }

    @Test
    public void testOnSubscribeCompletesWithSingleItem() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        Publisher<Integer> multicast = from(1).multicastToExactly(2);
        MulticastSubscriber subscriber1 = new MulticastSubscriber(latch);
        MulticastSubscriber subscriber2 = new MulticastSubscriber(latch);
        toSource(multicast).subscribe(subscriber1);
        toSource(multicast).subscribe(subscriber2);
        latch.await();
        subscriber1.verifyNoFailedAssertions().verifyItems(1).verifyOnComplete();
        subscriber2.verifyNoFailedAssertions().verifyItems(1).verifyOnComplete();
    }

    private static final class MulticastSubscriber implements Subscriber<Integer> {
        private boolean onSubscribeReceived;
        private List<Integer> values = new ArrayList<>(3);
        private TerminalNotification terminalNotification;
        private final CountDownLatch terminationLatch;
        @Nullable
        private AssertionError failedAssertion;

        MulticastSubscriber(CountDownLatch terminationLatch) {
            this.terminationLatch = terminationLatch;
        }

        @Override
        public void onSubscribe(Subscription s) {
            onSubscribeReceived = true;
            s.request(MAX_VALUE);
        }

        @Override
        public void onNext(Integer value) {
            if (!onSubscribeReceived) {
                throw new AssertionError("OnNext received before onSubscribe.");
            }
            values.add(value);
        }

        @Override
        public void onError(Throwable t) {
            if (t instanceof AssertionError) {
                failedAssertion = (AssertionError) t;
            } else if (!onSubscribeReceived) {
                failedAssertion = new AssertionError("OnError received before onSubscribe.");
            } else {
                terminalNotification = error(t);
            }
            terminationLatch.countDown();
        }

        @Override
        public void onComplete() {
            if (!onSubscribeReceived) {
                failedAssertion = new AssertionError("OnComplete received before onSubscribe.");
            } else {
                terminalNotification = complete();
            }
            terminationLatch.countDown();
        }

        MulticastSubscriber verifyNoFailedAssertions() {
            if (failedAssertion != null) {
                throw failedAssertion;
            }
            return this;
        }

        MulticastSubscriber verifyItems(Integer... expected) {
            assertThat("Unexpected items emitted.", values, hasSize(expected.length));
            assertThat("Unexpected item emitted.", values, contains(expected));
            return this;
        }

        MulticastSubscriber verifyOnError(Throwable expected) {
            assertThat("Unexpected item emitted.", terminalNotification, is(notNullValue()));
            assertThat("Unexpected item emitted.", expected, is(terminalNotification.cause()));
            return this;
        }

        void verifyOnComplete() {
            assertThat("Unexpected item emitted.", terminalNotification, is(complete()));
        }
    }

    private static class TerminateFromOnSubscribePublisher extends Publisher<Integer> {

        private final TerminalNotification terminalNotification;

        TerminateFromOnSubscribePublisher(TerminalNotification terminalNotification) {
            super(immediate());
            this.terminalNotification = terminalNotification;
        }

        @Override
        protected void handleSubscribe(Subscriber<? super Integer> subscriber) {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    terminalNotification.terminate(subscriber);
                }

                @Override
                public void cancel() {
                    // noop
                }
            });
        }
    }
}
