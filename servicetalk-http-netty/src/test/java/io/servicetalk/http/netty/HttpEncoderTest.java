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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

abstract class HttpEncoderTest<T extends HttpMetaData> {

    enum TransferEncoding {
        ContentLength,
        Chunked,
        Variable
    }

    abstract EmbeddedChannel newEmbeddedChannel();

    abstract T newMetaData(HttpHeaders headers);

    static void consumeEmptyBufferFromTrailers(EmbeddedChannel channel) {
        // Empty buffer is written when trailers are seen to indicate the end of the request
        ByteBuf byteBuf = channel.readOutbound();
        assertFalse(byteBuf.isReadable());
        byteBuf.release();
    }

    @Test
    void internalByteBufReleasedOnMetaDataError() {
        ByteBuf buf = mock(ByteBuf.class);
        ByteBufAllocator alloc = mock(ByteBufAllocator.class);
        when(alloc.directBuffer(anyInt())).thenReturn(buf);

        EmbeddedChannel channel = newEmbeddedChannel();
        channel.config().setAllocator(alloc);

        assertThrows(IndexOutOfBoundsException.class, () -> channel.writeOutbound(newMetaData(INSTANCE.newHeaders())));

        verify(alloc).directBuffer(anyInt());
        verify(buf).release();
    }

    @Test
    void internalByteBufReleasedOnTrailersError() {
        ByteBufAllocator alloc = mock(ByteBufAllocator.class);
        when(alloc.directBuffer(anyInt())).thenReturn(Unpooled.buffer());

        EmbeddedChannel channel = newEmbeddedChannel();
        channel.config().setAllocator(alloc);

        channel.writeOutbound(newMetaData(INSTANCE.newHeaders().add(TRANSFER_ENCODING, CHUNKED)));

        ByteBuf buf = mock(ByteBuf.class);
        when(buf.writeMedium(anyInt())).thenThrow(DELIBERATE_EXCEPTION);
        when(alloc.directBuffer(anyInt())).thenReturn(buf);
        assertThrows(DeliberateException.class,
                () -> channel.writeOutbound(INSTANCE.newTrailers().add("trailer-key", "trailer-value")));

        verify(alloc, times(2)).directBuffer(anyInt());
        verify(buf).release();
    }
}
