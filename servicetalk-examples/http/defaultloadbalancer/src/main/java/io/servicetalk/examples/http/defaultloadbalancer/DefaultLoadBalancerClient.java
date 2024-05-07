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
import io.servicetalk.http.api.DefaultHttpLoadBalancerFactory;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.loadbalancer.LoadBalancers;
import io.servicetalk.loadbalancer.OutlierDetectorConfig;
import io.servicetalk.loadbalancer.P2CLoadBalancingPolicy;
import io.servicetalk.transport.api.HostAndPort;

import java.net.InetSocketAddress;

import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static java.time.Duration.ofSeconds;

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
                .loadBalancingPolicy(
                        // DefaultLoadBalancer supports multiple patterns for selecting hosts including
                        // - Round robin: linear iteration through the host list, skipping unhealthy hosts, per request.
                        // - Power of two choices (P2C): randomly select two hosts and take the best based on score.
                        // Both policies consider outlier detection (see below) but only P2C can bias traffic toward
                        // more performant hosts. It does this by tracking request latency per-host using an
                        // exponentially weighted moving average (EWMA) and using that combined with outstanding
                        // request count to score hosts. The net result is typically a traffic distribution that will
                        // show a preference toward faster hosts while also rapidly adjust to changes in backend
                        // performance.
                        new P2CLoadBalancingPolicy.Builder()
                                // Set the max effort (default: 5). This is the number of times P2C will pick a random
                                // pair of hosts in search of a healthy host before giving up. When it gives up it will
                                // either attempt to use one of the hosts regardless of status if `failOpen == true` or
                                // return a `NoActiveHosts` exception if failOpen == false.
                                .maxEffort(6)
                                // Whether to try to use a host regardless of health status (default: false)
                                .failOpen(true)
                                .build())
                .outlierDetectorConfig(
                        // xDS compatible outlier detection has a number of tuning knobs. There are multiple detection
                        // algorithms describe in more detail below. In addition to the limits appropriate to each
                        // algorithm there are also parameters to tune the chances of enforcement when a particular
                        // threshold is hit, the general intervals for periodic detection, penalty durations, etc.
                        // See the `OutlierDetectorConfig` documentation for specific details.
                        // The three primary detection mechanisms are:
                        // - consecutive 5xx: Immediately mark a host as unhealthy if the specified number of failures
                        //     happen in a row (subject to constraints). Good for rapid detection of failing hosts.
                        // - success rate: statistical outlier detection pattern that runs on the detection interval.
                        //     Good for true outlier detection but not as easy to reason about as failure percentage.
                        // - failure percentage: Set a static limit on the percentage of requests that can fail before a
                        //     host is considered unhealthy. Simple to understand and correlates well to common metrics
                        //     but not as dynamic as success rate.
                        // In the context of the above, failures are considered exceptions (commonly failure to
                        // establish a connection, closed channel, etc) and HTTP responses with status 5xx and
                        // 429 TOO MANY REQUESTS. In the future the classification of responses will be configurable.
                        new OutlierDetectorConfig.Builder()
                                // set the interval to 30 seconds (default: to 10 seconds)
                                .failureDetectorInterval(ofSeconds(30))
                                // set a more aggressive consecutive failure policy (default: 5)
                                .consecutive5xx(3)
                                // enabled failure percentage detection (default: 0)
                                .enforcingFailurePercentage(100)
                                // only allow 20% of hosts to be marked unhealthy at any one time
                                .maxEjectionPercentage(80)
                                .build()
                    ).build();
    }
}
