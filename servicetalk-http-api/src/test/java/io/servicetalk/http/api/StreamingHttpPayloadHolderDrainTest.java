/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.PublisherSource.Processor;
import io.servicetalk.concurrent.api.Processors;
import io.servicetalk.concurrent.api.Publisher;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.DefaultPayloadInfo.forUserCreated;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class StreamingHttpPayloadHolderDrainTest {
    private static final HttpHeadersFactory H_FACTORY = DefaultHttpHeadersFactory.INSTANCE;
    private static final BufferAllocator ALLOCATOR = DEFAULT_ALLOCATOR;

    @Test
    void resubscribeDrainOriginalSourceNotReSubscribable() throws Exception {
        Processor<Buffer, Buffer> processor = Processors.newPublisherProcessor();
        processor.onNext(ALLOCATOR.fromAscii("first"));
        processor.onComplete();
        resubscribeDrainOriginalSource(fromSource(processor));
    }

    @Test
    void resubscribeDrainOriginalSourceReSubscribable() throws Exception {
        resubscribeDrainOriginalSource(from(ALLOCATOR.fromAscii("first")));
    }

    void resubscribeDrainOriginalSource(Publisher<?> payload) throws Exception {
        AtomicInteger completeCount = new AtomicInteger();
        StreamingHttpPayloadHolder holder = new StreamingHttpPayloadHolder(H_FACTORY.newHeaders(), ALLOCATOR,
                payload.afterFinally(completeCount::incrementAndGet), forUserCreated(), H_FACTORY);
        holder.payloadBody(from(ALLOCATOR.fromAscii("second")));
        // Subscribe twice and verify the results are the same both times (from supports multiple subscribes), and that
        // the original payload has been subscribed to and consumed.
        verifyCollectionSecond(holder.payloadBody().toFuture().get());
        verifyCollectionSecond(holder.payloadBody().toFuture().get());
        assertThat(completeCount.get(), is(1));
    }

    private static void verifyCollectionSecond(Collection<Buffer> buffers) {
        assertThat(buffers, hasSize(1));
        assertThat(buffers.iterator().next().toString(US_ASCII), is("second"));
    }
}
