/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConnectionFactory;
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
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpRequestFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.netty.internal.DeferSslHandler;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
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
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
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

class ProxyConnectConnectionFactoryFilterTest {

    private static final StreamingHttpRequestFactory REQ_FACTORY = new DefaultStreamingHttpRequestResponseFactory(
            DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);
    private static final String CONNECT_ADDRESS = "foo.bar";
    private static final String RESOLVED_ADDRESS = "bar.foo";

    private final FilterableStreamingHttpConnection connection;
    private final TestCompletable connectionClose;
    private final TestPublisher<Object> messageBody;
    private final TestSingleSubscriber<FilterableStreamingHttpConnection> subscriber;

    ProxyConnectConnectionFactoryFilterTest() {
        HttpExecutionContext executionContext = new HttpExecutionContextBuilder().build();
        HttpConnectionContext connectionContext = (mock(HttpConnectionContext.class));
        when(connectionContext.executionContext()).thenReturn(executionContext);
        connection = mock(FilterableStreamingHttpConnection.class);
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
    }

    private ChannelPipeline configurePipeline(@Nullable SslHandshakeCompletionEvent event) {
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

    private void configureDeferSslHandler(ChannelPipeline pipeline) {
        when(pipeline.get(DeferSslHandler.class)).thenReturn(mock(DeferSslHandler.class));
    }

    private void configureConnectionContext(final ChannelPipeline pipeline) {
        configureConnectionContext(pipeline, HttpExecutionStrategies.defaultStrategy());
    }

    private void configureConnectionContext(final ChannelPipeline pipeline,
                                            final HttpExecutionStrategy executionStrategy) {
        Channel channel = mock(Channel.class);
        when(channel.pipeline()).thenReturn(pipeline);

        HttpExecutionContext executionContext = new HttpExecutionContextBuilder()
                .executionStrategy(executionStrategy).build();
        NettyHttpConnectionContext nettyContext = mock(NettyHttpConnectionContext.class);
        when(nettyContext.executionContext()).thenReturn(executionContext);
        when(nettyContext.nettyChannel()).thenReturn(channel);
        when(connection.connectionContext()).thenReturn(nettyContext);
    }

    private void configureRequestSend() {
        StreamingHttpResponse response = mock(StreamingHttpResponse.class);
        when(response.status()).thenReturn(OK);
        when(response.messageBody()).thenReturn(messageBody);
        when(connection.request(any(), any())).thenReturn(succeeded(response));
    }

    private void configureConnectRequest() {
        when(connection.connect(any())).thenReturn(REQ_FACTORY.connect(CONNECT_ADDRESS));
    }

    private void subscribeToProxyConnectionFactory() {
        subscribeToProxyConnectionFactory(c -> { });
    }

    private void subscribeToProxyConnectionFactory(Consumer<FilterableStreamingHttpConnection> onSuccess) {
        @SuppressWarnings("unchecked")
        ConnectionFactory<String, FilterableStreamingHttpConnection> original = mock(ConnectionFactory.class);
        when(original.newConnection(any(), any())).thenReturn(succeeded(connection));
        toSource(new ProxyConnectConnectionFactoryFilter<String, FilterableStreamingHttpConnection>(CONNECT_ADDRESS)
                .create(original).newConnection(RESOLVED_ADDRESS, null).afterOnSuccess(onSuccess))
                .subscribe(subscriber);
    }

    @Test
    void newConnectRequestThrows() {
        when(connection.connect(any())).thenThrow(DELIBERATE_EXCEPTION);
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        verify(connection).connect(any());
        verify(connection, never()).request(any(), any());
        assertConnectionClosed();
    }

    @Test
    void connectRequestFails() {
        when(connection.request(any(), any())).thenReturn(failed(DELIBERATE_EXCEPTION));

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
        when(connection.request(any(), any())).thenReturn(succeeded(response));

        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        Throwable error = subscriber.awaitOnError();
        assertThat(error, instanceOf(ProxyResponseException.class));
        assertThat(((ProxyResponseException) error).status(), is(INTERNAL_SERVER_ERROR));
        assertConnectPayloadConsumed(false);
        assertConnectionClosed();
    }

    @Test
    void cannotAccessNettyChannel() {
        // Does not implement NettyConnectionContext:
        HttpExecutionContext executionContext = new HttpExecutionContextBuilder().build();

        HttpConnectionContext connectionContext = (mock(HttpConnectionContext.class));
        when(connectionContext.executionContext()).thenReturn(executionContext);

        when(connection.connectionContext()).thenReturn(connectionContext);

        configureRequestSend();
        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.awaitOnError(), instanceOf(ClassCastException.class));
        assertConnectPayloadConsumed(false);
        assertConnectionClosed();
    }

