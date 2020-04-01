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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSingleSubscriber;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.StreamingHttpRequestFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.netty.internal.DeferSslHandler;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProxyConnectConnectionFactoryFilterTest {

    private static final StreamingHttpRequestFactory REQ_FACTORY = new DefaultStreamingHttpRequestResponseFactory(
            DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);
    private static final String CONNECT_ADDRESS = "foo.bar";
    private static final String RESOLVED_ADDRESS = "bar.foo";

    private final TestCompletable connectionClose = new TestCompletable();
    private final FilterableStreamingHttpConnection connection = mock(FilterableStreamingHttpConnection.class);
    private final TestSingleSubscriber<FilterableStreamingHttpConnection> subscriber = new TestSingleSubscriber<>();
    private final TestPublisher<Object> payloadBodyAndTrailers = new TestPublisher<>();

    public ProxyConnectConnectionFactoryFilterTest() {
        when(connection.closeAsync()).thenReturn(connectionClose);
        payloadBodyAndTrailers.whenRequest(__ -> payloadBodyAndTrailers.onComplete());
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

    private void configureConnectionContext(ChannelPipeline pipeline) {
        Channel channel = mock(Channel.class);
        when(channel.pipeline()).thenReturn(pipeline);

        NettyHttpConnectionContext nettyContext = mock(NettyHttpConnectionContext.class);
        when(nettyContext.nettyChannel()).thenReturn(channel);
        when(connection.connectionContext()).thenReturn(nettyContext);
    }

    private void configureRequestSend() {
        StreamingHttpResponse response = mock(StreamingHttpResponse.class);
        when(response.status()).thenReturn(OK);
        when(response.payloadBodyAndTrailers()).thenReturn(payloadBodyAndTrailers);
        when(connection.request(any(), any())).thenReturn(succeeded(response));
    }

    private void configureConnectRequest() {
        when(connection.connect(any())).thenReturn(REQ_FACTORY.connect(CONNECT_ADDRESS));
    }

    private void subscribeToProxyConnectionFactory() {
        @SuppressWarnings("unchecked")
        ConnectionFactory<String, FilterableStreamingHttpConnection> original = mock(ConnectionFactory.class);
        when(original.newConnection(any())).thenReturn(succeeded(connection));
        toSource(new ProxyConnectConnectionFactoryFilter<String, FilterableStreamingHttpConnection>(CONNECT_ADDRESS)
                .create(original).newConnection(RESOLVED_ADDRESS)).subscribe(subscriber);
    }

    @Test
    public void newConnectRequestThrows() {
        when(connection.connect(any())).thenThrow(DELIBERATE_EXCEPTION);
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.isErrored(), is(true));
        assertThat(subscriber.error(), is(DELIBERATE_EXCEPTION));
        verify(connection).connect(any());
        verify(connection, never()).request(any(), any());
        assertConnectionClosed();
    }

    @Test
    public void connectRequestFails() {
        when(connection.request(any(), any())).thenReturn(failed(DELIBERATE_EXCEPTION));

        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.isErrored(), is(true));
        assertThat(subscriber.error(), is(DELIBERATE_EXCEPTION));
        verify(connection).connect(any());
        verify(connection).request(any(), any());
        assertConnectionClosed();
    }

    @Test
    public void nonSuccessfulResponseCode() {
        StreamingHttpResponse response = mock(StreamingHttpResponse.class);
        when(response.status()).thenReturn(INTERNAL_SERVER_ERROR);
        when(response.payloadBodyAndTrailers()).thenReturn(payloadBodyAndTrailers);
        when(connection.request(any(), any())).thenReturn(succeeded(response));

        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.isErrored(), is(true));
        Throwable error = subscriber.error();
        assertThat(error, is(notNullValue()));
        assertThat(error, instanceOf(ProxyResponseException.class));
        assertThat(((ProxyResponseException) error).status(), is(INTERNAL_SERVER_ERROR));
        assertConnectPayloadConsumed();
        assertConnectionClosed();
    }

    @Test
    public void cannotAccessNettyChannel() {
        // Does not implement NettyConnectionContext:
        when(connection.connectionContext()).thenReturn(mock(HttpConnectionContext.class));

        configureRequestSend();
        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.isErrored(), is(true));
        Throwable error = subscriber.error();
        assertThat(error, is(notNullValue()));
        assertThat(error, instanceOf(ClassCastException.class));
        assertConnectPayloadConsumed();
        assertConnectionClosed();
    }

    @Test
    public void noDeferSslHandler() {
        ChannelPipeline pipeline = configurePipeline(SslHandshakeCompletionEvent.SUCCESS);
        // Do not configureDeferSslHandler(pipeline);
        configureConnectionContext(pipeline);
        configureRequestSend();
        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.isErrored(), is(true));
        Throwable error = subscriber.error();
        assertThat(error, is(notNullValue()));
        assertThat(error, instanceOf(IllegalStateException.class));
        assertThat(error.getMessage(), containsString(DeferSslHandler.class.getSimpleName()));
        assertConnectPayloadConsumed();
        assertConnectionClosed();
    }

    @Test
    public void deferSslHandlerReadyThrows() {
        ChannelPipeline pipeline = configurePipeline(SslHandshakeCompletionEvent.SUCCESS);
        when(pipeline.get(DeferSslHandler.class)).thenThrow(DELIBERATE_EXCEPTION);

        configureConnectionContext(pipeline);
        configureRequestSend();
        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.isErrored(), is(true));
        assertThat(subscriber.error(), is(DELIBERATE_EXCEPTION));
        assertConnectPayloadConsumed();
        assertConnectionClosed();
    }

    @Test
    public void sslHandshakeFailure() {
        ChannelPipeline pipeline = configurePipeline(new SslHandshakeCompletionEvent(DELIBERATE_EXCEPTION));

        configureDeferSslHandler(pipeline);
        configureConnectionContext(pipeline);
        configureRequestSend();
        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.isErrored(), is(true));
        assertThat(subscriber.error(), is(DELIBERATE_EXCEPTION));
        assertConnectPayloadConsumed();
        assertConnectionClosed();
    }

    @Test
    public void cancelledBeforeSslHandshakeCompletionEvent() {
        ChannelPipeline pipeline = configurePipeline(null); // Do not generate any SslHandshakeCompletionEvent

        configureDeferSslHandler(pipeline);
        configureConnectionContext(pipeline);
        configureRequestSend();
        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.cancellableReceived(), is(true));
        assertThat(subscriber.isErrored(), is(false));
        assertThat(subscriber.isSuccess(), is(false));
        assertThat(connectionClose.isSubscribed(), is(false));
        subscriber.cancel();
        assertConnectPayloadConsumed();
        assertConnectionClosed();
    }

    @Test
    public void successfulConnect() {
        ChannelPipeline pipeline = configurePipeline(SslHandshakeCompletionEvent.SUCCESS);
        configureDeferSslHandler(pipeline);
        configureConnectionContext(pipeline);
        configureRequestSend();
        configureConnectRequest();
        subscribeToProxyConnectionFactory();

        assertThat(subscriber.isSuccess(), is(true));
        assertThat(subscriber.result(), is(sameInstance(this.connection)));
        assertConnectPayloadConsumed();
        assertThat("Connection closed", connectionClose.isSubscribed(), is(false));
    }

    private void assertConnectPayloadConsumed() {
        verify(connection).connect(any());
        verify(connection).request(any(), any());
        assertThat("CONNECT response payload body was not consumed", payloadBodyAndTrailers.isSubscribed(), is(true));
    }

    private void assertConnectionClosed() {
        assertThat("Closure of the connection was not triggered", connectionClose.isSubscribed(), is(true));
    }

    private interface NettyHttpConnectionContext extends HttpConnectionContext, NettyConnectionContext {
        // no methods
    }
}
