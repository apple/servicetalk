/*
 * Copyright © 2020-2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.AbstractLBHttpConnectionFactory.ProtocolBinding;
import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.netty.internal.DeferSslHandler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;

import java.nio.channels.ClosedChannelException;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProxyConnectLBHttpConnectionFactoryTest {

    private static final StreamingHttpRequestResponseFactory REQ_RES_FACTORY =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);
    private static final String CONNECT_ADDRESS = "foo.bar";

    private final NettyFilterableStreamingHttpConnection connection;
    private final TestCompletable connectionClose;
    private final TestPublisher<Object> messageBody;
    private final TestSingleSubscriber<FilterableStreamingHttpConnection> subscriber;
    private final ProxyConnectLBHttpConnectionFactory<String> connectionFactory;

    ProxyConnectLBHttpConnectionFactoryTest() {
        HttpExecutionContext executionContext = new HttpExecutionContextBuilder().build();
        HttpConnectionContext connectionContext = mock(HttpConnectionContext.class);
        when(connectionContext.executionContext()).thenReturn(executionContext);
        connection = mock(NettyFilterableStreamingHttpConnection.class);
        when(connection.connectionContext()).thenReturn(connectionContext);
        connectionClose = new TestCompletable.Builder().build(subscriber -> {
            subscriber.onSubscribe(IGNORE_CANCEL);
            subscriber.onComplete();
            return subscriber;
        });
        when(connection.closeAsync()).thenReturn(connectionClose);

        messageBody = new TestPublisher.Builder<>().build(subscriber -> {
            subscriber.onSubscribe(new PublisherSource.Subscription() {
                @Override
                public void request(final long n) {
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    // noop
                }
            });
            return subscriber;
        });

        subscriber = new TestSingleSubscriber<>();

        HttpClientConfig config = new HttpClientConfig();
        config.connectAddress(CONNECT_ADDRESS);
        config.protocolConfigs().protocols(h1Default());
        connectionFactory = new ProxyConnectLBHttpConnectionFactory<>(config.asReadOnly(),
                executionContext, null, REQ_RES_FACTORY, ConnectExecutionStrategy.offloadNone(),
                ConnectionFactoryFilter.identity(), mock(ProtocolBinding.class));
    }

    private static ChannelPipeline configurePipeline(@Nullable SslHandshakeCompletionEvent event) {
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        when(pipeline.addLast(any())).then((Answer<ChannelPipeline>) invocation -> {
            ChannelInboundHandler handshakeAwait = invocation.getArgument(0);
            if (event != null) {
                handshakeAwait.userEventTriggered(mock(ChannelHandlerContext.class), event);
            }
            return pipeline;
        });
        return pipeline;
    }

    private static void configureDeferSslHandler(ChannelPipeline pipeline) {
        when(pipeline.get(DeferSslHandler.class)).thenReturn(mock(DeferSslHandler.class));
    }

    private void configureConnectionNettyChannel(final ChannelPipeline pipeline) {
        Channel channel = mock(Channel.class);
        EventLoop eventLoop = mock(EventLoop.class);
        when(eventLoop.inEventLoop()).thenReturn(true);
        when(channel.eventLoop()).thenReturn(eventLoop);
        when(channel.pipeline()).thenReturn(pipeline);
        when(pipeline.channel()).thenReturn(channel);
        when(connection.nettyChannel()).thenReturn(channel);
    }

    private void configureRequestSend() {
        StreamingHttpResponse response = mock(StreamingHttpResponse.class);
        when(response.status()).thenReturn(OK);
        when(response.messageBody()).thenReturn(messageBody);
        when(connection.request(any())).thenReturn(succeeded(response));
    }

    private void configureConnectRequest() {
        when(connection.connect(any())).thenReturn(REQ_RES_FACTORY.connect(CONNECT_ADDRESS));
    }

    private void subscribeToProxyConnectionFactory() {
        toSource(connectionFactory.processConnect(connection)).subscribe(subscriber);
    }

    @Test
    void newConnectRequestThrows() {
        when(connection.connect(any())).thenThrow(DELIBERATE_EXCEPTION);
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        verify(connection).connect(any());
        verify(connection, never()).request(any());
        assertConnectionClosed();
    }

    @Test
    void connectRequestFails() {
        when(connection.request(any())).thenReturn(failed(DELIBERATE_EXCEPTION));

        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        Throwable error = subscriber.awaitOnError();
        assertThat("Unexpected error: " + error, error, is(DELIBERATE_EXCEPTION));
        assertConnectPayloadConsumed(false);
        assertConnectionClosed();
    }

    @Test
    void nonSuccessfulResponseCode() {
        StreamingHttpResponse response = mock(StreamingHttpResponse.class);
        when(response.status()).thenReturn(INTERNAL_SERVER_ERROR);
        when(response.messageBody()).thenReturn(messageBody);
        when(connection.request(any())).thenReturn(succeeded(response));

        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        Throwable error = subscriber.awaitOnError();
        assertThat(error, instanceOf(ProxyResponseException.class));
        assertThat(((ProxyResponseException) error).status(), is(INTERNAL_SERVER_ERROR));
        assertConnectPayloadConsumed(true);
        assertConnectionClosed();
    }

    @ParameterizedTest(name = "{displayName} [{index}] ttl={0}")
    @ValueSource(booleans = {true, false})
    void noDeferSslHandler(boolean channelActive) {
        ChannelPipeline pipeline = configurePipeline(SslHandshakeCompletionEvent.SUCCESS);
        // Do not configureDeferSslHandler(pipeline);
        configureConnectionNettyChannel(pipeline);
        Channel channel = pipeline.channel();
        when(channel.isActive()).thenReturn(channelActive);
        configureRequestSend();
        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        Throwable error = subscriber.awaitOnError();
        assertThat(error, is(notNullValue()));
        if (channelActive) {
            assertThat(error, instanceOf(IllegalStateException.class));
            assertThat(error.getMessage(), containsString(DeferSslHandler.class.getSimpleName()));
        } else {
            assertThat(error, instanceOf(ClosedChannelException.class));
        }
        assertConnectPayloadConsumed(true);
        assertConnectionClosed();
    }

    @Test
    void deferSslHandlerReadyThrows() {
        ChannelPipeline pipeline = configurePipeline(SslHandshakeCompletionEvent.SUCCESS);
        when(pipeline.get(DeferSslHandler.class)).thenThrow(DELIBERATE_EXCEPTION);

        configureConnectionNettyChannel(pipeline);
        configureRequestSend();
        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        assertConnectPayloadConsumed(true);
        assertConnectionClosed();
    }

    @Test
    void sslHandshakeFailure() {
        ChannelPipeline pipeline = configurePipeline(new SslHandshakeCompletionEvent(DELIBERATE_EXCEPTION));

        configureDeferSslHandler(pipeline);
        configureConnectionNettyChannel(pipeline);
        configureRequestSend();
        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        assertConnectPayloadConsumed(true);
        assertConnectionClosed();
    }

    @Test
    @Disabled("https://github.com/apple/servicetalk/issues/1010")
    void cancelledBeforeSslHandshakeCompletionEvent() {
        ChannelPipeline pipeline = configurePipeline(null); // Do not generate any SslHandshakeCompletionEvent

        configureDeferSslHandler(pipeline);
        configureConnectionNettyChannel(pipeline);
        configureRequestSend();
        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        Cancellable cancellable = subscriber.awaitSubscription();
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        assertThat(connectionClose.isSubscribed(), is(false));
        cancellable.cancel();
        assertConnectPayloadConsumed(true);
        assertConnectionClosed();
    }

    @Test
    void successfulConnect() {
        ChannelPipeline pipeline = configurePipeline(SslHandshakeCompletionEvent.SUCCESS);
        configureDeferSslHandler(pipeline);
        configureConnectionNettyChannel(pipeline);
        configureRequestSend();
        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.awaitOnSuccess(), is(sameInstance(this.connection)));
        assertConnectPayloadConsumed(true);
        assertThat("Connection closed", connectionClose.isSubscribed(), is(false));
    }

    private void assertConnectPayloadConsumed(boolean expected) {
        verify(connection).connect(any());
        verify(connection).request(any());
        assertThat("CONNECT response payload body was " + (expected ? "was" : "unnecessarily") + " consumed",
                messageBody.isSubscribed(), is(expected));
    }

    private void assertConnectionClosed() {
        assertThat("Closure of the connection was not triggered", connectionClose.isSubscribed(), is(true));
    }
}
