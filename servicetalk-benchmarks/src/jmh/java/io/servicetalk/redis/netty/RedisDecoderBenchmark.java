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
import io.servicetalk.buffer.netty.BufferAllocators;
import io.servicetalk.buffer.netty.BufferUtil;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.net.SocketAddress;

import static java.lang.String.valueOf;

/*
 * Benchmark of RedisDecoder for various lengths and numbers of string args, and various receive buffer sizes.
 */
@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.Throughput)
public class RedisDecoderBenchmark {

    @Param({"10", "100", "1000"})
    public int valCount;

    @Param({"10", "1000", "1000000"})
    public int valLen;

    @Param({"10", "100", "1000"})
    public int bufLength;

    int payloadLength;
    Buffer payload;
    private ChannelHandlerContext ctx;

    @Setup(Level.Iteration)
    public void setup(final Blackhole blackhole) {
        StringBuilder response = new StringBuilder(3 // "*\r\n"
                + (3 * valCount) // "$\r\n" for each valCount
                + valCount * valueOf(valLen).length()
                + valCount * (2 + valLen) // string length + "\r\n"
        ).append("*").append(valCount).append("\r\n");
        for (int i = 0; i < valCount; ++i) {
            response.append("$").append(valLen).append("\r\n")
                    .append(stringOfLength(valLen)).append("\r\n");
        }
        payload = BufferAllocators.PREFER_HEAP_ALLOCATOR.fromAscii(response.toString());
        payloadLength = payload.readableBytes();

        ctx = new ChannelHandlerContext() {
            @Override
            public Channel channel() {
                throw new UnsupportedOperationException();
            }

            @Override
            public EventExecutor executor() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String name() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandler handler() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isRemoved() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireChannelRegistered() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireChannelUnregistered() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireChannelActive() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireChannelInactive() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireExceptionCaught(final Throwable cause) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireUserEventTriggered(final Object evt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireChannelRead(final Object msg) {
                blackhole.consume(msg);
                return this;
            }

            @Override
            public ChannelHandlerContext fireChannelReadComplete() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireChannelWritabilityChanged() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext read() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext flush() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelPipeline pipeline() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBufAllocator alloc() {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> Attribute<T> attr(final AttributeKey<T> key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> boolean hasAttr(final AttributeKey<T> key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture bind(final SocketAddress localAddress) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture connect(final SocketAddress remoteAddress) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture connect(final SocketAddress remoteAddress, final SocketAddress localAddress) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture disconnect() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture close() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture deregister() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture bind(final SocketAddress localAddress, final ChannelPromise promise) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture connect(final SocketAddress remoteAddress, final ChannelPromise promise) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture connect(final SocketAddress remoteAddress, final SocketAddress localAddress,
                                         final ChannelPromise promise) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture disconnect(final ChannelPromise promise) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture close(final ChannelPromise promise) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture deregister(final ChannelPromise promise) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture write(final Object msg) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture write(final Object msg, final ChannelPromise promise) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture writeAndFlush(final Object msg, final ChannelPromise promise) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture writeAndFlush(final Object msg) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelPromise newPromise() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelProgressivePromise newProgressivePromise() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture newSucceededFuture() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture newFailedFuture(final Throwable cause) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelPromise voidPromise() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static String stringOfLength(final int valueLength) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < valueLength; ++i) {
            str.append((char) ('A' + (i % 26)));
        }
        return str.toString();
    }

    @Benchmark
    public void write() {
        final RedisDecoder decoder = new RedisDecoder();
        payload.readerIndex(0);
        int ri = 0;
        while (ri < payloadLength) {
            payload.writerIndex(ri);
            ri = Math.min(ri + bufLength, payloadLength);
            decoder.decode(ctx, BufferUtil.toByteBuf(payload));
        }
    }
}
