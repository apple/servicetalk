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

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

interface TestLoadBalancedConnection extends LoadBalancedConnection {
    String address();

    static TestLoadBalancedConnection mockConnection(final String address) {
        return mockConnection(address, emptyAsyncCloseable());
    }

    static TestLoadBalancedConnection mockConnection(final String address, final ListenableAsyncCloseable closeable) {
        final TestLoadBalancedConnection cnx = mock(TestLoadBalancedConnection.class);
        when(cnx.closeAsync()).thenReturn(closeable.closeAsync());
        when(cnx.closeAsyncGracefully()).thenReturn(closeable.closeAsyncGracefully());
        when(cnx.onClose()).thenReturn(closeable.onClose());
        when(cnx.onClosing()).thenReturn(closeable.onClosing());
        when(cnx.address()).thenReturn(address);
        when(cnx.toString()).thenReturn(address + '@' + cnx.hashCode());
        when(cnx.tryReserve()).thenReturn(true);
        return cnx;
    }
}
