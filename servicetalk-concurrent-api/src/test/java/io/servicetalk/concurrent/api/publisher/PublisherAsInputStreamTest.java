/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Arrays.copyOfRange;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PublisherAsInputStreamTest {

    private final TestPublisher<String> publisher = new TestPublisher<>();

    @Test
    void streamEmitsAllDataInSingleRead() throws IOException {
        Character[] src = {'1', '2', '3', '4'};
        InputStream stream = from(src).toInputStream(c -> new byte[]{(byte) c.charValue()});
        byte[] data = new byte[4];
        int read = stream.read(data, 0, 4);
        assertThat("Unexpected number of bytes read.", read, is(4));
        assertThat("Unexpected bytes read.", bytesToCharArray(data, read), equalTo(src));
        assertThat("Bytes read after complete.", stream.read(), is(-1));
    }

    @Test
    void streamEmitsAllDataInMultipleReads() throws IOException {
        Character[] src = {'1', '2', '3', '4'};
        InputStream stream = from(src).toInputStream(c -> new byte[]{(byte) c.charValue()});
        byte[] data = new byte[2];

        int read = stream.read(data, 0, 2);
        assertThat("Unexpected number of bytes read.", read, is(2));
        assertThat("Unexpected bytes read.", bytesToCharArray(data, 2),
                equalTo(copyOfRange(src, 0, 2)));
        read = stream.read(data, 0, 2);
        assertThat("Unexpected number of bytes read.", read, is(2));
        assertThat("Unexpected bytes read.", bytesToCharArray(data, 2),
                equalTo(copyOfRange(src, 2, 4)));

        assertThat("Bytes read after complete.", stream.read(), is(-1));
    }

    @Test
    void incrementallyFillAnArray() throws IOException {
        Character[] src = {'1', '2', '3', '4'};
        InputStream stream = from(src).toInputStream(c -> new byte[]{(byte) c.charValue()});
        byte[] data = new byte[4];

        int read = stream.read(data, 0, 2);
        assertThat("Unexpected number of bytes read.", read, is(2));
        assertThat("Unexpected bytes read.", bytesToCharArray(data, 2),
                equalTo(copyOfRange(src, 0, 2)));
        read = stream.read(data, 2, 2);
        assertThat("Unexpected number of bytes read.", read, is(2));
        assertThat("Unexpected bytes read.", bytesToCharArray(data, 4), equalTo(src));

        assertThat("Bytes read after complete.", stream.read(), is(-1));
    }

    @Test
    void readRequestMoreThanDataBuffer() throws IOException {
        Character[] src = {'1', '2', '3', '4'};
        InputStream stream = from(src).toInputStream(c -> new byte[]{(byte) c.charValue()});
        byte[] data = new byte[16];
        int read = stream.read(data, 0, 16);
        assertThat("Unexpected number of bytes read.", read, is(4));
        assertThat("Unexpected bytes read.", bytesToCharArray(data, read), equalTo(src));
        assertThat("Bytes read after complete.", stream.read(), is(-1));
    }

    @Test
    void readRequestLessThanDataBuffer() throws IOException {
        String src = "1234";
        InputStream stream = from(src).toInputStream(str -> str.getBytes(US_ASCII));
        byte[] data = new byte[2];
        int read = stream.read(data, 0, 2);
        assertThat("Unexpected number of bytes read.", read, is(2));
        assertThat("Unexpected bytes read.", bytesToCharArray(data, read), equalTo(new Character[]{'1', '2'}));
    }

    @Test
    void largerSizeItems() throws IOException {
        InputStream stream = from("123", "45678")
                .toInputStream(str -> str.getBytes(US_ASCII));
        byte[] data = new byte[4];
        int read = stream.read(data, 0, 4);
        assertThat("Unexpected number of bytes read.", read, is(4));
        assertThat("Unexpected bytes read.", bytesToCharArray(data, read),
                equalTo(new Character[]{'1', '2', '3', '4'}));
    }

    @Test
    void streamErrorShouldBeEmittedPostData() throws IOException {
        DeliberateException de = new DeliberateException();
        Character[] src = {'1', '2', '3', '4'};
        InputStream stream = from(src).concat(Completable.failed(de))
                .toInputStream(c -> new byte[]{(byte) c.charValue()});
        byte[] data = new byte[4];

        try {
            stream.read(data, 0, 4);
        } catch (DeliberateException e) {
            assertThat("Unexpected exception.", e, sameInstance(de));
            assertThat("Unexpected bytes read.", bytesToCharArray(data, 4), equalTo(src));
        }
    }

    @Test
    void closeThenReadShouldBeInvalid() throws IOException {
        Character[] src = {'1', '2', '3', '4'};
        InputStream stream = from(src).toInputStream(c -> new byte[]{(byte) c.charValue()});
        stream.close();
        assertThrows(IOException.class, () -> stream.read());
    }

    @Test
    void singleByteRead() throws IOException {
        Character[] src = {'1'};
        InputStream stream = from(src).toInputStream(c -> new byte[]{(byte) c.charValue()});
        int read = stream.read();
        assertThat("Unexpected bytes read.", (char) read, equalTo('1'));
        assertThat("Bytes read after complete.", stream.read(), is(-1));
    }

    @Test
    void singleByteReadWithEmptyIterable() throws IOException {
        Character[] src = {'1'};
        InputStream stream = from(src).toInputStream(c -> new byte[0]);
        assertThat("Unexpected bytes read.", stream.read(), is(-1));
        // We have left over state across reads, do a second read to make sure we do not have such state.
        // Since, we only ever emit 1 item from the source, we are sure that no other state will change after this read.
        assertThat("Unexpected bytes from second read.", stream.read(), is(-1));
    }

    @Test
    void readWithEmptyIterable() throws IOException {
        Character[] src = {'1'};
        InputStream stream = from(src).toInputStream(c -> new byte[0]);
        byte[] r = new byte[1];
        assertThat("Unexpected bytes read.", stream.read(r, 0, 1), is(-1));
        // We have left over state across reads, do a second read to make sure we do not have such state.
        // Since, we only ever emit 1 item from the source, we are sure that no other state will change after this read.
        assertThat("Unexpected bytes from second read.", stream.read(r), is(-1));
    }

    @Test
    void zeroLengthReadShouldBeValid() throws IOException {
        Character[] src = {'1'};
        InputStream stream = from(src).toInputStream(c -> new byte[]{(byte) c.charValue()});
        byte[] data = new byte[0];
        int read = stream.read(data, 0, 0);
        assertThat("Unexpected bytes read.", read, equalTo(0));
        assertThat("Bytes read after complete.", (char) stream.read(), equalTo('1'));
    }

    @Test
    void checkAvailableReturnsCorrectlyWithPrefetch() throws IOException {
        TestPublisher<String> testPublisher = new TestPublisher<>();
        InputStream stream = testPublisher.toInputStream(str -> str.getBytes(US_ASCII));
        assertThat("Unexpected available return type.", stream.available(), is(0));
        testPublisher.onNext("1234");
        assertThat("Unexpected available return type.", stream.available(), is(0));
        byte[] data = new byte[2];
        int read = stream.read(data, 0, 2);
        assertThat("Unexpected number of bytes read.", read, is(2));
        assertThat("Unexpected bytes read.", bytesToCharArray(data, read), equalTo(new Character[]{'1', '2'}));
        assertThat("Unexpected available return type.", stream.available(), is(2));
    }

    @Test
    void completionAndEmptyReadShouldIndicateEOF() throws IOException {
        InputStream stream = from(Publisher.empty()).toInputStream(obj -> new byte[0]);
        byte[] data = new byte[32];
        int read = stream.read(data, 0, 32);
        assertThat("Unexpected bytes read.", read, equalTo(-1));
    }

    @Test
    void testEmptyIteratorValue() throws IOException {
        testNullAndEmptyIteratorValue(new byte[0]);
    }

    @Test
    void testNullIteratorValue() throws IOException {
        testNullAndEmptyIteratorValue(null);
    }

    private void testNullAndEmptyIteratorValue(@Nullable byte[] emptyConversionValue) throws IOException {
        String realStringData = "hello!";
        final int midWayPoint = 3;
        byte[] data = new byte[realStringData.length()];
        InputStream is = publisher.toInputStream(str ->
                str == null ? emptyConversionValue : str.getBytes(US_ASCII));

        // Split the real data up into 2 chunks and send null/empty in between
        publisher.onNext((String[]) null);
        publisher.onNext("");
        publisher.onNext(realStringData.substring(0, midWayPoint));
        assertThat(is.read(data, 0, midWayPoint), is(midWayPoint));

        // send the second chunk
        publisher.onNext((String[]) null);
        publisher.onNext("");
        publisher.onNext(realStringData.substring(midWayPoint));
        final int len = realStringData.length() - midWayPoint;
        assertThat(is.read(data, midWayPoint, len), is(len));

        assertThat(data, is(realStringData.getBytes(US_ASCII)));
    }

    private Character[] bytesToCharArray(final byte[] data, final int length) {
        Character[] toReturn = new Character[length];
        for (int i = 0; i < length; i++) {
            toReturn[i] = (char) data[i];
        }
        return toReturn;
    }
}
