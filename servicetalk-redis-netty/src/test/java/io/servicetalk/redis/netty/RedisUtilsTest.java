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
package io.servicetalk.redis.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.netty.BufferAllocators;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.redis.api.RedisData.BulkStringChunkImpl;
import io.servicetalk.redis.api.RedisData.CompleteBulkString;
import io.servicetalk.redis.api.RedisData.FirstBulkStringChunkImpl;
import io.servicetalk.redis.api.RedisData.RequestRedisData;
import io.servicetalk.redis.api.RedisData.SimpleString;
import io.servicetalk.redis.api.RedisProtocolSupport;
import io.servicetalk.redis.api.RedisRequest;

import io.netty.buffer.ByteBuf;
import org.junit.Test;

import java.util.function.Function;
import javax.annotation.Nonnull;

import static io.servicetalk.buffer.netty.BufferUtil.toByteBuf;
import static io.servicetalk.redis.netty.RedisUtils.encodeRequestContent;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

public class RedisUtilsTest {

    private BufferAllocator allocator = BufferAllocators.PREFER_HEAP_ALLOCATOR;

    @Test
    public void testBulkStringChunks() throws Exception {
        assertEncoded("$10\r\nabcdefghij\r\n", Publisher.from(
                new FirstBulkStringChunkImpl(buf("abcd"), 10),
                new BulkStringChunkImpl(buf("efghij"))));

        assertEncoded("$10\r\nabcdefghij\r\n", Publisher.from(
                new FirstBulkStringChunkImpl(buf("abcdefghi"), 10),
                new BulkStringChunkImpl(buf("j"))));

        assertEncoded("$10\r\nabcdefghij\r\n", Publisher.from(
                new FirstBulkStringChunkImpl(buf("abcd"), 10),
                new BulkStringChunkImpl(buf("e")),
                new BulkStringChunkImpl(buf("fg")),
                new BulkStringChunkImpl(buf("hi")),
                new BulkStringChunkImpl(buf("j"))));
    }

    @Test
    public void testFirstBulkStringChunk() throws Exception {
        assertEncoded("$6\r\nabcde", Publisher.from(new FirstBulkStringChunkImpl(buf("abcde"), 6)));
    }

    @Test
    public void testFirstBulkStringChunkWithFullBulkString() throws Exception {
        assertEncoded("$5\r\nabcde\r\n", Publisher.from(new FirstBulkStringChunkImpl(buf("abcde"), 5)));
    }

    @Test
    public void testCompleteBulkString() throws Exception {
        assertEncoded("$5\r\nabcde\r\n", Publisher.from(new CompleteBulkString(buf("abcde"))));
    }

    @Test
    public void testSimpleString() throws Exception {
        assertEncoded("+abcde\r\n", Publisher.from(new SimpleString("abcde")));
    }

    private void assertEncoded(final String expected, final Publisher<RequestRedisData> content) throws Exception {
        final String encodedResult = encodeRequestContent(newRequest(content), allocator)
                .reduce(() -> toByteBuf(allocator.newBuffer()), ByteBuf::writeBytes).toFuture().get().toString(UTF_8);
        assertEquals(expected, encodedResult);
    }

    @Nonnull
    private RedisRequest newRequest(final Publisher<RequestRedisData> content) {
        return new RedisRequest() {

            @Override
            public RedisProtocolSupport.Command command() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Publisher<RequestRedisData> content() {
                return content;
            }

            @Override
            public RedisRequest transformContent(final Function<Publisher<RequestRedisData>, Publisher<RequestRedisData>> transformer) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private Buffer buf(final CharSequence cs) {
        return allocator.fromUtf8(cs);
    }
}
