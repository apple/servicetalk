/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.HttpResponseMetaData;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayDeque;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseMetaDataFactory.newResponseMetaData;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.netty.HttpResponseEncoder.NOOP_ON_RESPONSE;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;

/*
 * This benchmark measures encoding of full HTTP request with headers and payload body. Everything is allocated using
 * ReadOnlyBufferAllocator:
 *
 * Benchmark                                               Mode  Cnt       Score      Error  Units
 * HttpResponseEncoderBenchmarkFullResponse.fullResponse  thrpt    5  669406.100 ± 6113.671  ops/s
 */
@Fork(value = 1)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 10)
@BenchmarkMode(Mode.Throughput)
public class HttpResponseEncoderFullResponseBenchmark {

    private HttpResponseMetaData metaData;
    private Buffer payloadBody;

    private EmbeddedChannel channel;

    @Setup(Level.Trial)
    public void setup() {
        payloadBody = DEFAULT_RO_ALLOCATOR.fromAscii("Internal Server Error payload body for response");
        metaData = newResponseMetaData(HTTP_1_1, INTERNAL_SERVER_ERROR, INSTANCE.newHeaders())
                .addHeader(CONTENT_LENGTH, newAsciiString(Integer.toString(payloadBody.readableBytes())))
                .addHeader(CONTENT_TYPE, TEXT_PLAIN)
                .addHeader(newAsciiString("X-Custom-Header-Name"), newAsciiString("X-Custom-Header-Value"));

        channel = new EmbeddedChannel(new HttpResponseEncoder(new ArrayDeque<>(), 256, 256,
                UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, NOOP_ON_RESPONSE));
    }

    @Benchmark
    public int fullResponse() {
        channel.writeOutbound(metaData);
        channel.writeOutbound(payloadBody.duplicate());
        channel.writeOutbound(EmptyHttpHeaders.INSTANCE);

        final ByteBuf byteBuf1 = channel.readOutbound();
        final int size1 = byteBuf1.readableBytes();
        byteBuf1.release();
        final ByteBuf byteBuf2 = channel.readOutbound();
        final int size2 = byteBuf2.readableBytes();
        byteBuf2.release();
        final ByteBuf byteBuf3 = channel.readOutbound();
        final int size3 = byteBuf3.readableBytes();
        byteBuf3.release();
        return size1 + size2 + size3;
    }
}
