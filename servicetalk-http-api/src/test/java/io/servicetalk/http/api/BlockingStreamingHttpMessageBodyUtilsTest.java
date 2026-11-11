/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.BlockingStreamingHttpMessageBodyUtils.serializeMessageBody;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class BlockingStreamingHttpMessageBodyUtilsTest {

    private static final String ITERATOR_ID = "iterator-id";

    private final AtomicInteger iterators = new AtomicInteger();

    @Test
    void serializeMessageBodySupportsResubscribe() throws Exception {
        final Publisher<Object> body = serializedMessageBody();

        assertBodyWithTrailersFromIterator(body.toFuture().get(), 1);
        assertBodyWithTrailersFromIterator(body.toFuture().get(), 2);
    }

    @Test
    void serializeMessageBodySupportsOverlappingSubscribes() throws Exception {
        final Publisher<Object> body = serializedMessageBody();
        final TestPublisherSubscriber<Object> first = new TestPublisherSubscriber<>();
        toSource(body).subscribe(first);
        first.awaitSubscription().request(1);
        final List<Object> firstItems = new ArrayList<>(first.takeOnNext(1));

        assertBodyWithTrailersFromIterator(body.toFuture().get(), 2);

        first.awaitSubscription().request(Long.MAX_VALUE);
        firstItems.addAll(first.takeOnNext(2));
        first.awaitOnComplete();
        assertBodyWithTrailersFromIterator(firstItems, 1);
    }

    private Publisher<Object> serializedMessageBody() {
        final HttpMessageBodyIterable<String> messageBody = () -> new HttpMessageBodyIterator<String>() {
            private final int id = iterators.incrementAndGet();
            private final BlockingIterator<String> items = Publisher.from("Hello", "World").toIterable().iterator();

            @Override
            public HttpHeaders trailers() {
                return DefaultHttpHeadersFactory.INSTANCE.newTrailers().set(ITERATOR_ID, String.valueOf(id));
            }

            @Override
            public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
                return items.hasNext(timeout, unit);
            }

            @Nullable
            @Override
            public String next(final long timeout, final TimeUnit unit) throws TimeoutException {
                return items.next(timeout, unit);
            }

            @Override
            public boolean hasNext() {
                return items.hasNext();
            }

            @Nullable
            @Override
            public String next() {
                return items.next();
            }

            @Override
            public void close() throws Exception {
                items.close();
            }
        };
        return serializeMessageBody(messageBody, DefaultHttpHeadersFactory.INSTANCE.newHeaders(),
                appSerializerUtf8FixLen(), DEFAULT_ALLOCATOR);
    }

    private static void assertBodyWithTrailersFromIterator(final Collection<Object> items, final int iteratorId) {
        final List<Object> list = new ArrayList<>(items);
        assertThat(list, hasSize(3));
        assertThat(list.subList(0, 2), everyItem(instanceOf(Buffer.class)));
        assertThat(list.get(2), instanceOf(HttpHeaders.class));
        assertThat(((HttpHeaders) list.get(2)).get(ITERATOR_ID).toString(), is(String.valueOf(iteratorId)));
    }
}
