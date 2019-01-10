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

/*
 * Benchmark of RedisDecoder for various lengths and numbers of string args, and various receive buffer sizes.
 *
 * Benchmark                    (bufLength)  (valCount)  (valLen)   Mode  Cnt         Score         Error  Units
 * RedisDecoderBenchmark.write           10          10        10  thrpt    5    717506.942 ±   51346.128  ops/s
 * RedisDecoderBenchmark.write           10          10       100  thrpt    5    149759.038 ±    7258.237  ops/s
 * RedisDecoderBenchmark.write           10          10      1000  thrpt    5     18103.662 ±    1331.100  ops/s
 * RedisDecoderBenchmark.write           10         100        10  thrpt    5     70332.828 ±    4294.236  ops/s
 * RedisDecoderBenchmark.write           10         100       100  thrpt    5     15114.616 ±     578.767  ops/s
 * RedisDecoderBenchmark.write           10         100      1000  thrpt    5      2110.848 ±     119.576  ops/s
 * RedisDecoderBenchmark.write           10        1000        10  thrpt    5      7389.984 ±     800.250  ops/s
 * RedisDecoderBenchmark.write           10        1000       100  thrpt    5      1449.701 ±     108.337  ops/s
 * RedisDecoderBenchmark.write           10        1000      1000  thrpt    5       211.382 ±       8.863  ops/s
 * RedisDecoderBenchmark.write          100          10        10  thrpt    5   6394861.716 ±  444354.498  ops/s
 * RedisDecoderBenchmark.write          100          10       100  thrpt    5   1104276.800 ±   79815.991  ops/s
 * RedisDecoderBenchmark.write          100          10      1000  thrpt    5    142003.195 ±    4447.347  ops/s
 * RedisDecoderBenchmark.write          100         100        10  thrpt    5    675582.367 ±   45863.689  ops/s
 * RedisDecoderBenchmark.write          100         100       100  thrpt    5     90368.796 ±    5131.327  ops/s
 * RedisDecoderBenchmark.write          100         100      1000  thrpt    5     14229.505 ±     641.035  ops/s
 * RedisDecoderBenchmark.write          100        1000        10  thrpt    5     71320.281 ±    4251.774  ops/s
 * RedisDecoderBenchmark.write          100        1000       100  thrpt    5     11232.072 ±     390.909  ops/s
 * RedisDecoderBenchmark.write          100        1000      1000  thrpt    5      1428.908 ±      57.517  ops/s
 * RedisDecoderBenchmark.write         1000          10        10  thrpt    5  20953834.916 ± 1476915.538  ops/s
 * RedisDecoderBenchmark.write         1000          10       100  thrpt    5   6102221.241 ±  443218.739  ops/s
 * RedisDecoderBenchmark.write         1000          10      1000  thrpt    5    334638.490 ±   15575.734  ops/s
 * RedisDecoderBenchmark.write         1000         100        10  thrpt    5   6277949.075 ±  396739.025  ops/s
 * RedisDecoderBenchmark.write         1000         100       100  thrpt    5   1087285.766 ±   80665.243  ops/s
 * RedisDecoderBenchmark.write         1000         100      1000  thrpt    5     31320.376 ±    1152.215  ops/s
 * RedisDecoderBenchmark.write         1000        1000        10  thrpt    5    680465.530 ±   64658.052  ops/s
 * RedisDecoderBenchmark.write         1000        1000       100  thrpt    5    111253.832 ±    4570.156  ops/s
 * RedisDecoderBenchmark.write         1000        1000      1000  thrpt    5      3031.522 ±     117.617  ops/s
 */
@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.Throughput)
public class RedisDecoderBenchmark {

    @Param({"10", "100", "1000"})
    public int valCount;

    @Param({"10", "100", "1000"})
    public int valLen;

    @Param({"10", "100", "1000"})
    public int bufLength;

    int payloadLength;
    Buffer payload;
    private ChannelHandlerContext ctx;

    @Setup(Level.Iteration)
    public void setup(final Blackhole blackhole) {
        StringBuilder response = new StringBuilder();
        response.append("*" + valCount).append("\r\n");
        for (int i = 0; i < valCount; ++i) {
            response.append("$" + valLen).append("\r\n");
            response.append(stringOfLength(valLen)).append("\r\n");
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
            public ChannelFuture connect(final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
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
