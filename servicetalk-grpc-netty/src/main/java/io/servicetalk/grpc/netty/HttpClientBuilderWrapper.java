/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.http.api.DelegatingSingleAddressHttpClientBuilder;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.netty.DefaultHttpLoadBalancerFactory;
import io.servicetalk.loadbalancer.ErrorClass;
import io.servicetalk.loadbalancer.RequestTracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.loadbalancer.RequestTracker.REQUEST_TRACKER_KEY;

final class HttpClientBuilderWrapper<U, R> extends DelegatingSingleAddressHttpClientBuilder<U, R> {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientBuilderWrapper.class);

    private static final Function<GrpcStatus, ErrorClass> PEER_RESPONSE_ERROR_CLASSIFIER = (status) -> {
        switch (status.code()) {
            case OK:
                return null;
            case CANCELLED:
                return ErrorClass.CANCELLED;
            default: // TODO: chances are there is a better mapping here.
                return ErrorClass.EXT_ORIGIN_REQUEST_FAILED;
        }
    };

    // TODO: this will likely have a few cases that can be distinguished.
    private static final Function<Throwable, ErrorClass> ERROR_CLASS_FUNCTION = (exn) ->
            ErrorClass.EXT_ORIGIN_REQUEST_FAILED;

    HttpClientBuilderWrapper(SingleAddressHttpClientBuilder<U, R> delegate) {
        super(delegate);
    }

    @Override
    public SingleAddressHttpClientBuilder<U, R> loadBalancerFactory(HttpLoadBalancerFactory<R> loadBalancerFactory) {
        return super.loadBalancerFactory(new RequestTrackingHttpLoadBalancerFactory<>(loadBalancerFactory));
    }

    private static final class RequestTrackingHttpLoadBalancerFactory<R> implements HttpLoadBalancerFactory<R> {

        private final HttpLoadBalancerFactory<R> delegate;

        RequestTrackingHttpLoadBalancerFactory(final HttpLoadBalancerFactory<R> delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T extends FilterableStreamingHttpLoadBalancedConnection> LoadBalancer<T> newLoadBalancer(
                Publisher<? extends ServiceDiscovererEvent<R>> eventPublisher,
                ConnectionFactory<R, T> connectionFactory) {
            return delegate.newLoadBalancer(eventPublisher, connectionFactory);
        }

        @Override
        public <T extends FilterableStreamingHttpLoadBalancedConnection> LoadBalancer<T> newLoadBalancer(
                String targetResource,
                Publisher<? extends Collection<? extends ServiceDiscovererEvent<R>>> eventPublisher,
                ConnectionFactory<R, T> connectionFactory) {
            return delegate.newLoadBalancer(targetResource, eventPublisher, connectionFactory);
        }

        @Override
        public LoadBalancer<FilterableStreamingHttpLoadBalancedConnection> newLoadBalancer(
                Publisher<? extends Collection<? extends ServiceDiscovererEvent<R>>> eventPublisher,
                ConnectionFactory<R, FilterableStreamingHttpLoadBalancedConnection> connectionFactory,
                String targetResource) {
            return delegate.newLoadBalancer(eventPublisher, connectionFactory, targetResource);
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            return delegate.requiredOffloads();
        }

        @Override
        public FilterableStreamingHttpLoadBalancedConnection toLoadBalancedConnection(
                FilterableStreamingHttpConnection connection,
                ReservableRequestConcurrencyController concurrencyController, @Nullable ContextMap context) {
            // TODO: this is copied from the DefaultHttpLoadBalancerFactory. Can it be shared or simplified?
            RequestTracker requestTracker = null;
            if (context == null) {
                LOGGER.debug("Context is null. In order for " + DefaultHttpLoadBalancerFactory.class.getSimpleName() +
                        ":toLoadBalancedConnection to get access to the " + RequestTracker.class.getSimpleName() +
                        ", health-monitor of this connection, the context must not be null.");
            } else {
                requestTracker = context.remove(REQUEST_TRACKER_KEY);
                if (requestTracker == null) {
                    LOGGER.debug(REQUEST_TRACKER_KEY.name() + " is not set in context. " +
                            "In order for " + DefaultHttpLoadBalancerFactory.class.getSimpleName() +
                            ":toLoadBalancedConnection to get access to the " + RequestTracker.class.getSimpleName() +
                            ", health-monitor of this connection, the context must be properly wired.");
                }
            }
            if (requestTracker != null) {
                connection = GrpcRequestTracker.observe(PEER_RESPONSE_ERROR_CLASSIFIER, ERROR_CLASS_FUNCTION,
                        requestTracker, connection);
            }
            return delegate.toLoadBalancedConnection(connection, concurrencyController, context);
        }
    }
}
