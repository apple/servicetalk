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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.IMMEDIATE;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class RetryRequestWithNonRepeatablePayloadTest extends AbstractNettyHttpServerTest {

    private void setUp(HttpProtocol protocol, TestPublisher<Buffer> payloadBody, Queue<Throwable> errors,
                       boolean offloading) {
        protocol(protocol.config);
        ChannelOutboundHandler firstWriteHandler = new FailingFirstWriteHandler();
        connectionFactoryFilter(factory -> new DelegatingConnectionFactory<InetSocketAddress,
                FilterableStreamingHttpConnection>(factory) {
            @Override
            public Single<FilterableStreamingHttpConnection> newConnection(InetSocketAddress address,
                                                                           @Nullable TransportObserver observer) {
                return delegate().newConnection(address, observer).map(c -> {
                    final Channel channel = ((NettyConnectionContext) c.connectionContext()).nettyChannel();
                    if (protocol == HTTP_1) {
                        // Insert right before HttpResponseDecoder to avoid seeing failed frames on wire logs
                        channel.pipeline().addBefore(HttpRequestEncoder.class.getSimpleName() + "#0", null,
                                firstWriteHandler);
                    } else if (protocol == HTTP_2) {
                        // Insert right before Http2MultiplexHandler to avoid failing connection-level frames and
                        // seeing failed stream frames on frame/wire logs
                        channel.pipeline().addBefore(Http2MultiplexHandler.class.getSimpleName() + "#0", null,
                                firstWriteHandler);
                    }
                    return new StreamingHttpConnectionFilter(c) {
                        @Override
                        public Single<StreamingHttpResponse> request(HttpExecutionStrategy strategy,
                                                                     StreamingHttpRequest request) {
                            return delegate().request(strategy, request).whenOnError(t -> {
                                try {
                                    assertThat("Unexpected exception type", t, instanceOf(RetryableException.class));
                                    assertThat("Unexpected exception type",
                                            t.getCause(), instanceOf(DeliberateException.class));
                                    assertThat("Unexpected subscribe to payload body",
                                            payloadBody.isSubscribed(), is(false));
                                } catch (Throwable error) {
                                    errors.add(error);
                                }
                            });
                        }
                    };
                });
            }
        });
        setUp(offloading ? CACHED : IMMEDIATE, offloading ? CACHED_SERVER : IMMEDIATE);
    }

    private static Collection<Arguments> data() {
        List<Arguments> list = new ArrayList<>();
        for (HttpProtocol protocol : HttpProtocol.values()) {
            list.add(Arguments.of(protocol, true));
            list.add(Arguments.of(protocol, false));
        }
        return list;
    }

    @ParameterizedTest(name = "protocol={0}, offloading={1}")
    @MethodSource("data")
    void test(HttpProtocol protocol, boolean offloading) throws Exception {
        Queue<Throwable> errors = new LinkedBlockingDeque<>();
        TestPublisher<Buffer> payloadBody = new TestPublisher.Builder<Buffer>()
                .singleSubscriber()
                .build();
        setUp(protocol, payloadBody, errors, offloading);

        StreamingHttpClient client = streamingHttpClient();
        StreamingHttpResponse response = client.request(client.post(TestServiceStreaming.SVC_ECHO)
                .payloadBody(payloadBody)).toFuture().get();

        String expectedPayload = "hello";
        payloadBody.onNext(client.executionContext().bufferAllocator().fromAscii(expectedPayload));
        payloadBody.onComplete();

        assertResponse(response, protocol.version, OK, expectedPayload);
        assertNoAsyncErrors(errors);
    }

    @Sharable
    private static class FailingFirstWriteHandler extends ChannelOutboundHandlerAdapter {

        private boolean needToFail = true;

        @Override
        public void write(ChannelHandlerContext ctx, Object msg,
                          ChannelPromise promise) throws Exception {
            if (needToFail) {
                needToFail = false;
                ReferenceCountUtil.release(msg);
                throw DELIBERATE_EXCEPTION;
            } else {
                ctx.write(msg, promise);
            }
        }
    }
}
