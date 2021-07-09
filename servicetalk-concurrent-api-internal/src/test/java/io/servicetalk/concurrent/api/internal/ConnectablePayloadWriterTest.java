/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Runtime.getRuntime;
import static java.lang.System.arraycopy;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class ConnectablePayloadWriterTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectablePayloadWriterTest.class);

    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
    private ConnectablePayloadWriter<String> cpw;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        cpw = new ConnectablePayloadWriter<>();
        executorService = Executors.newCachedThreadPool();
    }

    @AfterEach
    void teardown() {
        executorService.shutdown();
    }

    @Test
    void subscribeDeliverDataSynchronously() throws Exception {
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        toSource(cpw.connect().afterOnSubscribe(subscription -> {
            subscriber.awaitSubscription().request(1); // request from the TestPublisherSubscriber!
            // We want to increase the chance that the writer thread has to wait for the Subscriber to become
            // available, instead of waiting for the requestN demand.
            CyclicBarrier barrier = new CyclicBarrier(2);
            futureRef.compareAndSet(null, executorService.submit(toRunnable(() -> {
                barrier.await();
                cpw.write("foo");
                cpw.flush();
                cpw.close();
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
        assertThat(subscriber.takeOnNext(), is("foo"));
        subscriber.awaitOnComplete();
    }

    @Test
    void subscribeCloseSynchronously() throws Exception {
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        toSource(cpw.connect().afterOnSubscribe(subscription -> {
            // We want to increase the chance that the writer thread has to wait for the Subscriber to become
            // available, instead of waiting for the requestN demand.
            CyclicBarrier barrier = new CyclicBarrier(2);
            futureRef.compareAndSet(null, executorService.submit(toRunnable(() -> {
                barrier.await();
                cpw.close();
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
    void writeAfterCloseShouldThrow() throws IOException {
        cpw.close();
        assertThrows(IOException.class, () -> cpw.write("foo"));
    }

    @Test
    void multipleWriteAfterCloseShouldThrow() throws Exception {
        Future<?> f = executorService.submit(toRunnable(() -> {
            cpw.write("foo");
            cpw.flush();
            cpw.close();
            cpw.write("bar");
            cpw.flush();
        }));

        toSource(cpw.connect()).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            verifyCheckedRunnableException(e, IOException.class);
        }

        assertThat(subscriber.takeOnNext(), is("foo"));
        subscriber.awaitOnComplete();

        // Make sure the Subscription thread isn't blocked.
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
    }

    @Test
    void connectMultipleWriteAfterCloseShouldThrow() throws Exception {
        toSource(cpw.connect()).subscribe(subscriber);
        subscriber.awaitSubscription().request(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cpw.write("foo");
            cpw.flush();
            cpw.close();
            cpw.write("bar");
            cpw.flush();
        }));

        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            verifyCheckedRunnableException(e, IOException.class);
        }

        assertThat(subscriber.takeOnNext(), is("foo"));
        subscriber.awaitOnComplete();

        // Make sure the Subscription thread isn't blocked.
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
    }

    @Test
    void cancelUnblocksWrite() throws Exception {
        CyclicBarrier afterFlushBarrier = new CyclicBarrier(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cpw.write("foo");
            cpw.flush();
            afterFlushBarrier.await();
            cpw.write("bar");
            cpw.flush();
        }));

        toSource(cpw.connect()).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        afterFlushBarrier.await();
        subscriber.awaitSubscription().cancel();
        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            verifyCheckedRunnableException(e, IOException.class);
        }

        assertThat(subscriber.takeOnNext(), is("foo"));
        assertNoTerminal(subscriber);
        cpw.close(); // should be idempotent

        // Make sure the Subscription thread isn't blocked.
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
    }

    @Test
    void connectCancelUnblocksWrite() throws Exception {
        toSource(cpw.connect()).subscribe(subscriber);
        subscriber.awaitSubscription().cancel();
        Future<?> f = executorService.submit(toRunnable(() -> cpw.write("foo")));

        try {
            f.get();
            fail();
        } catch (ExecutionException e) {
            verifyCheckedRunnableException(e, IOException.class);
        }

        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertNoTerminal(subscriber);
        cpw.close(); // should be idempotent

        // Make sure the Subscription thread isn't blocked.
        subscriber.awaitSubscription().request(1);
        subscriber.awaitSubscription().cancel();
    }

    static void assertNoTerminal(TestPublisherSubscriber<?> subscriber) {
        // We cancelled the subscription, this shouldn't force complete the subscriber and give the illusion we
        // successfully completed writing all data and closed the PayloadWriter.
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    void closeShouldBeIdempotent() throws Exception {
        Future<?> f = executorService.submit(toRunnable(() -> {
            cpw.write("foo");
            cpw.flush();
            cpw.close();
        }));

        toSource(cpw.connect()).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        f.get();
        assertThat(subscriber.takeOnNext(), is("foo"));
        subscriber.awaitOnComplete();
        cpw.close(); // should be idempotent
    }

    @Test
    void closeShouldBeIdempotentWhenNotSubscribed() throws IOException {
        cpw.connect();
        cpw.close();
        cpw.close(); // should be idempotent
    }

    @Test
    void multipleConnectWithInvalidRequestnShouldFailConnect() throws Exception {
        CountDownLatch onSubscribe = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        toSource(cpw.connect()).subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(final Subscription s) {
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
        toSource(cpw.connect()).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), is(instanceOf(IllegalStateException.class)));
        assertThat(onComplete.getCount(), equalTo(1L));
    }

    @Test
    void multipleConnectWhileEmittingShouldFailConnect() throws Exception {
        CountDownLatch onNext = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        toSource(cpw.connect()).subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(final Subscription s) {
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
        toSource(cpw.connect()).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), is(instanceOf(IllegalStateException.class)));
        onComplete.await();
    }

    @Test
    void multipleConnectWhileSubscribedShouldFailConnect() throws Exception {
        CountDownLatch onSubscribe = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        toSource(cpw.connect()).subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(final Subscription s) {
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
        toSource(cpw.connect()).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), is(instanceOf(IllegalStateException.class)));
        onComplete.await();
    }

    @Test
    void multipleConnectWhileSubscriberFailedShouldFailConnect() throws Exception {
        CountDownLatch onError = new CountDownLatch(1);
        CountDownLatch onComplete = new CountDownLatch(1);
        toSource(cpw.connect()).subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(final Subscription s) {
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
        try {
            cpw.write("foo");
            fail();
        } catch (RuntimeException cause) {
            assertSame(DELIBERATE_EXCEPTION, cause);
        }
        try {
            cpw.flush();
            fail();
        } catch (IOException ignored) {
            // expected
        }
        cpw.close();
        onError.await();
        toSource(cpw.connect()).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), is(instanceOf(IllegalStateException.class)));
        assertThat(onComplete.getCount(), equalTo(1L));
    }

    @Test
    void writeFlushCloseConnectSubscribeRequest() throws Exception {
        Future<?> f = executorService.submit(toRunnable(() -> {
            cpw.write("foo");
            cpw.flush();
            cpw.close();
        }));

        toSource(cpw.connect()).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        f.get();
        assertThat(subscriber.takeOnNext(), is("foo"));
        subscriber.awaitOnComplete();
    }

    @Test
    void connectSubscribeRequestWriteFlushClose() throws Exception {
        toSource(cpw.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cpw.write("foo");
            cpw.flush();
            cpw.close();
        }));
        f.get();
        assertThat(subscriber.takeOnNext(), is("foo"));
        subscriber.awaitOnComplete();
    }

    @Test
    void connectSubscribeWriteFlushCloseRequest() throws Exception {
        toSource(cpw.connect()).subscribe(subscriber);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cpw.write("foo");
            cpw.flush();
            cpw.close();
        }));
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        f.get();
        assertThat(subscriber.takeOnNext(), is("foo"));
        subscriber.awaitOnComplete();
    }

    @Test
    void requestWriteSingleWriteSingleFlushClose() throws Exception {
        toSource(cpw.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cpw.write("foo");
            cpw.write("bar");
            cpw.flush();
            cpw.close();
        }));
        f.get();
        assertThat(subscriber.takeOnNext(2), contains("foo", "bar"));
        subscriber.awaitOnComplete();
    }

    @Test
    void requestWriteSingleFlushWriteSingleFlushClose() throws Exception {
        toSource(cpw.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cpw.write("foo");
            cpw.flush();
            cpw.write("bar");
            cpw.flush();
            cpw.close();
        }));
        f.get();
        assertThat(subscriber.takeOnNext(2), contains("foo", "bar"));
        subscriber.awaitOnComplete();
    }

    @Test
    void writeSingleFlushWriteSingleFlushRequestClose() throws Exception {
        toSource(cpw.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cpw.write("foo");
            cpw.flush();
            cpw.write("bar");
            cpw.flush();
            cpw.close();
        }));
        subscriber.awaitSubscription().request(1);
        f.get();
        assertThat(subscriber.takeOnNext(2), contains("foo", "bar"));
        subscriber.awaitOnComplete();
    }

    @Test
    void invalidRequestN() throws IOException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        toSource(cpw.connect()).subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(final Subscription s) {
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

        cpw.close();
        assertThat("Unexpected failure", failure.get(), is(instanceOf(IllegalArgumentException.class)));
    }

    @Test
    void onNextThrows() throws IOException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        toSource(cpw.connect()).subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(final Subscription s) {
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
        try {
            cpw.write("foo");
            fail();
        } catch (RuntimeException cause) {
            assertSame(DELIBERATE_EXCEPTION, cause);
        }
        cpw.close();
        assertThat("Unexpected failure", failure.get(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void cancelCloses() {
        toSource(cpw.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().cancel();
        Future<?> f = executorService.submit(toRunnable(() -> cpw.write("foo")));
        ExecutionException ex = assertThrows(ExecutionException.class, f::get);
        assertThat(ex.getCause(), instanceOf(RuntimeException.class));
    }

    @Test
    void cancelCloseAfterWrite() throws Exception {
        toSource(cpw.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cpw.write("foo");
            cpw.flush();
        }));
        f.get();
        assertThat(subscriber.takeOnNext(), is("foo"));

        subscriber.awaitSubscription().cancel();
        assertThrows(IOException.class, () -> cpw.write("foo"));
    }

    @Test
    void requestNegativeWrite() throws Exception {
        toSource(cpw.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(-1);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cpw.write("foo");
            cpw.flush();
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
    void writeRequestNegative() throws Exception {
        toSource(cpw.connect()).subscribe(subscriber);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        CyclicBarrier cb = new CyclicBarrier(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cb.await();
            cpw.write("foo");
            cpw.flush();
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
    void closeNoWrite() throws Exception {
        CyclicBarrier cb = new CyclicBarrier(2);
        Future<?> f = executorService.submit(toRunnable(() -> {
            cb.await();
            cpw.close();
        }));
        final Publisher<String> connect = cpw.connect();
        cb.await();
        toSource(connect).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        f.get();
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitOnComplete();
    }

    @Test
    void multiThreadedProducerConsumer() throws Exception {
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
                toSource(pub).subscribe(new Subscriber<String>() {
                    @Nullable
                    private Subscription sub;
                    private int writeIndex;

                    @Override
                    public void onSubscribe(final Subscription s) {
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

    static Runnable toRunnable(CheckedRunnable runnable) {
        return () -> {
            try {
                runnable.doWork();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    @FunctionalInterface
    interface CheckedRunnable {
        void doWork() throws Exception;
    }

    static void verifyCheckedRunnableException(ExecutionException e, Class<? extends Throwable> clazz) {
        assertThat(e.getCause(), is(instanceOf(RuntimeException.class))); // this is from toRunnable
        assertThat(e.getCause().getCause(), is(instanceOf(clazz)));
    }
}
