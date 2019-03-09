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

import io.servicetalk.buffer.api.Buffer;
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
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_HEAP_ALLOCATOR;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.api.internal.ConnectablePayloadWriterTest.toRunnable;
import static io.servicetalk.concurrent.api.internal.ConnectablePayloadWriterTest.verifyCheckedRunnableException;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Runtime.getRuntime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
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

    private final TestPublisherSubscriber<Buffer> subscriber = new TestPublisherSubscriber<>();
    private ConnectableOutputStream cos;
    private ExecutorService executorService;

    @Before
    public void setUp() {
        cos = new ConnectableOutputStream(PREFER_HEAP_ALLOCATOR);
        executorService = Executors.newCachedThreadPool();
    }

    @After
    public void teardown() {
        executorService.shutdown();
    }

    @Test
    public void subscribeDeliverDataSynchronously() throws Exception {
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        toSource(cos.connect().doAfterSubscribe(subscription -> {
            subscriber.request(1); // request from the TestPublisherSubscriber!
            // We want to increase the chance that the writer thread has to wait for the Subscriber to become
            // available, instead of waiting for the requestN demand.
            CyclicBarrier barrier = new CyclicBarrier(2);
            futureRef.compareAndSet(null, executorService.submit(toRunnable(() -> {
                barrier.await();
                cos.write(1);
                cos.flush();
                cos.close();
            })));
            try {
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
        })).subscribe(subscriber);

        Future<?> f = futureRef.get();
        assertNotNull(f);
        f.get();
        assertThat(subscriber.takeItems(), contains(new byte[]{1}));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void subscribeCloseSynchronously() throws Exception {
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        toSource(cos.connect().doAfterSubscribe(subscription -> {
            // We want to increase the chance that the writer thread has to wait for the Subscriber to become
            // available, instead of waiting for the requestN demand.
            CyclicBarrier barrier = new CyclicBarrier(2);
            futureRef.compareAndSet(null, executorService.submit(toRunnable(() -> {
                barrier.await();
                cos.close();
            })));
            try {
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
        })).subscribe(subscriber);

        Future<?> f = futureRef.get();
        assertNotNull(f);
        f.get();
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void writeAfterCloseShouldThrow() throws IOException {
        cos.close();
        expectedException.expect(IOException.class);
        cos.write(1);

        // Make sure the Subscription thread isn't blocked.
        subscriber.request(1);
        subscriber.cancel();
    }

    @Test
    public void multipleWriteAfterCloseShouldThrow() throws Exception {
        Future<?> f = executorService.submit(toRunnable(() -> {
            cos.write(1);
            cos.flush();
            cos.close();
            cos.write(2);
            cos.flush();
        }));

        toSource(cos.connect()).subscribe(subscriber);
        subscriber.request(2);
        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            verifyCheckedRunnableException(e, IOException.class);
        }

        assertThat(subscriber.takeItems(), contains(new byte[]{1}));
        assertThat(subscriber.takeTerminal(), is(complete()));

        // Make sure the Subscription thread isn't blocked.
        subscriber.request(1);
        subscriber.cancel();
    }

    @Test
    public void connectMultipleWriteAfterCloseShouldThrow() throws Exception {
        toSource(cos.connect()).subscribe(subscriber);
        subscriber.request(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cos.write(1);
            cos.flush();
            cos.close();
            cos.write(2);
            cos.flush();
        }));

        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            verifyCheckedRunnableException(e, IOException.class);
        }

        assertThat(subscriber.takeItems(), contains(new byte[]{1}));
        assertThat(subscriber.takeTerminal(), is(complete()));

        // Make sure the Subscription thread isn't blocked.
        subscriber.request(1);
        subscriber.cancel();
    }

    @Test
    public void cancelUnblocksWrite() throws Exception {
        CyclicBarrier afterFlushBarrier = new CyclicBarrier(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cos.write(1);
            cos.flush();
            afterFlushBarrier.await();
            cos.write(2);
            cos.flush();
        }));

        toSource(cos.connect()).subscribe(subscriber);
        subscriber.request(1);
        afterFlushBarrier.await();
        subscriber.cancel();
        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            verifyCheckedRunnableException(e, IOException.class);
        }

        assertThat(subscriber.takeItems(), contains(new byte[]{1}));
        assertThat(subscriber.takeTerminal(), is(complete()));
        cos.close(); // should be idempotent

        // Make sure the Subscription thread isn't blocked.
        subscriber.request(1);
        subscriber.cancel();
    }

    @Test
    public void connectCancelUnblocksWrite() throws Exception {
        toSource(cos.connect()).subscribe(subscriber);
        subscriber.cancel();
        Future<?> f = executorService.submit(toRunnable(() -> cos.write(1)));

        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            verifyCheckedRunnableException(e, IOException.class);
        }

        assertThat(subscriber.takeItems(), is(empty()));
        assertThat(subscriber.takeTerminal(), is(complete()));
        cos.close(); // should be idempotent

        // Make sure the Subscription thread isn't blocked.
        subscriber.request(1);
        subscriber.cancel();
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
        assertThat(subscriber.takeItems(), contains(buf(1)));
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
        toSource(cos.connect()).subscribe(new Subscriber<Buffer>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.request(-1);
                onSubscribe.countDown();
            }

            @Override
            public void onNext(final Buffer buffer) {
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
        toSource(cos.connect()).subscribe(new Subscriber<Buffer>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(final Buffer buffer) {
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
        toSource(cos.connect()).subscribe(new Subscriber<Buffer>() {
            @Override
            public void onSubscribe(final Subscription s) {
                onSubscribe.countDown();
            }

            @Override
            public void onNext(final Buffer buffer) {
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
        toSource(cos.connect()).subscribe(new Subscriber<Buffer>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(final Buffer buffer) {
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
        assertThat(subscriber.takeItems(), contains(buf(1)));
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
        assertThat(subscriber.takeItems(), contains(buf(1)));
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
        assertThat(subscriber.takeItems(), contains(buf(1)));
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
        assertThat(subscriber.takeItems(), contains(buf(1), buf(2)));
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
        assertThat(subscriber.takeItems(), contains(buf(1), buf(2)));
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
        assertThat(subscriber.takeItems(), contains(buf(1), buf(2)));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void invalidRequestN() throws IOException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        toSource(cos.connect()).subscribe(new Subscriber<Buffer>() {
            @Override
            public void onSubscribe(final PublisherSource.Subscription s) {
                s.request(-1);
            }

            @Override
            public void onNext(final Buffer buffer) {
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
        toSource(cos.connect()).subscribe(new Subscriber<Buffer>() {
            @Override
            public void onSubscribe(final PublisherSource.Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(final Buffer buffer) {
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
        assertThat(subscriber.takeItems(), contains(buf(1)));

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
            verifyCheckedRunnableException(e, IOException.class);
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
            verifyCheckedRunnableException(e, IOException.class);
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
        final Publisher<Buffer> connect = cos.connect();
        cb.await();
        toSource(connect).subscribe(subscriber);
        subscriber.request(1);
        f.get();
        assertThat(subscriber.takeItems(), is(empty()));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void requestWriteArrWriteArrFlushClose() throws Exception {
        final Publisher<Buffer> connect = cos.connect();
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
        assertThat(subscriber.takeItems(), contains(buf(1, 2), buf(3, 4)));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void requestWriteArrFlushWriteArrFlushClose() throws Exception {
        final Publisher<Buffer> connect = cos.connect();
        toSource(connect).subscribe(subscriber);
        subscriber.request(2);
        executorService.submit(toRunnable(() -> {
            cos.write(new byte[]{1, 2});
            cos.flush();
            cos.write(new byte[]{3, 4});
            cos.flush();
            cos.close();
        })).get();
        assertThat(subscriber.takeItems(), contains(buf(1, 2), buf(3, 4)));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void writeArrFlushWriteArrFlushRequestClose() throws Exception {
        final Publisher<Buffer> connect = cos.connect();
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
        assertThat(subscriber.takeItems(), contains(buf(1, 2), buf(3, 4)));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void requestWriteArrOffWriteArrOffFlushClose() throws Exception {
        final Publisher<Buffer> connect = cos.connect();
        toSource(connect).subscribe(subscriber);
        subscriber.request(2);
        executorService.submit(toRunnable(() -> {
            cos.write(new byte[]{1, 2, 3, 4}, 1, 3);
            cos.write(new byte[]{5, 6, 7, 8}, 1, 3);
            cos.flush();
            cos.close();
        })).get();
        assertThat(subscriber.takeItems(), contains(buf(2, 3, 4), buf(6, 7, 8)));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void requestWriteArrOffFlushWriteArrOffFlushClose() throws Exception {
        final Publisher<Buffer> connect = cos.connect();
        toSource(connect).subscribe(subscriber);
        subscriber.request(2);
        executorService.submit(toRunnable(() -> {
            cos.write(new byte[]{1, 2, 3, 4}, 1, 3);
            cos.flush();
            cos.write(new byte[]{5, 6, 7, 8}, 1, 3);
            cos.flush();
            cos.close();
        })).get();
        assertThat(subscriber.takeItems(), contains(buf(2, 3, 4), buf(6, 7, 8)));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void writeArrOffFlushWriteArrOffFlushRequestClose() throws Exception {
        final Publisher<Buffer> connect = cos.connect();
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
        assertThat(subscriber.takeItems(), contains(buf(2, 3, 4), buf(6, 7)));
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

        final Publisher<Buffer> pub = cos.connect();

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
                toSource(pub).subscribe(new Subscriber<Buffer>() {
                    @Nullable
                    private Subscription sub;
                    private int writeIndex;

                    @Override
                    public void onSubscribe(final Subscription s) {
                        sub = s;
                        sub.request(1);
                    }

                    @Override
                    public void onNext(final Buffer buffer) {
                        final int readingBytes = buffer.readableBytes();
                        LOGGER.debug("Reading {} bytes, writeIndex = {}", readingBytes, writeIndex);
                        buffer.readBytes(received, writeIndex, readingBytes);
                        writeIndex += readingBytes;
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

    private static Buffer buf(int... bytes) {
        final byte[] byteArray = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            byteArray[i] = (byte) bytes[i];
        }
        return PREFER_HEAP_ALLOCATOR.wrap(byteArray);
    }
}