    @Test
    void noDeferSslHandler() {
        ChannelPipeline pipeline = configurePipeline(SslHandshakeCompletionEvent.SUCCESS);
        // Do not configureDeferSslHandler(pipeline);
        configureConnectionContext(pipeline);
        configureRequestSend();
        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        Throwable error = subscriber.awaitOnError();
        assertThat(error, is(notNullValue()));
        assertThat(error, instanceOf(IllegalStateException.class));
        assertThat(error.getMessage(), containsString(DeferSslHandler.class.getSimpleName()));
        assertConnectPayloadConsumed(false);
        assertConnectionClosed();
    }

    @Test
    void deferSslHandlerReadyThrows() {
        ChannelPipeline pipeline = configurePipeline(SslHandshakeCompletionEvent.SUCCESS);
        when(pipeline.get(DeferSslHandler.class)).thenThrow(DELIBERATE_EXCEPTION);

        configureConnectionContext(pipeline);
        configureRequestSend();
        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        assertConnectPayloadConsumed(false);
        assertConnectionClosed();
    }

    @Test
    void sslHandshakeFailure() {
        ChannelPipeline pipeline = configurePipeline(new SslHandshakeCompletionEvent(DELIBERATE_EXCEPTION));

        configureDeferSslHandler(pipeline);
        configureConnectionContext(pipeline);
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
        configureConnectionContext(pipeline);
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
        configureConnectionContext(pipeline);
        configureRequestSend();
        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.awaitOnSuccess(), is(sameInstance(this.connection)));
        assertConnectPayloadConsumed(true);
        assertThat("Connection closed", connectionClose.isSubscribed(), is(false));
    }

    @Test
    void noOffloadingStrategy() {
        ChannelPipeline pipeline = configurePipeline(SslHandshakeCompletionEvent.SUCCESS);
        configureDeferSslHandler(pipeline);
        configureConnectionContext(pipeline, HttpExecutionStrategies.noOffloadsStrategy());
        configureRequestSend();
        configureConnectRequest();
        Queue<Throwable> errors = new LinkedBlockingQueue<>();
        Thread testThread = Thread.currentThread();
        subscribeToProxyConnectionFactory(c -> {
            if (Thread.currentThread() != testThread) {
                errors.add(new AssertionError("Unexpected Thread for success " + Thread.currentThread()));
            }
        });

        assertNoAsyncErrors(errors);
        assertThat(subscriber.awaitOnSuccess(), is(sameInstance(this.connection)));
        assertConnectPayloadConsumed(true);
        assertThat("Connection closed", !connectionClose.isSubscribed());
    }

    private void assertConnectPayloadConsumed(boolean expected) {
        verify(connection).connect(any());
        verify(connection).request(any(), any());
        assertThat("CONNECT response payload body was " + (expected ? "was" : "unnecessarily") + " consumed",
                messageBody.isSubscribed(), is(expected));
    }

    private void assertConnectionClosed() {
        assertThat("Closure of the connection was not triggered", connectionClose.isSubscribed(), is(true));
    }

    private interface NettyHttpConnectionContext extends HttpConnectionContext, NettyConnectionContext {
        // no methods
    }
}
