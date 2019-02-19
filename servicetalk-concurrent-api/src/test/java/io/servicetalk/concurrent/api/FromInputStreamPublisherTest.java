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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.io.InputStream;
import java.util.PrimitiveIterator.OfInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.Math.ceil;
import static java.lang.Math.min;
import static java.lang.System.arraycopy;
import static java.util.stream.IntStream.generate;
import static java.util.stream.IntStream.of;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class FromInputStreamPublisherTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final MockedSubscriberRule<byte[]> sub1 = new MockedSubscriberRule();

    @Rule
    public final MockedSubscriberRule<byte[]> sub2 = new MockedSubscriberRule();

    private final byte[] smallBuff = init0toN(10);
    private final byte[] bigBuff = init0toN(37);

    private InputStream is;
    private Publisher<byte[]> pub;

    @Before
    public void setup() {
        is = mock(InputStream.class);
        pub = new FromInputStreamPublisher(is);
    }

    @Test
    public void noDuplicateSubscription() throws Exception {
        initEmptyStream();

        sub1.subscribe(pub);

        sub2.subscribe(pub);
        sub2.verifyFailure(DuplicateSubscribeException.class);

        sub1.request(1).verifySuccess();
    }

    @Test
    public void noDuplicateSubscriptionAfterError() throws Exception {
        when(is.available()).thenThrow(IOException.class);

        sub1.subscribe(pub);
        sub1.request(1).verifyFailure(IOException.class);

        sub2.subscribe(pub);
        sub2.verifyFailure(DuplicateSubscribeException.class);
    }

    @Test
    public void noDuplicateSubscriptionAfterComplete() throws Exception {
        initEmptyStream();

        sub1.subscribe(pub);
        sub1.request(1).verifySuccess();

        sub2.subscribe(pub);
        sub2.verifyFailure(DuplicateSubscribeException.class);
    }

    @Test
    public void closeStreamOnCancelByDefault() throws Exception {
        sub1.subscribe(pub);
        sub1.cancel();
        verify(is).close();
    }

    @Test
    public void streamClosedAndErrorOnInvalidReqN() throws Exception {
        sub1.subscribe(pub);
        sub1.request(-1);
        sub1.verifyFailure(IllegalArgumentException.class);

        verify(is).close();
    }

    @Test
    public void streamClosedAndErrorOnInvalidReqNAndValidReqN() throws Exception {
        initChunkedStream(smallBuff, of(10, 0), of(10, 0));

        sub1.subscribe(pub);
        sub1.request(-1);
        sub1.request(10);

        sub1.verifyFailure(IllegalArgumentException.class);
        verify(is).close();
    }

    @Test
    public void streamClosedAndErrorOnDoubleInvalidReqN() throws Exception {
        initChunkedStream(smallBuff, of(10, 0), of(10, 0));

        sub1.subscribe(pub);
        sub1.request(-1);
        sub1.request(-1);

        sub1.verifyFailure(IllegalArgumentException.class);
        verify(is).close();
    }

    @Test
    public void streamClosedAndErrorOnAvailableIOError() throws Exception {
        when(is.available()).thenThrow(IOException.class);
        sub1.subscribe(pub);
        sub1.request(1).verifyFailure(IOException.class);
        verify(is).close();
    }

    @Test
    public void streamClosedAndErrorOnReadIOError() throws Exception {
        when(is.available()).thenReturn(10);
        when(is.read(any(), anyInt(), anyInt())).thenThrow(IOException.class);

        sub1.subscribe(pub);
        sub1.request(1).verifyFailure(IOException.class);
        verify(is).close();
    }

    @Test
    public void streamClosedAndErrorOnDeliveryError() throws Exception {
        initChunkedStream(smallBuff, of(10), of(10));

        Subscriber sub = mock(Subscriber.class);

        doAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.request(1);
            return null;
        }).when(sub).onSubscribe(any());
        doThrow(DELIBERATE_EXCEPTION).when(sub).onNext(any());

        pub.subscribe(sub);

        verify(is).close();
        verify(sub, never()).onComplete();
        verify(sub).onError(DELIBERATE_EXCEPTION);
    }

    @Test
    public void streamClosedAndErrorOnDeliveryErrorOnce() throws Exception {
        initChunkedStream(smallBuff, ofAll(10), ofAll(10));

        Subscriber sub = mock(Subscriber.class);

        AtomicReference<Subscription> subRef = new AtomicReference<>();
        doAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            subRef.set(s);
            s.request(1);
            return null;
        }).when(sub).onSubscribe(any());
        doThrow(DELIBERATE_EXCEPTION).when(sub).onNext(any());

        pub.subscribe(sub);
        // triggers another delivery + failure, to ensure we only observe a single terminal event
        subRef.get().request(1);

        verify(sub, never()).onComplete();
        verify(sub).onError(DELIBERATE_EXCEPTION);
        verify(is).close();
    }

    @Test
    public void streamCanceledShouldCloseOnce() throws Exception {
        initChunkedStream(smallBuff, ofAll(10), ofAll(10));

        Subscriber sub = mock(Subscriber.class);

        doAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.request(1);
            s.cancel();
            return null;
        }).when(sub).onSubscribe(any());
        doThrow(DELIBERATE_EXCEPTION).when(sub).onNext(any());

        pub.subscribe(sub);

        verify(sub, never()).onComplete();
        verify(sub).onError(DELIBERATE_EXCEPTION);
        verify(is).close();
    }

    @Test
    public void breakReentrantRequestN() throws Throwable {

        AtomicInteger count = new AtomicInteger();
        AtomicBoolean complete = new AtomicBoolean();
        AtomicReference<Throwable> error = new AtomicReference<>();

        when(is.available()).thenReturn(1);
        when(is.read(any(), anyInt(), anyInt())).then(inv -> {
            if (count.incrementAndGet() > 1_000) {
                return -1;
            }
            return 1;
        });

        pub.subscribe(new Subscriber<byte[]>() {
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
    public void consumeSimpleStream() throws Exception {
        initChunkedStream(smallBuff, of(10, 0), of(10, 0));
        sub1.subscribe(pub)
                .request(1) // smallBuff
                .verifyItems(smallBuff)
                .request(1) // read EOF
                .verifySuccess();
    }

    @Test
    public void multiRequests() {
        initChunkedStream(smallBuff, of(8, 2, 0), of(8, 2, 0));
        byte[] first = new byte[8];
        arraycopy(smallBuff, 0, first, 0, 8);
        byte[] second = new byte[2];
        arraycopy(smallBuff, 8, second, 0, 2);

        sub1.subscribe(pub);
        sub1.request(1).verifyItems(first)
                .request(1).verifyItems(second)
                .request(1).verifySuccess(); // read EOF
    }

    @Test
    public void completeStreamIfEOFObservedDuringReadFromOverEstimatedAvailability() throws Throwable {
        initChunkedStream(smallBuff, ofAll(100), of(10, 0)); // only has 10 items

        byte[][] items = new byte[][]{
                new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
        };

        verifySuccess(items);
    }

    @Test
    public void dontFailOnInputStreamWithBrokenAvailableCall() throws Throwable {
        initChunkedStream(bigBuff, of(5, 0, 0, 10, 5, 5, 5, 5, 0),
                                   of(5, 1, 1, 10, 5, 5, 5, 5, 0));

        byte[][] items = new byte[][]{
                new byte[]{0, 1, 2, 3, 4},
                new byte[]{5}, // avail == 0 -> override to 1
                new byte[]{6}, // avail == 0 -> override to 1
                new byte[]{7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                new byte[]{17, 18, 19, 20, 21},
                new byte[]{22, 23, 24, 25, 26},
                new byte[]{27, 28, 29, 30, 31},
                new byte[]{32, 33, 34, 35, 36},
        };

        verifySuccess(items);
    }

    @Test
    public void expandBufferOnIncreasingAvailability() throws Throwable {
        initChunkedStream(bigBuff, of(3, 2, 15, 15, 10, 0),
                                   of(3, 2, 15, 15, 2, 0));

        byte[][] items = new byte[][]{
                new byte[]{0, 1, 2},
                new byte[]{3, 4},
                new byte[]{5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19},
                new byte[]{20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34},
                new byte[]{35, 36},
        };

        verifySuccess(items);
    }

    @Test
    public void keepReadingWhenAvailabilityPermits() throws Throwable {
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

    @Test
    public void repeatedReadingWhenAvailabilityRunsOut() throws Throwable {
        // constrain publisher to 10 byte chunks with only 5 byte availability per chunk to enforce multiple outer loops
        // simulating multiple calls to IS.available()
        initChunkedStream(bigBuff, ofAll(5), ofAll(5)); // 5 byte chunks per available() call

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
        when(is.available()).thenReturn(0);
        when(is.read(any(), anyInt(), anyInt())).thenReturn(-1);
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
        try {
            when(is.available()).then(inv -> availSizes.nextInt());
            when(is.read(any(), anyInt(), anyInt())).then(inv -> {
                byte[] b = inv.getArgument(0);
                int pos = inv.getArgument(1);
                int len = inv.getArgument(2);
                int read = min(min(len, data.length - readIdx.get()), chunkSizes.nextInt());
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
    public void assertChunkedUtil() {
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
     * The mockito {@link MockedSubscriberRule} gets confused about verifying multiple emitted {@code byte[]}.
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

        pub.subscribe(new Subscriber<byte[]>() {
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
}
