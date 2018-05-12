/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.redis.netty;

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.redis.api.RedisClient;

/**
 * A factory that applies filters to {@link RedisClient}s.
 */
public interface RedisClientFilterFactory {
    /**
     * Apply filters to a {@link RedisClient}.
     * @param client The {@link RedisClient} before filtering.
     * @param subscribeLoadBalancerEvents The {@link LoadBalancer#getEventStream()} for the load balancer responsible
     * for pub/sub commands.
     * @param pipelinedLoadBalancerEvents The {@link LoadBalancer#getEventStream()} for the load balancer responsible
     * for non-pub/sub commands.
     * @return The filtered {@link RedisClient}.
     */
    RedisClient apply(RedisClient client,
                      Publisher<Object> subscribeLoadBalancerEvents,
                      Publisher<Object> pipelinedLoadBalancerEvents);
}
