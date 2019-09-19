/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.netty.AlpnChannelHandler.AlpnConnectionContext;
import io.servicetalk.http.netty.AlpnChannelHandler.NoopChannelInitializer;
import io.servicetalk.http.netty.H2ClientParentConnectionContext.H2ClientParentConnection;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpConnector;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http2.Http2CodecUtil.SMALLEST_MAX_CONCURRENT_STREAMS;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.reservedConnectionsPipelineEnabled;
import static io.servicetalk.transport.api.SecurityConfigurator.ApplicationProtocolNames.HTTP_1_1;
import static io.servicetalk.transport.api.SecurityConfigurator.ApplicationProtocolNames.HTTP_2;

final class AlpnLBHttpConnectionFactory<ResolvedAddress> extends AbstractLBHttpConnectionFactory<ResolvedAddress> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlpnLBHttpConnectionFactory.class);

    AlpnLBHttpConnectionFactory(
            final ReadOnlyHttpClientConfig config, final HttpExecutionContext executionContext,
            @Nullable final StreamingHttpConnectionFilterFactory connectionFilterFunction,
            final StreamingHttpRequestResponseFactory reqRespFactory,
            final HttpExecutionStrategyInfluencer strategyInfluencer,
            final ConnectionFactoryFilter<ResolvedAddress, FilterableStreamingHttpConnection> connectionFactoryFilter,
            final Function<FilterableStreamingHttpConnection,
                    FilterableStreamingHttpLoadBalancedConnection> protocolBinding) {
        super(config, executionContext, connectionFilterFunction, reqRespFactory, strategyInfluencer,
                connectionFactoryFilter, protocolBinding);
    }

    @Override
    Single<FilterableStreamingHttpConnection> newFilterableConnection(final ResolvedAddress resolvedAddress) {
        final ReadOnlyTcpClientConfig roTcpClientConfig = config.tcpClientConfig();
        // This state is read only, so safe to keep a copy across Subscribers
        return TcpConnector.connect(null, resolvedAddress, roTcpClientConfig, executionContext)
                .flatMap(this::createConnection);
    }

    private Single<FilterableStreamingHttpConnection> createConnection(final Channel channel) {

        return new SubscribableSingle<AlpnConnectionContext>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super AlpnConnectionContext> subscriber) {
                final AlpnConnectionContext context;
                try {
                    context = new AlpnConnectionContext(channel, executionContext);
                    new TcpClientChannelInitializer(config.tcpClientConfig()).init(channel, context);
                } catch (Throwable cause) {
                    channel.close();
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onError(cause);
                    return;
                }
                subscriber.onSubscribe(channel::close);
                // We have to add to the pipeline AFTER we call onSubscribe, because adding to the pipeline may invoke
                // callbacks that interact with the subscriber.
                channel.pipeline().addLast(new AlpnChannelHandler(context, subscriber, false));
            }
        }.flatMap(alpnContext -> {
            final ReadOnlyTcpClientConfig tcpConfig = config.tcpClientConfig();
            final String protocol = alpnContext.protocol();
            assert protocol != null;
            switch (protocol) {
                case HTTP_1_1:
                    return StreamingConnectionFactory.createConnection(channel, executionContext, config,
                            NoopChannelInitializer.INSTANCE)
                            .map(conn -> reservedConnectionsPipelineEnabled(config) ?
                                    new PipelinedStreamingHttpConnection(conn, config, executionContext,
                                            reqRespFactory) :
                                    new NonPipelinedStreamingHttpConnection(conn, executionContext,
                                            reqRespFactory, config.headersFactory()));
                case HTTP_2:
                    return H2ClientParentConnectionContext.initChannel(channel,
                            executionContext.bufferAllocator(), executionContext.executor(),
                            config.h2ClientConfig(), reqRespFactory, tcpConfig.flushStrategy(),
                            executionContext.executionStrategy(),
                            new H2ClientParentChannelInitializer(config.h2ClientConfig()));
                default:
                    return failed(new IllegalStateException("Unknown ALPN protocol negotiated: " + protocol));
            }
        });
    }

    @Override
    int initialMaxConcurrency(final FilterableStreamingHttpConnection connection) {
        if (connection instanceof NonPipelinedStreamingHttpConnection) {
            return 1;
        } else if (connection instanceof PipelinedStreamingHttpConnection) {
            return config.maxPipelinedRequests();
        } else if (connection instanceof H2ClientParentConnection) {
            return SMALLEST_MAX_CONCURRENT_STREAMS;
        } else {
            throw new IllegalStateException("Unsupported connection type: " + connection.getClass().getName());
        }
    }
}
