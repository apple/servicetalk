/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.transport.netty.internal.CopyByteBufHandlerChannelInitializer.CopyByteBufHandler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledDirectByteBuf;
import io.netty.buffer.UnpooledHeapByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;

import static io.netty.buffer.ByteBufUtil.writeAscii;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class CopyByteBufHandlerTest {

    @Test
    public void doesNotAcceptPooledAllocator() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new CopyByteBufHandler(PooledByteBufAllocator.DEFAULT));
        assertThat(ex.getMessage(), equalTo("ByteBufAllocator must be unpooled"));
    }

    @Test
    public void acceptsUnpooledAllocator() {
        assertThat(new CopyByteBufHandler(UnpooledByteBufAllocator.DEFAULT), is(notNullValue()));
    }

    @Test
    public void doesNotProcessByteBufHolder() {
        CopyByteBufHandler handler = new CopyByteBufHandler(UnpooledByteBufAllocator.DEFAULT);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        ByteBuf buf = mock(ByteBuf.class);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                // Use DatagramPacket as a ByteBufHolder implementation:
                () -> handler.channelRead(ctx, new DatagramPacket(buf, mock(InetSocketAddress.class))));
        assertThat(ex.getMessage(), startsWith("Unexpected message type"));

        verify(ctx, never()).fireChannelRead(any());
        verify(buf).release();
    }

    @Test
    public void copiesAndReleasesPooledByteBuf() {
        UnpooledByteBufAllocator unpooledAllocator = UnpooledByteBufAllocator.DEFAULT;
        CopyByteBufHandler handler = new CopyByteBufHandler(unpooledAllocator);

        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        ArgumentCaptor<ByteBuf> valueCapture = ArgumentCaptor.forClass(ByteBuf.class);
        doReturn(ctx).when(ctx).fireChannelRead(valueCapture.capture());

        ByteBuf pooledBuf = PooledByteBufAllocator.DEFAULT.buffer(4);
        try {
            assertThat(writeAscii(pooledBuf, "test"), is(4));
            handler.channelRead(ctx, pooledBuf);
            assertThat(pooledBuf.refCnt(), is(0));

            ByteBuf unpooled = valueCapture.getValue();
            assertThat(unpooled.alloc(), is(unpooledAllocator));
            if (unpooled.isDirect()) {
                assertThat(unpooled, is(instanceOf(UnpooledDirectByteBuf.class)));
            } else {
                assertThat(unpooled, is(instanceOf(UnpooledHeapByteBuf.class)));
            }
            assertThat(unpooled.toString(US_ASCII), equalTo("test"));
        } finally {
            if (pooledBuf.refCnt() > 0) {
                pooledBuf.release();
            }
        }
    }
}
