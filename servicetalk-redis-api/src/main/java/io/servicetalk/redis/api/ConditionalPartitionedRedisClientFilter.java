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
package io.servicetalk.redis.api;

import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.Publisher.failed;

final class ConditionalPartitionedRedisClientFilter extends PartitionedRedisClientFilter {
    private final Predicate<RedisRequest> predicate;
    private final PartitionedRedisClient predicatedClient;

    ConditionalPartitionedRedisClientFilter(final Predicate<RedisRequest> predicate,
                                            final PartitionedRedisClient predicatedClient,
                                            final PartitionedRedisClient client) {
        super(client);
        this.predicate = predicate;
        this.predicatedClient = predicatedClient;
    }

    @Override
    public Publisher<RedisData> request(final RedisExecutionStrategy strategy,
                                        final PartitionAttributes partitionSelector,
                                        final RedisRequest req) {
        boolean b;
        try {
            b = predicate.test(req);
        } catch (Throwable t) {
            return failed(new RuntimeException("Unexpected predicate failure", t));
        }

        if (b) {
            return predicatedClient.request(strategy, partitionSelector, req);
        }

        return delegate().request(strategy, partitionSelector, req);
    }

    @Override
    public <R> Single<R> request(final RedisExecutionStrategy strategy,
                                 final PartitionAttributes partitionSelector,
                                 final RedisRequest req,
                                 final Class<R> resType) {
        boolean b;
        try {
            b = predicate.test(req);
        } catch (Throwable t) {
            return Single.failed(new RuntimeException("Unexpected predicate failure", t));
        }

        if (b) {
            return predicatedClient.request(strategy, partitionSelector, req, resType);
        }

        return delegate().request(strategy, partitionSelector, req, resType);
    }
}
