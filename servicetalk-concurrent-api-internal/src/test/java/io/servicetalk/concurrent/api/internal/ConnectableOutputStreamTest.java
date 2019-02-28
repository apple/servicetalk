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
package io.servicetalk.concurrent.api.internal;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.After;
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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.api.internal.ConnectablePayloadWriterTest.toRunnable;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Runtime.getRuntime;
import static java.lang.System.arraycopy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.junit.rules.ExpectedException.none;

public class ConnectableOutputStreamTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectableOutputStreamTest.class);

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expectedException = none();

    private final TestPublisherSubscriber<byte[]> subscriber = new TestPublisherSubscriber<>();
    private ConnectableOutputStream cos;
    private ExecutorService executorService;

    @Before
    public void setUp() {
        cos = new ConnectableOutputStream();
        executorService = Executors.newCachedThreadPool();
    }

    @After
    public void teardown() {
        executorService.shutdown();
    }

    @Test
    public void closeShouldBeIdempotent() throws Exception {
        Future<?> f = executorService.submit(toRunnable(() -> {
            cos.write(1);
            cos.flush();
            cos.close();
        }));

        toSource(cos.connect()).subscribe(subscriber);
        subscriber.request(1);
        f.get();
        assertThat(subscriber.takeItems(), contains(new byte[]{1}));
        assertThat(subscriber.takeTerminal(), is(complete()));
        cos.close(); // should be idempotent
    }

    @Test
    public void closeShouldBeIdempotentWhenNotSubscribed() throws IOException {
        cos.connect();
        cos.close();
        cos.close(); // should be idempotent
    }

    @Test
    public void multipleConnectWithInvalidRequestnShouldFailConnect() throws Exception {
        CountDownLatch onSubscribe = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        toSource(cos.connect()).subscribe(new Subscriber<byte[]>() {
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
        toSource(cos.connect()).subscribe(subscriber);
        assertThat(subscriber.takeError(), instanceOf(IllegalStateException.class));
        assertThat(onComplete.getCount(), equalTo(1L));
    }

    @Test
    public void multipleConnectWhileEmittingShouldFailConnect() throws Exception {
        CountDownLatch onNext = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        toSource(cos.connect()).subscribe(new Subscriber<byte[]>() {
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
        toSource(cos.connect()).subscribe(subscriber);
        assertThat(subscriber.takeError(), instanceOf(IllegalStateException.class));
        onComplete.await();
    }

    @Test
    public void multipleConnectWhileSubscribedShouldFailConnect() throws Exception {
        CountDownLatch onSubscribe = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        toSource(cos.connect()).subscribe(new Subscriber<byte[]>() {
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
        toSource(cos.connect()).subscribe(subscriber);
        assertThat(subscriber.takeError(), instanceOf(IllegalStateException.class));
        onComplete.await();
    }

    @Test
    public void multipleConnectWhileSubscriberFailedShouldFailConnect() throws Exception {
        CountDownLatch onError = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        toSource(cos.connect()).subscribe(new Subscriber<byte[]>() {
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
        try {
            cos.write(1);
            fail();
        } catch (RuntimeException cause) {
            assertSame(DELIBERATE_EXCEPTION, cause);
        }
        try {
            cos.flush();
            fail();
        } catch (IOException ignored) {
            // expected
        }
        cos.close();
        onError.await();
        toSource(cos.connect()).subscribe(subscriber);
        assertThat(subscriber.takeError(), instanceOf(IllegalStateException.class));
        assertThat(onComplete.getCount(), equalTo(1L));
    }

    @Test
    public void writeFlushCloseConnectSubscribeRequest() throws Exception {
        Future<?> f = executorService.submit(toRunnable(() -> {
            cos.write(1);
            cos.flush();
            cos.close();
        }));

        toSource(cos.connect()).subscribe(subscriber);
        subscriber.request(1);
        f.get();
        assertThat(subscriber.takeItems(), contains(new byte[]{1}));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void connectSubscribeRequestWriteFlushClose() throws Exception {
        toSource(cos.connect()).subscribe(subscriber);
        assertThat(subscriber.takeItems(), is(empty()));
        subscriber.request(1);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cos.write(1);
            cos.flush();
            cos.close();
        }));
        f.get();
        assertThat(subscriber.takeItems(), contains(new byte[]{1}));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void connectSubscribeWriteFlushCloseRequest() throws Exception {
        toSource(cos.connect()).subscribe(subscriber);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cos.write(1);
            cos.flush();
            cos.close();
        }));
        assertThat(subscriber.takeItems(), is(empty()));
        subscriber.request(1);
        f.get();
        assertThat(subscriber.takeItems(), contains(new byte[]{1}));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void requestWriteSingleWriteSingleFlushClose() throws Exception {
        toSource(cos.connect()).subscribe(subscriber);
        assertThat(subscriber.takeItems(), is(empty()));
        subscriber.request(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cos.write(1);
            cos.write(2);
            cos.flush();
            cos.close();
        }));
        f.get();
        assertThat(subscriber.takeItems(), contains(new byte[]{1}, new byte[]{2}));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void requestWriteSingleFlushWriteSingleFlushClose() throws Exception {
        toSource(cos.connect()).subscribe(subscriber);
        assertThat(subscriber.takeItems(), is(empty()));
        subscriber.request(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cos.write(1);
            cos.flush();
            cos.write(2);
            cos.flush();
            cos.close();
        }));
        f.get();
        assertThat(subscriber.takeItems(), contains(new byte[]{1}, new byte[]{2}));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void writeSingleFlushWriteSingleFlushRequestClose() throws Exception {
        toSource(cos.connect()).subscribe(subscriber);
        assertThat(subscriber.takeItems(), is(empty()));
        subscriber.request(1);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cos.write(1);
            cos.flush();
            cos.write(2);
            cos.flush();
            cos.close();
        }));
        subscriber.request(1);
        f.get();
        assertThat(subscriber.takeItems(), contains(new byte[]{1}, new byte[]{2}));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void invalidRequestN() throws IOException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        toSource(cos.connect()).subscribe(new PublisherSource.Subscriber<byte[]>() {
            @Override
            public void onSubscribe(final PublisherSource.Subscription s) {
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

        cos.close();
        assertThat("Unexpected failure", failure.get(), is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void onNextThrows() throws IOException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        toSource(cos.connect()).subscribe(new PublisherSource.Subscriber<byte[]>() {
            @Override
            public void onSubscribe(final PublisherSource.Subscription s) {
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
        try {
            cos.write(1);
            fail();
        } catch (RuntimeException cause) {
            assertSame(DELIBERATE_EXCEPTION, cause);
        }
        cos.close();
        assertThat("Unexpected failure", failure.get(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void cancelCloses() throws Exception {
        toSource(cos.connect()).subscribe(subscriber);
        assertThat(subscriber.takeItems(), is(empty()));
        subscriber.cancel();
        Future<?> f = executorService.submit(toRunnable(() -> cos.write(1)));
        expectedException.expect(ExecutionException.class);
        expectedException.expectCause(is(instanceOf(RuntimeException.class)));
        f.get();
    }

    @Test
    public void cancelCloseAfterWrite() throws Exception {
        toSource(cos.connect()).subscribe(subscriber);
        assertThat(subscriber.takeItems(), is(empty()));
        subscriber.request(1);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cos.write(1);
            cos.flush();
        }));
        f.get();
        assertThat(subscriber.takeItems(), contains(new byte[]{1}));

        subscriber.cancel();
        expectedException.expect(is(instanceOf(IOException.class)));
        cos.write(2);
    }

    @Test
    public void requestNegativeWrite() throws Exception {
        toSource(cos.connect()).subscribe(subscriber);
        assertThat(subscriber.takeItems(), is(empty()));
        subscriber.request(-1);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cos.write(1);
            cos.flush();
        }));
        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(RuntimeException.class)));
            assertThat(e.getCause().getCause(), is(instanceOf(IOException.class)));
        }
        assertThat(subscriber.takeError(), is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void writeRequestNegative() throws Exception {
        toSource(cos.connect()).subscribe(subscriber);
        assertThat(subscriber.takeItems(), is(empty()));
        CyclicBarrier cb = new CyclicBarrier(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cb.await();
            cos.write(1);
            cos.flush();
        }));
        cb.await();
        subscriber.request(-1);
        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(RuntimeException.class)));
            assertThat(e.getCause().getCause(), is(instanceOf(IOException.class)));
        }
        assertThat(subscriber.takeError(), is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void closeNoWrite() throws Exception {
        CyclicBarrier cb = new CyclicBarrier(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cb.await();
            cos.close();
        }));
        final Publisher<byte[]> connect = cos.connect();
        cb.await();
        toSource(connect).subscribe(subscriber);
        subscriber.request(1);
        f.get();
        assertThat(subscriber.takeItems(), is(empty()));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void requestWriteArrWriteArrFlushClose() throws Exception {
        final Publisher<byte[]> connect = cos.connect();
        toSource(connect).subscribe(subscriber);
        subscriber.request(1);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cos.write(new byte[]{1, 2});
            cos.write(new byte[]{3, 4});
            cos.flush();
            cos.close();
        }));
        subscriber.request(1);
        f.get();
        assertThat(subscriber.takeItems(), contains(new byte[]{1, 2}, new byte[]{3, 4}));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void requestWriteArrFlushWriteArrFlushClose() throws Exception {
        final Publisher<byte[]> connect = cos.connect();
        toSource(connect).subscribe(subscriber);
        subscriber.request(2);
        executorService.submit(toRunnable(() -> {
            cos.write(new byte[]{1, 2});
            cos.flush();
            cos.write(new byte[]{3, 4});
            cos.flush();
            cos.close();
        })).get();
        assertThat(subscriber.takeItems(), contains(new byte[]{1, 2}, new byte[]{3, 4}));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void writeArrFlushWriteArrFlushRequestClose() throws Exception {
        final Publisher<byte[]> connect = cos.connect();
        toSource(connect).subscribe(subscriber);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cos.write(new byte[]{1, 2});
            cos.flush();
            cos.write(new byte[]{3, 4});
            cos.flush();
            cos.close();
        }));
        subscriber.request(2);
        f.get();
        assertThat(subscriber.takeItems(), contains(new byte[]{1, 2}, new byte[]{3, 4}));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void requestWriteArrOffWriteArrOffFlushClose() throws Exception {
        final Publisher<byte[]> connect = cos.connect();
        toSource(connect).subscribe(subscriber);
        subscriber.request(2);
        executorService.submit(toRunnable(() -> {
            cos.write(new byte[]{1, 2, 3, 4}, 1, 3);
            cos.write(new byte[]{5, 6, 7, 8}, 1, 3);
            cos.flush();
            cos.close();
        })).get();
        assertThat(subscriber.takeItems(), contains(new byte[]{2, 3, 4}, new byte[]{6, 7, 8}));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void requestWriteArrOffFlushWriteArrOffFlushClose() throws Exception {
        final Publisher<byte[]> connect = cos.connect();
        toSource(connect).subscribe(subscriber);
        subscriber.request(2);
        executorService.submit(toRunnable(() -> {
            cos.write(new byte[]{1, 2, 3, 4}, 1, 3);
            cos.flush();
            cos.write(new byte[]{5, 6, 7, 8}, 1, 3);
            cos.flush();
            cos.close();
        })).get();
        assertThat(subscriber.takeItems(), contains(new byte[]{2, 3, 4}, new byte[]{6, 7, 8}));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void writeArrOffFlushWriteArrOffFlushRequestClose() throws Exception {
        final Publisher<byte[]> connect = cos.connect();
        toSource(connect).subscribe(subscriber);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cos.write(new byte[]{1, 2, 3, 4}, 1, 3);
            cos.flush();
            cos.write(new byte[]{5, 6, 7, 8}, 1, 2);
            cos.flush();
            cos.close();
        }));
        subscriber.request(2);
        f.get();
        assertThat(subscriber.takeItems(), contains(new byte[]{2, 3, 4}, new byte[]{6, 7}));
        assertThat(subscriber.takeTerminal(), is(complete()));
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
                toSource(pub).subscribe(new Subscriber<byte[]>() {
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
