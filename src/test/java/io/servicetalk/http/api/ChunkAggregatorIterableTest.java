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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpPayloadChunks.newLastPayloadChunk;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.rules.ExpectedException.none;

public class ChunkAggregatorIterableTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expected = none();

    @Test
    public void emptyStream() throws Exception {
        LastHttpPayloadChunk lastChunk = aggregate(emptyList());
        verifyAggregatedChunk(lastChunk);
    }

    @Test
    public void errorStream() throws Exception {
        expected.expect(sameInstance(DELIBERATE_EXCEPTION));
        addErrorAndAggregate();
    }

    @Test
    public void noLastHttpChunk() throws Exception {
        final Buffer data = DEFAULT_ALLOCATOR.fromAscii("Hello");
        HttpPayloadChunk chunk = newPayloadChunk(data);
        LastHttpPayloadChunk lastChunk = aggregate(chunk);

        verifyAggregatedChunk(lastChunk, data);
    }

    @Test
    public void onlyLastHttpChunkNoContent() throws Exception {
        HttpHeaders trailers = DefaultHttpHeadersFactory.INSTANCE.newEmptyTrailers();
        trailers.add("foo", "bar");
        LastHttpPayloadChunk chunk = newLastPayloadChunk(DEFAULT_ALLOCATOR.newBuffer(0), trailers);
        LastHttpPayloadChunk aggregated = aggregate(chunk);

        verifyAggregatedChunk(aggregated, trailers);
    }

    @Test
    public void onlyLastHttpChunk() throws Exception {
        final Buffer data = DEFAULT_ALLOCATOR.fromAscii("Hello");
        HttpHeaders trailers = DefaultHttpHeadersFactory.INSTANCE.newEmptyTrailers();
        trailers.add("foo", "bar");
        LastHttpPayloadChunk chunk = newLastPayloadChunk(data, trailers);
        LastHttpPayloadChunk aggregated = aggregate(chunk);

        verifyAggregatedChunk(aggregated, data, trailers);
    }

    @Test
    public void chunkAndLastChunkNoContent() throws Exception {
        final Buffer data = DEFAULT_ALLOCATOR.fromAscii("Hello");
        HttpHeaders trailers = DefaultHttpHeadersFactory.INSTANCE.newEmptyTrailers();
        HttpPayloadChunk chunk = newPayloadChunk(data);
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(DEFAULT_ALLOCATOR.newBuffer(0), trailers);
        LastHttpPayloadChunk aggregated = aggregate(chunk, lastChunk);

        verifyAggregatedChunk(aggregated, data, trailers);
    }

    @Test
    public void chunkAndLastChunkContent() throws Exception {
        final Buffer data = DEFAULT_ALLOCATOR.fromAscii("Hello");
        HttpHeaders trailers = DefaultHttpHeadersFactory.INSTANCE.newEmptyTrailers();
        HttpPayloadChunk chunk = newPayloadChunk(data.slice(0, 2));
        LastHttpPayloadChunk lastChunk = newLastPayloadChunk(data.slice(2, 3), trailers);
        LastHttpPayloadChunk aggregated = aggregate(chunk, lastChunk);

        verifyAggregatedChunk(aggregated, data, trailers);
    }

    @Test
    public void chunksAndThenError() throws Exception {
        final Buffer data = DEFAULT_ALLOCATOR.fromAscii("Hello");
        HttpPayloadChunk chunk = newPayloadChunk(data);
        expected.expect(sameInstance(DELIBERATE_EXCEPTION));
        addErrorAndAggregate(chunk);
    }

    private LastHttpPayloadChunk aggregate(HttpPayloadChunk... chunks) throws Exception {
        return aggregate(asList(chunks));
    }

    private void addErrorAndAggregate(HttpPayloadChunk... chunks) throws Exception {
        Iterable<HttpPayloadChunk> iterable = asList(chunks);
        aggregate(() -> new Iterator<HttpPayloadChunk>() {

            private final Iterator<HttpPayloadChunk> chunkIterator = iterable.iterator();
            private boolean errorThrown;

            @Override
            public boolean hasNext() {
                return chunkIterator.hasNext() || !errorThrown;
            }

            @Override
            public HttpPayloadChunk next() {
                if (chunkIterator.hasNext()) {
                    return chunkIterator.next();
                }
                if (errorThrown) {
                    throw new NoSuchElementException();
                }
                errorThrown = true;
                throw DELIBERATE_EXCEPTION;
            }
        });
    }

    private LastHttpPayloadChunk aggregate(Iterable<HttpPayloadChunk> chunks) throws Exception {
        return HttpPayloadChunks.aggregateChunks(chunks, DEFAULT_ALLOCATOR);
    }

    private void verifyAggregatedChunk(final LastHttpPayloadChunk chunk) {
        verifyAggregatedChunk(chunk, DEFAULT_ALLOCATOR.newBuffer(0));
    }

    private void verifyAggregatedChunk(final LastHttpPayloadChunk chunk, HttpHeaders expectedTrailers) {
        verifyAggregatedChunk(chunk, DEFAULT_ALLOCATOR.newBuffer(0), expectedTrailers);
    }

    private void verifyAggregatedChunk(final LastHttpPayloadChunk chunk, Buffer expectedData) {
        verifyAggregatedChunk(chunk, expectedData, DefaultHttpHeadersFactory.INSTANCE.newEmptyTrailers());
    }

    private void verifyAggregatedChunk(final LastHttpPayloadChunk chunk, Buffer expectedData,
                                       HttpHeaders expectedTrailers) {
        assertThat("Unexpected trailers.", chunk.getTrailers(), equalTo(expectedTrailers));
        assertThat("Unexpected content.", chunk.getContent(), is(notNullValue()));
        assertThat("Unexpected content readable bytes.", chunk.getContent().getReadableBytes(),
                is(expectedData.getReadableBytes()));
        assertThat("Unexpected content data.", chunk.getContent().toString(defaultCharset()),
                is(expectedData.toString(defaultCharset())));
    }
}
