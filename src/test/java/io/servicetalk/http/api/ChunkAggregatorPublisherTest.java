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

import io.servicetalk.buffer.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpPayloadChunks.AggregatingChunk;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.error;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpPayloadChunks.newAggregatingChunk;
import static io.servicetalk.http.api.HttpPayloadChunks.newLastPayloadChunk;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static java.nio.charset.Charset.defaultCharset;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.rules.ExpectedException.none;

public class ChunkAggregatorPublisherTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expected = none();

    @Test
    public void emptyStream() throws Exception {
        LastHttpPayloadChunk lastChunk = aggregate();
        verifyAggregatedChunk(lastChunk);
    }

    @Test
    public void errorStream() throws Exception {
        expected.expect(instanceOf(ExecutionException.class));
        expected.expectCause(sameInstance(DELIBERATE_EXCEPTION));
        aggregate(error(DELIBERATE_EXCEPTION, immediate()));
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
        Publisher<HttpPayloadChunk> chunks =
                just(newPayloadChunk(data), immediate()).concatWith(error(DELIBERATE_EXCEPTION, immediate()));
        expected.expect(instanceOf(ExecutionException.class));
        expected.expectCause(sameInstance(DELIBERATE_EXCEPTION));
        aggregate(chunks);
    }

    @Test
    public void addHeadersBeforeComplete() {
        AggregatingChunk aggregatingChunk = newAggregatingChunk(DEFAULT_ALLOCATOR);
        HttpHeaders trailers = aggregatingChunk.getTrailers();
        assertThat("Different trailers instance.", trailers, sameInstance(aggregatingChunk.getTrailers()));
        trailers.add("foo", "bar");
        assertThat("Unexpected trailer.", aggregatingChunk.getTrailers().get("foo"), equalTo("bar"));
        HttpHeaders trailersInChunk = DefaultHttpHeadersFactory.INSTANCE.newTrailers();
        trailersInChunk.add("foo1", "bar1");
        aggregatingChunk.addChunk(newLastPayloadChunk(DEFAULT_ALLOCATOR.fromAscii("Hello"), trailersInChunk));
        assertThat("Unexpected trailer.", aggregatingChunk.getTrailers().get("foo"), equalTo("bar"));
        assertThat("Unexpected trailer.", aggregatingChunk.getTrailers().get("foo1"), equalTo("bar1"));
    }

    private LastHttpPayloadChunk aggregate(HttpPayloadChunk... chunks) throws Exception {
        return aggregate(from(immediate(), chunks));
    }

    private LastHttpPayloadChunk aggregate(Publisher<HttpPayloadChunk> chunks) throws Exception {
        Single<LastHttpPayloadChunk> aggregator = HttpPayloadChunks.aggregateChunks(chunks, DEFAULT_ALLOCATOR);
        LastHttpPayloadChunk aggregated = awaitIndefinitely(aggregator);
        assert aggregated != null : "Aggregated chunk can not be null.";
        return aggregated;
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
