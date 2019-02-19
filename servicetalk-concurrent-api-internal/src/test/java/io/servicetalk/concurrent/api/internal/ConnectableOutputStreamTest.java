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
package io.servicetalk.concurrent.api.internal;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntBinaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Runtime.getRuntime;
import static java.lang.System.arraycopy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ConnectableOutputStreamTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expectedException = none();
    @Rule
    public final MockedSubscriberRule<byte[]> subscriberRule = new MockedSubscriberRule<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectableOutputStreamTest.class);

    private IntBinaryOperator nextSizeSupplier;
    private ConnectableOutputStream cos;

    @Before
    public void setUp() {
        nextSizeSupplier = mock(IntBinaryOperator.class);
        when(nextSizeSupplier.applyAsInt(anyInt(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        cos = new ConnectableOutputStream(nextSizeSupplier);
    }

    @Test
    public void writeConnectFlushCloseSubscribe() throws IOException {
        cos.write(1);
        final Publisher<byte[]> connect = cos.connect();
        cos.flush();
        cos.close();
        subscriberRule.subscribe(connect).verifySuccess(new byte[]{1});
        verify(nextSizeSupplier).applyAsInt(0, 1);
    }

    @Test
    public void closeShouldBeIdempotent() throws IOException {
        cos.write(1);
        final Publisher<byte[]> connect = cos.connect();
        cos.flush();
        cos.close();
        subscriberRule.subscribe(connect).verifySuccess(new byte[]{1});
        verify(nextSizeSupplier).applyAsInt(0, 1);
        cos.close(); // should be idempotent
        verifyNoMoreInteractions(nextSizeSupplier);
    }

    @Test
    public void closeShouldBeIdempotentWhenNotSubscribed() throws IOException {
        cos.connect();
        cos.write(1);
        cos.close();
        cos.close(); // should be idempotent
    }

    @Test
    public void multipleConnectWithInvalidRequestnShouldFailConnect() throws Exception {
        @SuppressWarnings("unchecked")
        CountDownLatch onSubscribe = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        cos.connect().subscribe(new Subscriber<byte[]>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.request(-1);
                onSubscribe.countDown();
            }

            @Override
            public void onNext(final byte[] bytes) {
            }

            @Override
            public void onError(final Throwable t) {
                errorRef.set(t);
            }

            @Override
            public void onComplete() {
                onComplete.countDown();
            }
        });
        cos.close();
        onSubscribe.await();
        assertThat(errorRef.get(), instanceOf(IllegalArgumentException.class));
        subscriberRule.subscribe(cos.connect()).verifyFailure(IllegalStateException.class);
        assertThat(onComplete.getCount(), equalTo(1L));
    }

    @Test
    public void multipleConnectWhileEmittingShouldFailConnect() throws Exception {
        @SuppressWarnings("unchecked")
        CountDownLatch onNext = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        cos.connect().subscribe(new Subscriber<byte[]>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(final byte[] bytes) {
                onNext.countDown();
            }

            @Override
            public void onError(final Throwable t) {
            }

            @Override
            public void onComplete() {
                onComplete.countDown();
            }
        });
        cos.write(0);
        cos.flush();
        cos.close();
        onNext.await();
        subscriberRule.subscribe(cos.connect()).verifyFailure(IllegalStateException.class);
        onComplete.await();
    }

    @Test
    public void multipleConnectWhileSubscribedShouldFailConnect() throws Exception {
        @SuppressWarnings("unchecked")
        CountDownLatch onSubscribe = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        cos.connect().subscribe(new Subscriber<byte[]>() {
            @Override
            public void onSubscribe(final Subscription s) {
                onSubscribe.countDown();
            }

            @Override
            public void onNext(final byte[] bytes) {
            }

            @Override
            public void onError(final Throwable t) {
            }

            @Override
            public void onComplete() {
                onComplete.countDown();
            }
        });
        cos.close();
        onSubscribe.await();
        subscriberRule.subscribe(cos.connect()).verifyFailure(IllegalStateException.class);
        onComplete.await();
    }

    @Test
    public void multipleConnectWhileSubscriberFailedShouldFailConnect() throws Exception {
        @SuppressWarnings("unchecked")
        CountDownLatch onError = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        cos.connect().subscribe(new Subscriber<byte[]>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(final byte[] bytes) {
                throw DELIBERATE_EXCEPTION;
            }

            @Override
            public void onError(final Throwable t) {
                onError.countDown();
            }

            @Override
            public void onComplete() {
                onComplete.countDown();
            }
        });
        cos.write(0);
        try {
            cos.flush();
            fail("Should fail with premature termination");
        } catch (IOException e) {
            // Expected
        }
        cos.close();
        onError.await();
        subscriberRule.subscribe(cos.connect()).verifyFailure(IllegalStateException.class);
        assertThat(onComplete.getCount(), equalTo(1L));
    }

    @Test
    public void writeFlushConnectCloseSubscribe() throws IOException {
        cos.write(1);
        verify(nextSizeSupplier).applyAsInt(0, 1);
        cos.flush();
        final Publisher<byte[]> connect = cos.connect();
        cos.close();
        subscriberRule.subscribe(connect).verifySuccess(new byte[]{1});
    }

    @Test
    public void writeFlushCloseConnectSubscribe() throws IOException {
        cos.write(1);
        verify(nextSizeSupplier).applyAsInt(0, 1);
        cos.flush();
        cos.close();
        subscriberRule.subscribe(cos.connect()).verifyFailure(IllegalStateException.class);
    }

    @Test
    public void connectWriteFlushCloseSubscribe() throws IOException {
        final Publisher<byte[]> connect = cos.connect();
        cos.write(1);
        verify(nextSizeSupplier).applyAsInt(0, 1);
        cos.flush();
        cos.close();
        subscriberRule.subscribe(connect).verifySuccess(new byte[]{1});
    }

    @Test
    public void connectSubscribeWriteFlushCloseRequest() throws IOException {
        final Publisher<byte[]> connect = cos.connect();
        subscriberRule.subscribe(connect);
        cos.write(1);
        verify(nextSizeSupplier).applyAsInt(0, 1);
        cos.flush();
        cos.close();
        subscriberRule.verifySuccess(new byte[]{1});
    }

    @Test
    public void connectSubscribeRequestWriteFlushClose() throws IOException {
        final Publisher<byte[]> connect = cos.connect();
        subscriberRule.subscribe(connect).request(1);
        cos.write(1);
        verify(nextSizeSupplier).applyAsInt(0, 1);
        cos.flush();
        cos.close();
        subscriberRule.verifySuccessNoRequestN(new byte[]{1});
    }

    @Test
    public void requestWriteSingleWriteSingleFlushClose() throws IOException {
        final Publisher<byte[]> connect = cos.connect();
        subscriberRule.subscribe(connect).request(1);
        cos.write(1);
        verify(nextSizeSupplier).applyAsInt(0, 1);
        cos.write(1);
        verify(nextSizeSupplier).applyAsInt(1, 2);
        cos.flush();
        cos.close();
        subscriberRule.verifySuccessNoRequestN(new byte[]{1, 1});
    }

    @Test
    public void requestWriteSingleFlushWriteSingleFlushClose() throws IOException {
        final Publisher<byte[]> connect = cos.connect();
        subscriberRule.subscribe(connect).request(2);
        cos.write(1);
        cos.flush();
        cos.write(2);
        verify(nextSizeSupplier, times(2)).applyAsInt(0, 1);
        verifyNoMoreInteractions(nextSizeSupplier);
        cos.flush();
        cos.close();
        subscriberRule.verifySuccessNoRequestN(new byte[]{1}, new byte[]{2});
    }

    @Test
    public void writeSingleFlushWriteSingleFlushRequestClose() throws IOException {
        final Publisher<byte[]> connect = cos.connect();
        subscriberRule.subscribe(connect);
        cos.write(1);
        cos.flush();
        cos.write(2);
        verify(nextSizeSupplier, times(2)).applyAsInt(0, 1);
        verifyNoMoreInteractions(nextSizeSupplier);
        cos.flush();
        cos.close();
        subscriberRule.verifySuccess(new byte[]{1, 2});
    }

    @Test
    public void requestWriteArrWriteArrFlushClose() throws IOException {
        final Publisher<byte[]> connect = cos.connect();
        subscriberRule.subscribe(connect).request(1);
        cos.write(new byte[]{1, 2});
        verify(nextSizeSupplier).applyAsInt(0, 2);
        cos.write(new byte[]{3, 4});
        verify(nextSizeSupplier).applyAsInt(2, 4);
        cos.flush();
        cos.close();
        subscriberRule.verifySuccessNoRequestN(new byte[]{1, 2, 3, 4});
    }

    @Test
    public void requestWriteArrFlushWriteArrFlushClose() throws IOException {
        final Publisher<byte[]> connect = cos.connect();
        subscriberRule.subscribe(connect).request(2);
        cos.write(new byte[]{1, 2});
        cos.flush();
        cos.write(new byte[]{3, 4});
        cos.flush();
        verify(nextSizeSupplier, times(2)).applyAsInt(0, 2);
        verifyNoMoreInteractions(nextSizeSupplier);
        cos.close();
        subscriberRule.verifySuccessNoRequestN(new byte[]{1, 2}, new byte[]{3, 4});
    }

    @Test
    public void writeArrFlushWriteArrFlushRequestClose() throws IOException {
        final Publisher<byte[]> connect = cos.connect();
        subscriberRule.subscribe(connect);
        cos.write(new byte[]{1, 2});
        cos.flush();
        cos.write(new byte[]{3, 4});
        verify(nextSizeSupplier, times(2)).applyAsInt(0, 2);
        verifyNoMoreInteractions(nextSizeSupplier);
        cos.flush();
        cos.close();
        subscriberRule.verifySuccess(new byte[]{1, 2, 3, 4});
    }

    @Test
    public void requestWriteArrOffWriteArrOffFlushClose() throws IOException {
        final Publisher<byte[]> connect = cos.connect();
        subscriberRule.subscribe(connect).request(1);
        cos.write(new byte[]{1, 2, 3, 4}, 1, 3);
        verify(nextSizeSupplier).applyAsInt(0, 3);
        cos.write(new byte[]{5, 6, 7, 8}, 1, 3);
        verify(nextSizeSupplier).applyAsInt(3, 6);
        cos.flush();
        cos.close();
        subscriberRule.verifySuccessNoRequestN(new byte[]{2, 3, 4, 6, 7, 8});
    }

    @Test
    public void requestWriteArrOffFlushWriteArrOffFlushClose() throws IOException {
        final Publisher<byte[]> connect = cos.connect();
        subscriberRule.subscribe(connect).request(2);
        cos.write(new byte[]{1, 2, 3, 4}, 1, 3);
        cos.flush();
        cos.write(new byte[]{5, 6, 7, 8}, 1, 3);
        verify(nextSizeSupplier, times(2)).applyAsInt(0, 3);
        verifyNoMoreInteractions(nextSizeSupplier);
        cos.flush();
        cos.close();
        subscriberRule.verifySuccessNoRequestN(new byte[]{2, 3, 4}, new byte[]{6, 7, 8});
    }

    @Test
    public void writeArrOffFlushWriteArrOffFlushRequestClose() throws IOException {
        final Publisher<byte[]> connect = cos.connect();
        subscriberRule.subscribe(connect);
        cos.write(new byte[]{1, 2, 3, 4}, 1, 3);
        verify(nextSizeSupplier).applyAsInt(0, 3);
        cos.flush();
        cos.write(new byte[]{5, 6, 7, 8}, 1, 2);
        verify(nextSizeSupplier).applyAsInt(0, 2);
        verifyNoMoreInteractions(nextSizeSupplier);
        cos.flush();
        cos.close();
        subscriberRule.verifySuccess(new byte[]{2, 3, 4, 6, 7});
    }

    @Test
    public void invalidRequestN() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        cos.connect().subscribe(new Subscriber<byte[]>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.request(-1);
            }

            @Override
            public void onNext(final byte[] bytes) {
                failure.set(new AssertionError("onNext received for illegal request-n"));
            }

            @Override
            public void onError(final Throwable t) {
                failure.set(t);
            }

            @Override
            public void onComplete() {
                failure.set(new AssertionError("onComplete received for illegal request-n"));
            }
        });

        assertThat("Unexpected failure", failure.get(), is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void onNextThrows() throws IOException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        cos.connect().subscribe(new Subscriber<byte[]>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(final byte[] bytes) {
                throw DELIBERATE_EXCEPTION;
            }

            @Override
            public void onError(final Throwable t) {
                failure.set(t);
            }

            @Override
            public void onComplete() {
                failure.set(new AssertionError("onComplete received when onNext threw."));
            }
        });
        cos.write(1);
        try {
            cos.close();
            fail("Expected close() to fail.");
        } catch (IOException e) {
            assertThat("Unexpected failure", e.getCause(), is(DELIBERATE_EXCEPTION));
        }
        assertThat("Unexpected failure", failure.get(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void multiThreadedProducerConsumer() throws Exception {

        final Random r = new Random();
        final long seed = r.nextLong();  // capture seed to have repeatable tests
        r.setSeed(seed);

        // 3% of heap or max of 100 MiB
        final int dataSize = (int) min(getRuntime().maxMemory() * 0.03, 100 * 1024 * 1024);
        LOGGER.info("Test seed = {} – data size = {}", seed, dataSize);

        final AtomicReference<Throwable> error = new AtomicReference<>();

        final byte[] data = new byte[dataSize];
        final byte[] received = new byte[dataSize];
        r.nextBytes(data);

        final Publisher<byte[]> pub = cos.connect();

        final Thread producerThread = new Thread(() -> {
            int writeIndex = 0;
            try {
                while (writeIndex < dataSize) {
                    // write at most 25% of remaining bytes
                    final int length = (int) max(1, r.nextInt(dataSize - (writeIndex - 1)) * 0.25);
                    LOGGER.debug("Writing {} bytes - writeIndex = {}", length, writeIndex);
                    cos.write(data, writeIndex, length);
                    writeIndex += length;
                    if (r.nextDouble() < 0.4) {
                        LOGGER.debug("Flushing - writeIndex = {}", writeIndex);
                        cos.flush();
                    }
                }
                LOGGER.debug("Closing - writeIndex = {}", writeIndex);
                cos.close();
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        });

        final Thread consumerThread = new Thread(() -> {
            try {
                final CountDownLatch consumerDone = new CountDownLatch(1);
                pub.subscribe(new Subscriber<byte[]>() {
                    @Nullable
                    private Subscription sub;
                    private int writeIndex;

                    @Override
                    public void onSubscribe(final Subscription s) {
                        sub = s;
                        sub.request(1);
                    }

                    @Override
                    public void onNext(final byte[] bytes) {
                        LOGGER.debug("Reading {} bytes - writeIndex = {}", bytes.length, writeIndex);
                        arraycopy(bytes, 0, received, writeIndex, bytes.length);
                        writeIndex += bytes.length;
                        assert sub != null : "Subscription can not be null in onNext.";
                        sub.request(1);
                    }

                    @Override
                    public void onError(final Throwable t) {
                        error.compareAndSet(null, t);
                        consumerDone.countDown();
                    }

                    @Override
                    public void onComplete() {
                        consumerDone.countDown();
                    }
                });
                consumerDone.await();
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        });

        producerThread.start();
        consumerThread.start();

        // make sure both threads exit
        producerThread.join();
        consumerThread.join(); // provides visibility for received from consumerThread
        assertNull(error.get());
        assertArrayEquals(data, received); // assertThat() times out
    }
}
