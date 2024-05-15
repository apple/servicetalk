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
package io.servicetalk.loadbalancer.experimental;

import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.http.api.DefaultHttpLoadBalancerFactory;
import io.servicetalk.http.api.DelegatingSingleAddressHttpClientBuilder;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpProviders;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.loadbalancer.LoadBalancers;
import io.servicetalk.transport.api.HostAndPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client builder provider that supports enabling the new `DefaultLoadBalancer` in applications via property flags.
 * See the packages README.md for more details.
 */
public class DefaultHttpLoadBalancerProvider implements HttpProviders.SingleAddressHttpClientBuilderProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHttpLoadBalancerProvider.class);

    public DefaultHttpLoadBalancerProvider() {
    }

    @Override
    public final <U, R> SingleAddressHttpClientBuilder<U, R> newBuilder(U address,
                                                                  SingleAddressHttpClientBuilder<U, R> builder) {
        final String serviceName = clientNameFromAddress(address);
        final DefaultLoadBalancerProviderConfig config = DefaultLoadBalancerProviderConfig.instance();
        LOGGER.debug(
                "Property based config for client to service name {} at address {}: {}", serviceName, address, config);
        if (config.enabledForServiceName(serviceName)) {
            try {
                HttpLoadBalancerFactory<R> loadBalancerFactory = new DefaultHttpLoadBalancerFactory<>(
                        defaultLoadBalancer(config));
                builder = builder.loadBalancerFactory(loadBalancerFactory);
                builder = new LoadBalancerIgnoringBuilder<>(builder, serviceName);
                LOGGER.info("Enabled DefaultLoadBalancer for service with name {}", serviceName);
            } catch (Throwable ex) {
                LOGGER.warn("Failed to enabled DefaultLoadBalancer for client to address {}.", address, ex);
            }
        }
        return builder;
    }

    private <R> LoadBalancerFactory<R, FilterableStreamingHttpLoadBalancedConnection> defaultLoadBalancer(
            DefaultLoadBalancerProviderConfig config) {
        return LoadBalancers.<R, FilterableStreamingHttpLoadBalancedConnection>
                        builder("experimental-load-balancer")
                .loadBalancerObserver(DefaultLoadBalancerObserver::new)
                // set up the new features.
                .outlierDetectorConfig(config.outlierDetectorConfig())
                .loadBalancingPolicy(config.getLoadBalancingPolicy())
                .build();
    }

    /**
     * Extract the service name from the address object.
     * Note: this is a protected method to allow overriding for custom address types.
     * @param <U> the unresolved type of the address.
     * @param address the address from which to extract the service name.
     * @return the String representation of the provided address.
     */
    protected <U> String clientNameFromAddress(U address) {
        String serviceName;
        if (address instanceof HostAndPort) {
            serviceName = ((HostAndPort) address).hostName();
        } else if (address instanceof String) {
            serviceName = (String) address;
        } else {
            LOGGER.debug("Unknown service address type={} was provided, "
                    + "default 'toString()' will be used as serviceName", address.getClass());
            serviceName = address.toString();
        }
        return serviceName;
    }

    // Exposed for testing
    static final class LoadBalancerIgnoringBuilder<U, R>
            extends DelegatingSingleAddressHttpClientBuilder<U, R> {

        private final String serviceName;

        LoadBalancerIgnoringBuilder(final SingleAddressHttpClientBuilder<U, R> delegate, final String serviceName) {
            super(delegate);
            this.serviceName = serviceName;
        }

        @Override
        public SingleAddressHttpClientBuilder<U, R> loadBalancerFactory(
                HttpLoadBalancerFactory<R> loadBalancerFactory) {
            LOGGER.info("Ignoring http load balancer factory of type {} for client to {} which has " +
                    "DefaultLoadBalancer enabled.", loadBalancerFactory.getClass(), serviceName);
            return this;
        }
    }
}
