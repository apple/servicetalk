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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.transport.api.IoExecutor;

import java.util.function.Function;

import static io.servicetalk.redis.netty.DefaultRedisConnectionBuilder.buildForPipelined;
import static io.servicetalk.redis.netty.DefaultRedisConnectionBuilder.buildForSubscribe;
import static java.util.Objects.requireNonNull;

final class DefaultRedisConnectionFactory<ResolvedAddress> implements ConnectionFactory<ResolvedAddress, LoadBalancedRedisConnection> {

    private final CompletableProcessor onClose = new CompletableProcessor();
    private final ReadOnlyRedisClientConfig config;
    private final IoExecutor executor;
    private final boolean forSubscribe;
    private final Function<RedisConnection, RedisConnection> connectionFilterFactory;
    private final Completable closeAsync = new Completable() {
        @Override
        protected void handleSubscribe(Subscriber subscriber) {
            onClose.onComplete();
            onClose.subscribe(subscriber);
        }
    };

    DefaultRedisConnectionFactory(ReadOnlyRedisClientConfig config, IoExecutor executor, boolean forSubscribe,
                                  Function<RedisConnection, RedisConnection> connectionFilterFactory) {
        this.config = config;
        this.executor = executor;
        this.forSubscribe = forSubscribe;
        this.connectionFilterFactory = connectionFilterFactory;
    }

    @Override
    public Single<LoadBalancedRedisConnection> newConnection(ResolvedAddress address) {
        return (forSubscribe ? buildForSubscribe(executor, address, config) : buildForPipelined(executor, address, config))
                .map(this::newConnection);
    }

    private LoadBalancedRedisConnection newConnection(RedisConnection conn) {
        return new LoadBalancedRedisConnection(requireNonNull(connectionFilterFactory.apply(conn)));
    }

    @Override
    public Completable onClose() {
        return onClose;
    }

    @Override
    public Completable closeAsync() {
        return closeAsync;
    }
}
