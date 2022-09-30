/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Collection;

class GrpcClientsCompileTest {
    private static final String IGNORE_ADDRESS = "";

    @Test
    void testGrpcClientsAcceptsCustomServiceDiscovererEvents() {
        GrpcClients.forAddress(new NullServiceDiscoverer<ServiceDiscovererEvent<InetSocketAddress>>(), IGNORE_ADDRESS);
        GrpcClients.forAddress(new NullServiceDiscoverer<CustomServiceDiscovererEvent>(), IGNORE_ADDRESS);
    }

    private interface CustomServiceDiscovererEvent extends ServiceDiscovererEvent<InetSocketAddress> { }

    private static final class NullServiceDiscoverer<E extends ServiceDiscovererEvent<InetSocketAddress>>
            implements ServiceDiscoverer<String, InetSocketAddress, E> {

        @Override
        public Publisher<Collection<E>> discover(final String inetSocketAddress) {
            return Publisher.empty();
        }

        @Override
        public Completable onClose() {
            return Completable.completed();
        }

        @Override
        public Completable closeAsync() {
            return Completable.completed();
        }
    }
}
