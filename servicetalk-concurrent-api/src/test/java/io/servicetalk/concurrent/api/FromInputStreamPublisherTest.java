/*
 * Copyright © 2018-2021, 2023-2024 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.PrimitiveIterator.OfInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.fromInputStream;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.Math.ceil;
import static java.lang.Math.min;
import static java.lang.System.arraycopy;
import static java.util.stream.IntStream.generate;
import static java.util.stream.IntStream.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FromInputStreamPublisherTest {

    private final TestPublisherSubscriber<byte[]> sub1 = new TestPublisherSubscriber<>();
    private final TestPublisherSubscriber<byte[]> sub2 = new TestPublisherSubscriber<>();

    private final byte[] smallBuff = init0toN(10);
    private final byte[] bigBuff = init0toN(37);

    private InputStream inputStream;
    private Publisher<byte[]> pub;

    @BeforeEach
    void setup() {
        inputStream = mock(InputStream.class);
        pub = fromInputStream(inputStream);
    }

    @Test
    void noDuplicateSubscription() throws Exception {
        initEmptyStream();

        toSource(pub).subscribe(sub1);

        toSource(pub).subscribe(sub2);
        assertThat(sub2.awaitOnError(), instanceOf(DuplicateSubscribeException.class));

        sub1.awaitSubscription().request(1);
        sub1.awaitOnComplete();
        verify(inputStream).close();
    }

    @Test
    void noDuplicateSubscriptionAfterError() throws Exception {
        when(inputStream.available()).thenThrow(IOException.class);

        toSource(pub).subscribe(sub1);
        sub1.awaitSubscription().request(1);
        assertThat(sub1.awaitOnError(), instanceOf(IOException.class));

        toSource(pub).subscribe(sub2);
        assertThat(sub2.awaitOnError(), instanceOf(DuplicateSubscribeException.class));
        verify(inputStream).close();
    }

    @Test
    void noDuplicateSubscriptionAfterComplete() throws Exception {
        initEmptyStream();

        toSource(pub).subscribe(sub1);
        sub1.awaitSubscription().request(1);
        sub1.awaitOnComplete();

        toSource(pub).subscribe(sub2);
        assertThat(sub2.awaitOnError(), instanceOf(DuplicateSubscribeException.class));
        verify(inputStream).close();
    }

    @Test
    void noDuplicateSubscriptionAfterCancel() throws Exception {
        initEmptyStream();

        toSource(pub).subscribe(sub1);
        sub1.awaitSubscription().request(1);
        sub1.awaitSubscription().cancel();

        toSource(pub).subscribe(sub2);
        assertThat(sub2.awaitOnError(), instanceOf(DuplicateSubscribeException.class));
        verify(inputStream).close();
    }

    @Test
    void closeStreamOnCompleteByDefault() throws Exception {
        initEmptyStream();

        toSource(pub).subscribe(sub1);
        sub1.awaitSubscription().request(1);
        assertThat(sub1.pollAllOnNext(), is(empty()));
        sub1.awaitOnComplete();

        verify(inputStream).close();
    }

    @Test
    void closeStreamOnCancelByDefault() throws Exception {
        toSource(pub).subscribe(sub1);
        sub1.awaitSubscription().cancel();
        verify(inputStream).close();
    }

    @Test
    void closeStreamOnThrowingSubscriber() throws Exception {
        AtomicReference<Throwable> onError = new AtomicReference<>();
        toSource(pub).subscribe(new Subscriber<byte[]>() {
            @Override
            public void onSubscribe(final Subscription subscription) {
                throw DELIBERATE_EXCEPTION;
            }

            @Override
            public void onNext(@Nullable final byte[] bytes) {
            }

            @Override
            public void onError(final Throwable t) {
                onError.set(t);
            }

            @Override
            public void onComplete() {
            }
        });
        assertThat(onError.get(), is(DELIBERATE_EXCEPTION));
        verify(inputStream).close();
    }

    @Test
    void streamClosedAndErrorOnInvalidReqN() throws Exception {
        toSource(pub).subscribe(sub1);
        sub1.awaitSubscription().request(-1);
        assertThat(sub1.awaitOnError(), instanceOf(IllegalArgumentException.class));

        verify(inputStream).close();
    }

    @Test
    void streamClosedAndErrorOnInvalidReqNAndValidReqN() throws Exception {
        initChunkedStream(smallBuff, of(10, 0), of(10, 0));

        toSource(pub).subscribe(sub1);
        sub1.awaitSubscription().request(-1);
        sub1.awaitSubscription().request(10);

        assertThat(sub1.awaitOnError(), instanceOf(IllegalArgumentException.class));
        verify(inputStream).close();
    }

    @Test
    void streamClosedAndErrorOnDoubleInvalidReqN() throws Exception {
        initChunkedStream(smallBuff, of(10, 0), of(10, 0));

        toSource(pub).subscribe(sub1);
        sub1.awaitSubscription().request(-1);
        sub1.awaitSubscription().request(-1);

        assertThat(sub1.awaitOnError(), instanceOf(IllegalArgumentException.class));
        verify(inputStream).close();
    }

    @Test
    void streamClosedAndErrorOnAvailableIOError() throws Exception {
        when(inputStream.available()).thenThrow(IOException.class);
        toSource(pub).subscribe(sub1);
        sub1.awaitSubscription().request(1);
        assertThat(sub1.awaitOnError(), instanceOf(IOException.class));
        verify(inputStream).close();
    }

    @Test
    void streamClosedAndErrorOnReadIOError() throws Exception {
        when(inputStream.available()).thenReturn(10);
        when(inputStream.read(any(), anyInt(), anyInt())).thenThrow(IOException.class);

        toSource(pub).subscribe(sub1);
        sub1.awaitSubscription().request(1);
        assertThat(sub1.awaitOnError(), instanceOf(IOException.class));
        verify(inputStream).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void streamClosedAndErrorOnDeliveryError() throws Exception {
        initChunkedStream(smallBuff, of(10), of(10));

        Subscriber<byte[]> sub = mock(Subscriber.class);

        doAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.request(1);
            return null;
        }).when(sub).onSubscribe(any());
        doThrow(DELIBERATE_EXCEPTION).when(sub).onNext(any());

        toSource(pub).subscribe(sub);

        verify(inputStream).close();
        verify(sub, never()).onComplete();
        verify(sub).onError(DELIBERATE_EXCEPTION);
    }

    @Test
    @SuppressWarnings("unchecked")
    void streamClosedAndErrorOnDeliveryErrorOnce() throws Exception {
        initChunkedStream(smallBuff, ofAll(10), ofAll(10));

        Subscriber<byte[]> sub = mock(Subscriber.class);

        AtomicReference<Subscription> subRef = new AtomicReference<>();
        doAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            subRef.set(s);
            s.request(1);
            return null;
        }).when(sub).onSubscribe(any());
        doThrow(DELIBERATE_EXCEPTION).when(sub).onNext(any());

        toSource(pub).subscribe(sub);
        // triggers another delivery + failure, to ensure we only observe a single terminal event
        subRef.get().request(1);

        verify(sub, never()).onComplete();
        verify(sub).onError(DELIBERATE_EXCEPTION);
        verify(inputStream).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void streamCanceledShouldCloseOnce() throws Exception {
        initChunkedStream(smallBuff, ofAll(10), ofAll(10));

        Subscriber<byte[]> sub = mock(Subscriber.class);

        doAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.request(1);
            s.cancel();
            return null;
        }).when(sub).onSubscribe(any());
        doThrow(DELIBERATE_EXCEPTION).when(sub).onNext(any());

        toSource(pub).subscribe(sub);

        verify(sub, never()).onComplete();
        verify(sub).onError(DELIBERATE_EXCEPTION);
        verify(inputStream).close();
    }

    @Test
    void breakReentrantRequestN() throws Throwable {

        AtomicInteger count = new AtomicInteger();
        AtomicBoolean complete = new AtomicBoolean();
        AtomicReference<Throwable> error = new AtomicReference<>();

        when(inputStream.available()).thenReturn(1);
        when(inputStream.read(any(), anyInt(), anyInt())).then(inv -> {
            if (count.incrementAndGet() > 1_000) {
                return -1;
            }
            return 1;
        });

        toSource(pub).subscribe(new Subscriber<byte[]>() {
            private Subscription s;

            @Override
            public void onSubscribe(final Subscription s) {
                this.s = s;
                s.request(1);
            }

            @Override
            public void onNext(final byte[] b) {
                s.request(1);
            }

            @Override
            public void onError(final Throwable t) {
                error.set(t);
            }

            @Override
            public void onComplete() {
                complete.set(true);
            }
        });

        if (error.get() != null) {
            throw error.get();
        }
        assertThat(complete.get(), equalTo(true));
    }

    @Test
    void consumeSimpleStream() throws Exception {
        initChunkedStream(smallBuff, of(10, 0), of(10, 0));
        toSource(pub).subscribe(sub1);
        sub1.awaitSubscription().request(1); // smallBuff
        assertThat(sub1.takeOnNext(), is(smallBuff));
        sub1.awaitSubscription().request(1); // read EOF
        sub1.awaitOnComplete();
        verify(inputStream).close();
    }

    @Test
    void multiRequests() throws Exception {
        initChunkedStream(smallBuff, of(8, 2, 0), of(8, 2, 0));
        byte[] first = new byte[8];
        arraycopy(smallBuff, 0, first, 0, 8);
        byte[] second = new byte[2];
        arraycopy(smallBuff, 8, second, 0, 2);

        toSource(pub).subscribe(sub1);
        sub1.awaitSubscription().request(1);
        assertThat(sub1.takeOnNext(), is(first));
        sub1.awaitSubscription().request(1);
        assertThat(sub1.takeOnNext(), is(second));
        sub1.awaitSubscription().request(1);
        // read EOF
        sub1.awaitOnComplete();
        verify(inputStream).close();
    }

    @Test
    void completeStreamIfEOFObservedDuringReadFromOverEstimatedAvailability() throws Throwable {
        initChunkedStream(smallBuff, ofAll(100), of(10, 0)); // only has 10 items

        byte[][] items = {
                new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
        };

        verifySuccess(items);
    }

    @Test
    void readsAllBytesWhenAvailableNotImplemented() throws Throwable {
        // constrain publisher to 10 byte chunks with no data availability to enforce inner loops until buffer drained
        initChunkedStream(bigBuff, ofAll(0), ofAll(10));

        // expect single emitted item
        // [ 0,  1,  2,  3,  4,  5,  6,  7,  8,  9,
        //  10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        //  20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
        //  30, 31, 32, 33, 34, 35, 36]

        byte[][] items = chunked(bigBuff.length, bigBuff.length);
        verifySuccess(items);
    }

    @ParameterizedTest(name = "{displayName} [{index}] readChunkSize={0}")
    @ValueSource(ints = {4, 1024})
    void doNotFailOnInputStreamWithBrokenAvailableCall(int readChunkSize) throws Throwable {
        // We use double "0, 0" because FromInputStreamPublisher does two calls to available() now. For this test, both
        // calls to "broken" available() should consistently return `0`.
        initChunkedStream(bigBuff, of(3, 0, 0, 4, 0, 0, 5, 0, 0, 2, 0, 0,    0, 0,       4, 0),
                                   of(3, 7,    4, 4,    5, 2, 2, 2, 1, 2, 1, 1, 1, 1, 1, 4, 0));
        pub = fromInputStream(inputStream, readChunkSize);

        if (readChunkSize > bigBuff.length) {
            byte[][] items = {
                    new byte[]{0, 1, 2},
                    // avail == 0 -> override to readChunkSize
                    new byte[]{3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26,
                            27, 28, 29, 30, 31, 32, 33, 34, 35, 36},
            };
            verifySuccess(items);
        } else {
            byte[][] items = {
                    // available < readChunkSize
                    new byte[]{0, 1, 2},
                    // available == 0 -> override to readChunkSize, actual chunk > readChunkSize
                    new byte[]{3, 4, 5, 6},
                    // available == readChunkSize
                    new byte[]{7, 8, 9, 10},
                    // available == 0 -> override to readChunkSize, actual chunk == readChunkSize
                    new byte[]{11, 12, 13, 14},
                    // available > readChunkSize -> limit by readChunkSize
                    new byte[]{15, 16, 17, 18},
                    // available == 0 -> override to readChunkSize, actual chunk < readChunkSize -> read twice
                    new byte[]{19, 20, 21, 22},
                    // available < readChunkSize
                    new byte[]{23, 24},
                    // available == 0 -> override to readChunkSize, actual chunk < readChunkSize -> read 3 times
                    new byte[]{25, 26, 27, 28},
                    // available == 0 -> override to readChunkSize, actual chunk < readChunkSize -> read 4 times
                    new byte[]{29, 30, 31, 32},
                    // available == readChunkSize
                    new byte[]{33, 34, 35, 36},
            };
            verifySuccess(items);
        }
    }

    @Test
    void singleReadTriggersMoreAvailability() throws Throwable {
        // We simulate a case when a single stream.read() triggers a read of a larger chunk and then the next call to
        // available() returns "chunk - 1". To accommodate mock behavior, if the 3rd in a row call to available()
        // returns a non zero value, we should return a chunk value of "chunks[idx - 1] - number of read bytes".
        initChunkedStream(bigBuff, of(0, 1, 0, 7, 0, 8, 1,  0, 17, 10, 2, 0),
                                   of(2,    8,    9,    1, 18,     10, 2, 0));
        pub = fromInputStream(inputStream, 8);

        byte[][] items = {
                // available < readChunkSize
                new byte[]{0, 1},
                // available == readChunkSize
                new byte[]{2, 3, 4, 5, 6, 7, 8, 9},
                // available > readChunkSize -> limit by readChunkSize
                new byte[]{10, 11, 12, 13, 14, 15, 16, 17},
                // available == 1 - unread remaining from the previous chunk of 9
                new byte[]{18},
                // available > 2x readChunkSize -> limit by readChunkSize
                new byte[]{19, 20, 21, 22, 23, 24, 25, 26},
                // available == 10 > readChunkSize - unread remaining from the previous chunk of 18
                new byte[]{27, 28, 29, 30, 31, 32, 33, 34},
                // available == 2 -> unread remaining from the previous chunk of 18
                new byte[]{35, 36},
        };
        verifySuccess(items);
    }

    @ParameterizedTest(name = "{displayName} [{index}] chunkSize={0}")
    @ValueSource(ints = {3, 5, 7})
    void readChunkSizeRespectedWhenAvailableNotImplemented(int chunkSize) throws Throwable {
        initChunkedStream(bigBuff, ofAll(0), ofAll(chunkSize));
        int readChunkSize = 5;
        pub = fromInputStream(inputStream, readChunkSize);

        // expect 8 emitted items
        // [ 0,  1,  2,  3,  4]
        // [ 5,  6,  7,  8,  9]
        // [10, 11, 12, 13, 14]
        // [15, 16, 17, 18, 19]
        // [20, 21, 22, 23, 24]
        // [25, 26, 27, 28, 29]
        // [30, 31, 32, 33, 34]
        // [35, 36]

        byte[][] items = chunked(bigBuff.length, readChunkSize);
        verifySuccess(items);
    }

    @Test
    void expandBufferOnIncreasingAvailability() throws Throwable {
        initChunkedStream(bigBuff, of(3, 2, 15, 15, 10, 0),
                                   of(3, 2, 15, 15, 2, 0));

        byte[][] items = {
                new byte[]{0, 1, 2},
                new byte[]{3, 4},
                new byte[]{5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19},
                new byte[]{20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34},
                new byte[]{35, 36},
        };

        verifySuccess(items);
    }

    @Test
    void keepReadingWhenAvailabilityPermits() throws Throwable {
        // constrain publisher to 10 byte chunks with full data availability to enforce inner loops until buffer drained
        initChunkedStream(bigBuff, ofAll(100), ofAll(10));

        // expect single emitted item
        // [ 0,  1,  2,  3,  4,  5,  6,  7,  8,  9,
        //  10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        //  20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
        //  30, 31, 32, 33, 34, 35, 36]

        byte[][] items = chunked(bigBuff.length, bigBuff.length);
        verifySuccess(items);
    }

    @ParameterizedTest(name = "{displayName} [{index}] chunkSize={0}")
    @ValueSource(ints = {3, 5, 7})
    void repeatedReadingWhenAvailabilityRunsOut(int chunkSize) throws Throwable {
        // constrain publisher to chunkSize byte chunks with only 5 byte availability per chunk to enforce multiple
        // outer loops simulating multiple calls to IS.available()
        initChunkedStream(bigBuff, ofAll(5), ofAll(chunkSize)); // chunkSize byte chunks per available() call

        // expect 8 emitted items
        // [ 0,  1,  2,  3,  4]
        // [ 5,  6,  7,  8,  9]
        // [10, 11, 12, 13, 14]
        // [15, 16, 17, 18, 19]
        // [20, 21, 22, 23, 24]
        // [25, 26, 27, 28, 29]
        // [30, 31, 32, 33, 34]
        // [35, 36]

        byte[][] items = chunked(bigBuff.length, 5);
        verifySuccess(items);
    }

    private IntStream ofAll(int i) {
        return generate(() -> i);
    }

    private void initEmptyStream() throws IOException {
        when(inputStream.available()).thenReturn(0);
        when(inputStream.read()).thenReturn(-1);
        when(inputStream.read(any(), anyInt(), anyInt())).thenReturn(-1);
    }

    /**
     * Emit data in chunks by predefined availability and chunk sizes or remaining data in buffer, whichever is smaller.
     * @param data buffer to emit
     * @param avails stream of availability values
     * @param chunks stream of chunk sizes
     */
    private void initChunkedStream(final byte[] data, final IntStream avails, final IntStream chunks) {
        AtomicInteger readIdx = new AtomicInteger();
        OfInt availSizes = avails.iterator();
        OfInt chunkSizes = chunks.iterator();
        AtomicBoolean readOneByte = new AtomicBoolean();
        try {
            when(inputStream.available()).then(inv -> availSizes.nextInt());
            when(inputStream.read()).then(inv -> {
                if (data.length == readIdx.get()) {
                    return -1;
                }
                readOneByte.set(true);
                return (int) data[readIdx.getAndIncrement()];
            });
            when(inputStream.read(any(), anyInt(), anyInt())).then(inv -> {
                byte[] b = inv.getArgument(0);
                int pos = inv.getArgument(1);
                int len = inv.getArgument(2);
                // subtract 1 byte from the next chunk if a single byte was already read
                final int chunkSize = chunkSizes.nextInt() - (readOneByte.getAndSet(false) ? 1 : 0);
                int read = min(min(len, data.length - readIdx.get()), chunkSize);
                if (read == 0) {
                    return data.length == readIdx.get() ? -1 : 0;
                }
                arraycopy(data, readIdx.get(), b, pos, read);
                readIdx.addAndGet(read);
                return read;
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // generate chunks to verify emitted data, eg: 35 items with chunkSize 10 results in 4 chunks
    // [ 0,  1,  2,  3,  4,  5,  6,  7,  8,  9]
    // [10, 11, 12, 13, 14, 15, 16, 17, 18, 19]
    // [20, 21, 22, 23, 24, 25, 26, 27, 28, 29]
    // [30, 31, 32, 33, 34]
    private byte[][] chunked(final int items, final int chunkSize) {
        byte[][] chunks = new byte[(int) ceil((float) items / (float) chunkSize)][];
        for (byte i = 0; i < items; i++) {
            int batch = i / chunkSize;
            if (i % chunkSize == 0) {
                chunks[batch] = new byte[min(items - i, chunkSize)];
            }
            chunks[batch][i % chunkSize] = i;
        }
        return chunks;
    }

    @Test
    void assertChunkedUtil() {
        assertThat(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, equalTo(init0toN(10)));
        assertThat(new byte[][]{
                new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
                new byte[]{10, 11, 12, 13, 14, 15, 16, 17, 18, 19},
                new byte[]{20, 21, 22, 23, 24, 25, 26, 27, 28, 29},
                new byte[]{30, 31, 32, 33, 34, 35, 36},
        }, equalTo(chunked(37, 10)));
    }

    /**
     * initialize {@code byte[]} with {@code [0, .. , n-1]}.
     */
    private byte[] init0toN(final int n) {
        return chunked(n, n)[0];
    }

    /**
     * The mockito {@link TestPublisherSubscriber} gets confused about verifying multiple emitted {@code byte[]}.
     * <p>
     * This is equivalent to:
     * <pre>{@code
     * sub1.subscribe(pub).request(MAX_VALUE);
     * sub1.verifyItems(items);
     * sub1.verifySuccess();
     * }</pre>
     * @param items the batches of {@code byte[]} that should have been emitted in order
     * @throws Throwable fails the test
     */
    private void verifySuccess(final byte[][] items) throws Throwable {

        AtomicBoolean complete = new AtomicBoolean();
        AtomicReference<Throwable> error = new AtomicReference<>();

        toSource(pub).subscribe(new Subscriber<byte[]>() {
            int batch;

            @Override
            public void onSubscribe(final Subscription s) {
                s.request(MAX_VALUE);
            }

            @Override
            public void onNext(final byte[] b) {
                assertThat(b, equalTo(items[batch++]));
            }

            @Override
            public void onError(final Throwable t) {
                error.set(t);
            }

            @Override
            public void onComplete() {
                complete.set(true);
            }
        });

        if (error.get() != null) {
            throw error.get();
        }
        assertThat(complete.get(), equalTo(true));
    }

    @Test
    void dontFailWhenInputStreamAvailableExceedsVmArraySizeLimit() throws Throwable {
        when(inputStream.available()).thenReturn(MAX_VALUE);
        when(inputStream.read(any(), anyInt(), anyInt())).thenReturn(-1);
        toSource(pub).subscribe(sub1);
        sub1.awaitSubscription().request(1);
        sub1.awaitOnComplete();
    }
}
