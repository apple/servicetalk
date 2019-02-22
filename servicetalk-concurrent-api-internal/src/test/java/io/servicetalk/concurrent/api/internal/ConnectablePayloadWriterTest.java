/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource;
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

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Runtime.getRuntime;
import static java.lang.System.arraycopy;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.rules.ExpectedException.none;

public class ConnectablePayloadWriterTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectablePayloadWriterTest.class);
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expectedException = none();
    @Rule
    public final MockedSubscriberRule<String> subscriberRule = new MockedSubscriberRule<>();
    private ConnectablePayloadWriter<String> cpw;

    @Before
    public void setUp() {
        cpw = new ConnectablePayloadWriter<>();
    }

    @Test
    public void writeConnectFlushCloseSubscribe() {
        cpw.write("foo");
        final Publisher<String> connect = cpw.connect();
        cpw.flush();
        cpw.close();
        subscriberRule.subscribe(connect);
        subscriberRule.verifyNoEmissions();
        subscriberRule.verifySuccess("foo");
    }

    @Test
    public void closeShouldBeIdempotent() {
        cpw.write("foo");
        final Publisher<String> connect = cpw.connect();
        cpw.flush();
        cpw.close();
        subscriberRule.subscribe(connect);
        subscriberRule.verifyNoEmissions();
        subscriberRule.verifySuccess("foo");
        cpw.close(); // should be idempotent
    }

    @Test
    public void closeShouldBeIdempotentWhenNotSubscribed() {
        cpw.connect();
        cpw.write("foo");
        cpw.close();
        cpw.close(); // should be idempotent
    }

    @Test
    public void multipleConnectWithInvalidRequestnShouldFailConnect() throws Exception {
        CountDownLatch onSubscribe = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        toSource(cpw.connect()).subscribe(new PublisherSource.Subscriber<String>() {
            @Override
            public void onSubscribe(final PublisherSource.Subscription s) {
                s.request(-1);
                onSubscribe.countDown();
            }

            @Override
            public void onNext(final String str) {
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
        cpw.close();
        onSubscribe.await();
        assertThat(errorRef.get(), instanceOf(IllegalArgumentException.class));
        subscriberRule.subscribe(cpw.connect()).verifyFailure(IllegalStateException.class);
        assertThat(onComplete.getCount(), equalTo(1L));
    }

    @Test
    public void multipleConnectWhileEmittingShouldFailConnect() throws Exception {
        CountDownLatch onNext = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        toSource(cpw.connect()).subscribe(new PublisherSource.Subscriber<String>() {
            @Override
            public void onSubscribe(final PublisherSource.Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(final String str) {
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
        cpw.write("foo");
        cpw.flush();
        cpw.close();
        onNext.await();
        subscriberRule.subscribe(cpw.connect()).verifyFailure(IllegalStateException.class);
        onComplete.await();
    }

    @Test
    public void multipleConnectWhileSubscribedShouldFailConnect() throws Exception {
        CountDownLatch onSubscribe = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        toSource(cpw.connect()).subscribe(new PublisherSource.Subscriber<String>() {
            @Override
            public void onSubscribe(final PublisherSource.Subscription s) {
                onSubscribe.countDown();
            }

            @Override
            public void onNext(final String str) {
            }

            @Override
            public void onError(final Throwable t) {
            }

            @Override
            public void onComplete() {
                onComplete.countDown();
            }
        });
        cpw.close();
        onSubscribe.await();
        subscriberRule.subscribe(cpw.connect()).verifyFailure(IllegalStateException.class);
        onComplete.await();
    }

    @Test
    public void multipleConnectWhileSubscriberFailedShouldFailConnect() throws Exception {
        CountDownLatch onError = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        toSource(cpw.connect()).subscribe(new PublisherSource.Subscriber<String>() {
            @Override
            public void onSubscribe(final PublisherSource.Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(final String str) {
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
        cpw.write("foo");
        cpw.flush();
        cpw.close();
        onError.await();
        subscriberRule.subscribe(cpw.connect()).verifyFailure(IllegalStateException.class);
        assertThat(onComplete.getCount(), equalTo(1L));
    }

    @Test
    public void writeFlushConnectCloseSubscribe() {
        cpw.write("foo");
        cpw.flush();
        final Publisher<String> connect = cpw.connect();
        cpw.close();
        subscriberRule.subscribe(connect).verifySuccess("foo");
    }

    @Test
    public void writeFlushCloseConnectSubscribe() {
        cpw.write("foo");
        cpw.flush();
        cpw.close();
        subscriberRule.subscribe(cpw.connect()).verifySuccess("foo");
    }

    @Test
    public void connectWriteFlushCloseSubscribe() {
        final Publisher<String> connect = cpw.connect();
        cpw.write("foo");
        cpw.flush();
        cpw.close();
        subscriberRule.subscribe(connect).verifySuccess("foo");
    }

    @Test
    public void connectSubscribeWriteFlushCloseRequest() {
        final Publisher<String> connect = cpw.connect();
        subscriberRule.subscribe(connect);
        cpw.write("foo");
        cpw.flush();
        cpw.close();
        subscriberRule.verifySuccess("foo");
    }

    @Test
    public void connectSubscribeRequestWriteFlushClose() {
        final Publisher<String> connect = cpw.connect();
        subscriberRule.subscribe(connect).request(1);
        cpw.write("foo");
        cpw.flush();
        cpw.close();
        subscriberRule.verifySuccessNoRequestN("foo");
    }

    @Test
    public void requestWriteSingleWriteSingleFlushClose() {
        final Publisher<String> connect = cpw.connect();
        subscriberRule.subscribe(connect).request(2);
        cpw.write("foo");
        cpw.write("bar");
        cpw.flush();
        cpw.close();
        subscriberRule.verifySuccessNoRequestN("foo", "bar");
    }

    @Test
    public void requestWriteSingleFlushWriteSingleFlushClose() {
        final Publisher<String> connect = cpw.connect();
        subscriberRule.subscribe(connect).request(2);
        cpw.write("foo");
        cpw.flush();
        cpw.write("bar");
        cpw.flush();
        cpw.close();
        subscriberRule.verifySuccessNoRequestN("foo", "bar");
    }

    @Test
    public void writeSingleFlushWriteSingleFlushRequestClose() {
        final Publisher<String> connect = cpw.connect();
        subscriberRule.subscribe(connect);
        cpw.write("foo");
        cpw.flush();
        cpw.write("bar");
        cpw.flush();
        cpw.close();
        subscriberRule.request(1);
        subscriberRule.verifyItems("foo");
        subscriberRule.verifyNoEmissions();
        subscriberRule.request(1);
        subscriberRule.verifySuccess("bar");
    }

    @Test
    public void invalidRequestN() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        toSource(cpw.connect()).subscribe(new PublisherSource.Subscriber<String>() {
            @Override
            public void onSubscribe(final PublisherSource.Subscription s) {
                s.request(-1);
            }

            @Override
            public void onNext(final String str) {
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
    public void onNextThrows() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        toSource(cpw.connect()).subscribe(new PublisherSource.Subscriber<String>() {
            @Override
            public void onSubscribe(final PublisherSource.Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(final String str) {
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
        cpw.write("foo");
        cpw.close();
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
        for (int i = 0; i < dataSize; ++i) { // Single character encoding
            data[i] = (byte) (r.nextInt((Byte.MAX_VALUE - 1) + 1) + 1);
        }

        final Publisher<String> pub = cpw.connect();

        final Thread producerThread = new Thread(() -> {
            int writeIndex = 0;
            try {
                while (writeIndex < dataSize) {
                    // write at most 25% of remaining bytes
                    final int length = (int) max(1, r.nextInt(dataSize - (writeIndex - 1)) * 0.25);
                    LOGGER.debug("Writing {} bytes - writeIndex = {}", length, writeIndex);
                    cpw.write(new String(data, writeIndex, length, ISO_8859_1));
                    writeIndex += length;
                    if (r.nextDouble() < 0.4) {
                        LOGGER.debug("Flushing - writeIndex = {}", writeIndex);
                        cpw.flush();
                    }
                }
                LOGGER.debug("Closing - writeIndex = {}", writeIndex);
                cpw.close();
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        });

        final Thread consumerThread = new Thread(() -> {
            try {
                final CountDownLatch consumerDone = new CountDownLatch(1);
                toSource(pub).subscribe(new PublisherSource.Subscriber<String>() {
                    @Nullable
                    private PublisherSource.Subscription sub;
                    private int writeIndex;

                    @Override
                    public void onSubscribe(final PublisherSource.Subscription s) {
                        sub = s;
                        sub.request(1);
                    }

                    @Override
                    public void onNext(final String str) {
                        LOGGER.debug("Reading {} bytes - writeIndex = {}", str.length(), writeIndex);
                        byte[] bytes = str.getBytes(ISO_8859_1);
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
