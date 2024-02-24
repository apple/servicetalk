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
package io.servicetalk.examples.http.defaultloadbalancer;

import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.netty.DefaultHttpLoadBalancerFactory;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.loadbalancer.LoadBalancers;
import io.servicetalk.loadbalancer.OutlierDetectorConfig;
import io.servicetalk.loadbalancer.XdsHealthCheckerFactory;
import io.servicetalk.transport.api.HostAndPort;

import java.net.InetSocketAddress;

import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

public final class DefaultLoadBalancerClient {

    public static void main(String[] args) throws Exception {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080)
                .loadBalancerFactory(DefaultHttpLoadBalancerFactory.Builder.from(
                        loadBalancer("localhost-defaultloadbalancer")).build());
        try (BlockingHttpClient client = builder.buildBlocking()) {
            HttpResponse response = client.request(client.get("/sayHello"));
            System.out.println(response.toString((name, value) -> value));
            System.out.println(response.payloadBody(textSerializerUtf8()));
        }
    }

    private static LoadBalancerFactory<InetSocketAddress, FilterableStreamingHttpLoadBalancedConnection> loadBalancer(
            String id) {
        return LoadBalancers.<InetSocketAddress, FilterableStreamingHttpLoadBalancedConnection>
                builder(id)
                .healthCheckerFactory(new XdsHealthCheckerFactory<>(
                        new OutlierDetectorConfig.Builder().build()
                    )).build();
    }
}
