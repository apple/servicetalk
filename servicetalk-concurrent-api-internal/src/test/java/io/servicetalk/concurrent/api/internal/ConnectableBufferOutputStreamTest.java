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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

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
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.junit.rules.ExpectedException.none;

public class ConnectableBufferOutputStreamTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectableBufferOutputStreamTest.class);
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expectedException = none();

    private final TestPublisherSubscriber<Buffer> subscriber = new TestPublisherSubscriber<>();
    private ConnectableBufferOutputStream cbos;
    private ExecutorService executorService;

    @Before
    public void setUp() {
        cbos = new ConnectableBufferOutputStream(PREFER_HEAP_ALLOCATOR);
        executorService = Executors.newCachedThreadPool();
    }

    @After
    public void teardown() {
        executorService.shutdown();
    }

    @Test
    public void subscribeDeliverDataSynchronously() throws Exception {
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        toSource(cbos.connect().afterOnSubscribe(subscription -> {
            subscriber.awaitSubscription().request(1); // request from the TestPublisherSubscriber!
            // We want to increase the chance that the writer thread has to wait for the Subscriber to become
            // available, instead of waiting for the requestN demand.
            CyclicBarrier barrier = new CyclicBarrier(2);
            futureRef.compareAndSet(null, executorService.submit(toRunnable(() -> {
                barrier.await();
                cbos.write(1);
                cbos.flush();
                cbos.close();
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
        assertThat(subscriber.takeOnNext(), is(buf(1)));
        subscriber.awaitOnComplete();
    }

    @Test
    public void subscribeCloseSynchronously() throws Exception {
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        toSource(cbos.connect().afterOnSubscribe(subscription -> {
            // We want to increase the chance that the writer thread has to wait for the Subscriber to become
            // available, instead of waiting for the requestN demand.
            CyclicBarrier barrier = new CyclicBarrier(2);
            futureRef.compareAndSet(null, executorService.submit(toRunnable(() -> {
                barrier.await();
                cbos.close();
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
        subscriber.awaitOnComplete();
    }

    @Test
    public void writeAfterCloseShouldThrow() throws IOException {
        cbos.close();
        expectedException.expect(IOException.class);
        cbos.write(1);

        // Make sure the Subscription thread isn't blocked.
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
    }

    @Test
    public void multipleWriteAfterCloseShouldThrow() throws Exception {
        Future<?> f = executorService.submit(toRunnable(() -> {
            cbos.write(1);
            cbos.flush();
            cbos.close();
            cbos.write(2);
            cbos.flush();
        }));

        toSource(cbos.connect()).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            verifyCheckedRunnableException(e, IOException.class);
        }

        assertThat(subscriber.takeOnNext(), is(buf(1)));
        subscriber.awaitOnComplete();

        // Make sure the Subscription thread isn't blocked.
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
    }

    @Test
    public void connectMultipleWriteAfterCloseShouldThrow() throws Exception {
        toSource(cbos.connect()).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cbos.write(1);
            cbos.flush();
            cbos.close();
            cbos.write(2);
            cbos.flush();
        }));

        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            verifyCheckedRunnableException(e, IOException.class);
        }

        assertThat(subscriber.takeOnNext(), is(buf(1)));
        subscriber.awaitOnComplete();

        // Make sure the Subscription thread isn't blocked.
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
    }

    @Test
    public void cancelUnblocksWrite() throws Exception {
        CyclicBarrier afterFlushBarrier = new CyclicBarrier(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cbos.write(1);
            cbos.flush();
            afterFlushBarrier.await();
            cbos.write(2);
            cbos.flush();
        }));

        toSource(cbos.connect()).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        afterFlushBarrier.await();
        subscriber.awaitSubscription().cancel();
        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            verifyCheckedRunnableException(e, IOException.class);
        }

        assertThat(subscriber.takeOnNext(), is(buf(1)));
        subscriber.awaitOnComplete();
        cbos.close(); // should be idempotent

        // Make sure the Subscription thread isn't blocked.
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
    }

    @Test
    public void connectCancelUnblocksWrite() throws Exception {
        toSource(cbos.connect()).subscribe(subscriber);
        subscriber.awaitSubscription().cancel();
        Future<?> f = executorService.submit(toRunnable(() -> cbos.write(1)));

        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            verifyCheckedRunnableException(e, IOException.class);
        }

        subscriber.awaitOnComplete();
        cbos.close(); // should be idempotent

        // Make sure the Subscription thread isn't blocked.
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
    }

    @Test
    public void closeShouldBeIdempotent() throws Exception {
        Future<?> f = executorService.submit(toRunnable(() -> {
            cbos.write(1);
            cbos.flush();
            cbos.close();
        }));

        toSource(cbos.connect()).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        f.get();
        assertThat(subscriber.takeOnNext(), is(buf(1)));
        subscriber.awaitOnComplete();
        cbos.close(); // should be idempotent
    }

    @Test
    public void closeShouldBeIdempotentWhenNotSubscribed() throws IOException {
        cbos.connect();
        cbos.close();
        cbos.close(); // should be idempotent
    }

    @Test
    public void multipleConnectWithInvalidRequestnShouldFailConnect() throws Exception {
        CountDownLatch onSubscribe = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        toSource(cbos.connect()).subscribe(new Subscriber<Buffer>() {
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
        cbos.close();
        onSubscribe.await();
        assertThat(errorRef.get(), instanceOf(IllegalArgumentException.class));
        toSource(cbos.connect()).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalStateException.class));
        assertThat(onComplete.getCount(), equalTo(1L));
    }

    @Test
    public void multipleConnectWhileEmittingShouldFailConnect() throws Exception {
        CountDownLatch onNext = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        toSource(cbos.connect()).subscribe(new Subscriber<Buffer>() {
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
        cbos.write(0);
        cbos.flush();
        cbos.close();
        onNext.await();
        toSource(cbos.connect()).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalStateException.class));
        onComplete.await();
    }

    @Test
    public void multipleConnectWhileSubscribedShouldFailConnect() throws Exception {
        CountDownLatch onSubscribe = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        toSource(cbos.connect()).subscribe(new Subscriber<Buffer>() {
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
        cbos.close();
        onSubscribe.await();
        toSource(cbos.connect()).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalStateException.class));
        onComplete.await();
    }

    @Test
    public void multipleConnectWhileSubscriberFailedShouldFailConnect() throws Exception {
        CountDownLatch onError = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        toSource(cbos.connect()).subscribe(new Subscriber<Buffer>() {
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
            cbos.write(1);
            fail();
        } catch (RuntimeException cause) {
            assertSame(DELIBERATE_EXCEPTION, cause);
        }
        try {
            cbos.flush();
            fail();
        } catch (IOException ignored) {
            // expected
        }
        cbos.close();
        onError.await();
        toSource(cbos.connect()).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalStateException.class));
        assertThat(onComplete.getCount(), equalTo(1L));
    }

    @Test
    public void writeFlushCloseConnectSubscribeRequest() throws Exception {
        Future<?> f = executorService.submit(toRunnable(() -> {
            cbos.write(1);
            cbos.flush();
            cbos.close();
        }));

        toSource(cbos.connect()).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        f.get();
        assertThat(subscriber.takeOnNext(), is(buf(1)));
        subscriber.awaitOnComplete();
    }

    @Test
    public void connectSubscribeRequestWriteFlushClose() throws Exception {
        toSource(cbos.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cbos.write(1);
            cbos.flush();
            cbos.close();
        }));
        f.get();
        assertThat(subscriber.takeOnNext(), is(buf(1)));
        subscriber.awaitOnComplete();
    }

    @Test
    public void connectSubscribeWriteFlushCloseRequest() throws Exception {
        toSource(cbos.connect()).subscribe(subscriber);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cbos.write(1);
            cbos.flush();
            cbos.close();
        }));
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        f.get();
        assertThat(subscriber.takeOnNext(), is(buf(1)));
        subscriber.awaitOnComplete();
    }

    @Test
    public void requestWriteSingleWriteSingleFlushClose() throws Exception {
        toSource(cbos.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cbos.write(1);
            cbos.write(2);
            cbos.flush();
            cbos.close();
        }));
        f.get();
        assertThat(subscriber.takeOnNext(2), contains(buf(1), buf(2)));
        subscriber.awaitOnComplete();
    }

    @Test
    public void requestWriteSingleFlushWriteSingleFlushClose() throws Exception {
        toSource(cbos.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cbos.write(1);
            cbos.flush();
            cbos.write(2);
            cbos.flush();
            cbos.close();
        }));
        f.get();
        assertThat(subscriber.takeOnNext(2), contains(buf(1), buf(2)));
        subscriber.awaitOnComplete();
    }

    @Test
    public void writeSingleFlushWriteSingleFlushRequestClose() throws Exception {
        toSource(cbos.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cbos.write(1);
            cbos.flush();
            cbos.write(2);
            cbos.flush();
            cbos.close();
        }));
        subscriber.awaitSubscription().request(1);
        f.get();
        assertThat(subscriber.takeOnNext(2), contains(buf(1), buf(2)));
        subscriber.awaitOnComplete();
    }

    @Test
    public void invalidRequestN() throws IOException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        toSource(cbos.connect()).subscribe(new Subscriber<Buffer>() {
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

        cbos.close();
        assertThat("Unexpected failure", failure.get(), is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void onNextThrows() throws IOException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        toSource(cbos.connect()).subscribe(new Subscriber<Buffer>() {
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
            cbos.write(1);
            fail();
        } catch (RuntimeException cause) {
            assertSame(DELIBERATE_EXCEPTION, cause);
        }
        cbos.close();
        assertThat("Unexpected failure", failure.get(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void cancelCloses() throws Exception {
        toSource(cbos.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        Future<?> f = executorService.submit(toRunnable(() -> cbos.write(1)));
        expectedException.expect(ExecutionException.class);
        expectedException.expectCause(is(instanceOf(RuntimeException.class)));
        f.get();
    }

    @Test
    public void cancelCloseAfterWrite() throws Exception {
        toSource(cbos.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cbos.write(1);
            cbos.flush();
        }));
        f.get();
        assertThat(subscriber.takeOnNext(), is(buf(1)));

        subscriber.awaitSubscription().cancel();
        expectedException.expect(is(instanceOf(IOException.class)));
        cbos.write(2);
    }

    @Test
    public void requestNegativeWrite() throws Exception {
        toSource(cbos.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(-1);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cbos.write(1);
            cbos.flush();
        }));
        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            verifyCheckedRunnableException(e, IOException.class);
        }
        assertThat(subscriber.awaitOnError(), is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void writeRequestNegative() throws Exception {
        toSource(cbos.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        CyclicBarrier cb = new CyclicBarrier(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cb.await();
            cbos.write(1);
            cbos.flush();
        }));
        cb.await();
        subscriber.awaitSubscription().request(-1);
        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            verifyCheckedRunnableException(e, IOException.class);
        }
        assertThat(subscriber.awaitOnError(), is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    public void closeNoWrite() throws Exception {
        CyclicBarrier cb = new CyclicBarrier(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cb.await();
            cbos.close();
        }));
        final Publisher<Buffer> connect = cbos.connect();
        cb.await();
        toSource(connect).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        f.get();
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitOnComplete();
    }

    @Test
    public void requestWriteArrWriteArrFlushClose() throws Exception {
        final Publisher<Buffer> connect = cbos.connect();
        toSource(connect).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cbos.write(new byte[]{1, 2});
            cbos.write(new byte[]{3, 4});
            cbos.flush();
            cbos.close();
        }));
        subscriber.awaitSubscription().request(1);
        f.get();
        assertThat(subscriber.takeOnNext(2), contains(buf(1, 2), buf(3, 4)));
        subscriber.awaitOnComplete();
    }

    @Test
    public void requestWriteArrFlushWriteArrFlushClose() throws Exception {
        final Publisher<Buffer> connect = cbos.connect();
        toSource(connect).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        executorService.submit(toRunnable(() -> {
            cbos.write(new byte[]{1, 2});
            cbos.flush();
            cbos.write(new byte[]{3, 4});
            cbos.flush();
            cbos.close();
        })).get();
        assertThat(subscriber.takeOnNext(2), contains(buf(1, 2), buf(3, 4)));
        subscriber.awaitOnComplete();
    }

    @Test
    public void writeArrFlushWriteArrFlushRequestClose() throws Exception {
        final Publisher<Buffer> connect = cbos.connect();
        toSource(connect).subscribe(subscriber);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cbos.write(new byte[]{1, 2});
            cbos.flush();
            cbos.write(new byte[]{3, 4});
            cbos.flush();
            cbos.close();
        }));
        subscriber.awaitSubscription().request(2);
        f.get();
        assertThat(subscriber.takeOnNext(2), contains(buf(1, 2), buf(3, 4)));
        subscriber.awaitOnComplete();
    }

    @Test
    public void requestWriteArrOffWriteArrOffFlushClose() throws Exception {
        final Publisher<Buffer> connect = cbos.connect();
        toSource(connect).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        executorService.submit(toRunnable(() -> {
            cbos.write(new byte[]{1, 2, 3, 4}, 1, 3);
            cbos.write(new byte[]{5, 6, 7, 8}, 1, 3);
            cbos.flush();
            cbos.close();
        })).get();
        assertThat(subscriber.takeOnNext(2), contains(buf(2, 3, 4), buf(6, 7, 8)));
        subscriber.awaitOnComplete();
    }

    @Test
    public void requestWriteArrOffFlushWriteArrOffFlushClose() throws Exception {
        final Publisher<Buffer> connect = cbos.connect();
        toSource(connect).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        executorService.submit(toRunnable(() -> {
            cbos.write(new byte[]{1, 2, 3, 4}, 1, 3);
            cbos.flush();
            cbos.write(new byte[]{5, 6, 7, 8}, 1, 3);
            cbos.flush();
            cbos.close();
        })).get();
        assertThat(subscriber.takeOnNext(2), contains(buf(2, 3, 4), buf(6, 7, 8)));
        subscriber.awaitOnComplete();
    }

    @Test
    public void writeArrOffFlushWriteArrOffFlushRequestClose() throws Exception {
        final Publisher<Buffer> connect = cbos.connect();
        toSource(connect).subscribe(subscriber);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cbos.write(new byte[]{1, 2, 3, 4}, 1, 3);
            cbos.flush();
            cbos.write(new byte[]{5, 6, 7, 8}, 1, 2);
            cbos.flush();
            cbos.close();
        }));
        subscriber.awaitSubscription().request(2);
        f.get();
        assertThat(subscriber.takeOnNext(2), contains(buf(2, 3, 4), buf(6, 7)));
        subscriber.awaitOnComplete();
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

        final Publisher<Buffer> pub = cbos.connect();

        final Thread producerThread = new Thread(() -> {
            int writeIndex = 0;
            try {
                while (writeIndex < dataSize) {
                    // write at most 25% of remaining bytes
                    final int length = (int) max(1, r.nextInt(dataSize - (writeIndex - 1)) * 0.25);
                    LOGGER.debug("Writing {} bytes - writeIndex = {}", length, writeIndex);
                    cbos.write(data, writeIndex, length);
                    writeIndex += length;
                    if (r.nextDouble() < 0.4) {
                        LOGGER.debug("Flushing - writeIndex = {}", writeIndex);
                        cbos.flush();
                    }
                }
                LOGGER.debug("Closing - writeIndex = {}", writeIndex);
                cbos.close();
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
