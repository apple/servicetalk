/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static java.util.Arrays.copyOf;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public abstract class FromInMemoryPublisherAbstractTest {

    final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();

    protected abstract InMemorySource newPublisher(Executor executor, String[] values);

    @Test
    public void testRequestAllValues() {
        InMemorySource source = newSource(5);
        toSource(source.publisher()).subscribe(subscriber);
        subscriber.request(source.values().length);
        assertThat(subscriber.takeItems(), contains(source.values()));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testRequestInChunks() {
        InMemorySource source = newSource(10);
        toSource(source.publisher()).subscribe(subscriber);
        subscriber.request(2);
        subscriber.request(2);
        subscriber.request(6);
        assertThat(subscriber.takeItems(), contains(source.values()));
    }

    @Test
    public void testNullAsValue() {
        String[] values = {"Hello", null};
        InMemorySource source = newPublisher(immediate(), values);
        toSource(source.publisher()).subscribe(subscriber);
        subscriber.request(2);
        assertThat(subscriber.takeItems(), contains("Hello", null));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testRequestPostComplete() {
        // Due to race between on* and request-n, request-n may arrive after onComplete/onError.
        InMemorySource source = newSource(5);
        toSource(source.publisher()).subscribe(subscriber);
        subscriber.request(source.values().length);
        assertThat(subscriber.takeItems(), contains(source.values()));
        assertThat(subscriber.takeTerminal(), is(complete()));
        subscriber.request(1);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
    }

    @Test
    public void testRequestPostError() {
        String[] values = {"Hello", null};
        InMemorySource source = newPublisher(immediate(), values);
        toSource(source.publisher().doOnNext(n -> {
            if (n == null) {
                throw DELIBERATE_EXCEPTION;
            }
        })).subscribe(subscriber);
        subscriber.request(2);
        assertThat(subscriber.takeItems(), contains("Hello", null));
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
        subscriber.request(1);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
    }

    @Test
    public void testReentrant() {
        InMemorySource source = newSource(6);
        Publisher<String> p = source.publisher().doBeforeOnNext(s -> subscriber.request(5));
        toSource(p).subscribe(subscriber);
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(source.values()));
    }

    @Test
    public void testReactiveStreams2_13() {
        InMemorySource source = newSource(6);
        Publisher<String> p = source.publisher().doBeforeOnNext(s -> {
            throw DELIBERATE_EXCEPTION;
        });
        toSource(p).subscribe(subscriber);
        subscriber.request(6);
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testIncompleteRequest() {
        InMemorySource source = newSource(6);
        requestItemsAndVerifyEmissions(source);
    }

    @Test
    public void testCancel() {
        InMemorySource source = newSource(6);
        requestItemsAndVerifyEmissions(source);
        subscriber.cancel();
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
        subscriber.request(1);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
    }

    @Test
    public void testCancelFromInOnNext() {
        InMemorySource source = newSource(2);
        toSource(source.publisher().doOnNext(n -> {
            subscriber.cancel();
        })).subscribe(subscriber);
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains("Hello0"));
        subscriber.request(1);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
    }

    @Test
    public void testReentrantInvalidRequestN() throws InterruptedException {
        InMemorySource source = newSource(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        toSource(source.publisher()).subscribe(new Subscriber<String>() {
            @Nullable
            private Subscription subscription;
            @Nullable
            private String firstValue;

            @Override
            public void onSubscribe(Subscription s) {
                subscription = s;
                s.request(1);
            }

            @Override
            public void onNext(String s) {
                assert subscription != null;
                if (firstValue == null) {
                    firstValue = s;
                    subscription.request(-1);
                    subscription.request(1);
                } else {
                    throwableRef.set(new IllegalStateException("onNext not expected: " + s));
                }

                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                throwableRef.set(t);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                throwableRef.set(new IllegalStateException("onComplete not expected"));
                latch.countDown();
            }
        });
        latch.await();
        Throwable throwable = throwableRef.get();
        assertTrue("unexpected throwable: " + throwable, throwable instanceof IllegalArgumentException);
    }

    @Test
    public void testInvalidRequestNZeroLengthArrayNoMultipleTerminal() {
        InMemorySource source = newSource(1);
        toSource(source.publisher()).subscribe(subscriber);
        subscriber.request(-1);
        assertThat(subscriber.takeError(), instanceOf(IllegalArgumentException.class));
        subscriber.request(1);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
    }

    @Test
    public void testInvalidRequestAfterCompleteDoesNotDeliverOnError() {
        InMemorySource source = newSource(1);
        toSource(source.publisher()).subscribe(subscriber);
        subscriber.request(1);
        assertThat(subscriber.takeTerminal(), is(complete()));
        subscriber.request(-1);
        assertThat(subscriber.takeError(), nullValue());
    }

    @Test
    public void testOnNextThrows() {
        final AtomicReference<AssertionError> assertErrorRef = new AtomicReference<>();
        final AtomicBoolean onErrorCalled = new AtomicBoolean();

        InMemorySource source = newSource(20);
        toSource(source.publisher()).subscribe(new Subscriber<String>() {
            private boolean onNextCalled;

            @Override
            public void onSubscribe(Subscription s) {
                // Should fail on the first onNext(...)
                s.request(10);

                // Should not produce anymore data
                s.request(10);
            }

            @Override
            public void onNext(String s) {
                try {
                    assertFalse(onNextCalled);
                    assertFalse(onErrorCalled.get());
                } catch (AssertionError e) {
                    assertErrorRef.compareAndSet(null, e);
                }
                onNextCalled = true;
                throw DELIBERATE_EXCEPTION;
            }

            @Override
            public void onError(Throwable t) {
                try {
                    assertTrue(onNextCalled);
                    assertFalse(onErrorCalled.get());
                } catch (AssertionError e) {
                    assertErrorRef.compareAndSet(null, e);
                }
                onErrorCalled.set(true);
            }

            @Override
            public void onComplete() {
                try {
                    fail();
                } catch (AssertionError e) {
                    assertErrorRef.compareAndSet(null, e);
                }
            }
        });

        AssertionError err = assertErrorRef.get();
        if (err != null) {
            throw err;
        }
        assertTrue(onErrorCalled.get());
    }

    private void requestItemsAndVerifyEmissions(InMemorySource source) {
        toSource(source.publisher()).subscribe(subscriber);
        subscriber.request(3);
        assertThat(subscriber.takeItems(), contains(copyOf(source.values(), 3)));
        assertThat(subscriber.takeTerminal(), nullValue());
    }

    final InMemorySource newSource(int size) {
        String[] values = new String[size];
        for (int i = 0; i < size; i++) {
            values[i] = "Hello" + i;
        }
        return newPublisher(immediate(), values);
    }

    protected abstract class InMemorySource {
        private final String[] values;

        protected InMemorySource(String[] values) {
            this.values = requireNonNull(values);
        }

        protected final String[] values() {
            return values;
        }

        protected abstract Publisher<String> publisher();
    }
}
