/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.transport.api.TransportObserver;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.annotation.Nullable;

class TestConnectionFactory implements
        ConnectionFactory<String, TestLoadBalancedConnection> {

    private final Function<String, Single<TestLoadBalancedConnection>> connectionFactory;
    private final AtomicBoolean closed = new AtomicBoolean();

    TestConnectionFactory(Function<String, Single<TestLoadBalancedConnection>> connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Single<TestLoadBalancedConnection> newConnection(String address, TransportObserver observer) {
        return connectionFactory.apply(address);
    }

    @Override
    public Single<TestLoadBalancedConnection> newConnection(String address, @Nullable ContextMap context,
                                                            @Nullable TransportObserver observer) {
        return connectionFactory.apply(address);
    }

    @Override
    public Completable onClose() {
        return Completable.completed();
    }

    @Override
    public Completable closeAsync() {
        return Completable.completed().beforeOnSubscribe(cancellable -> closed.set(true));
    }

    boolean isClosed() {
        return closed.get();
    }
}
