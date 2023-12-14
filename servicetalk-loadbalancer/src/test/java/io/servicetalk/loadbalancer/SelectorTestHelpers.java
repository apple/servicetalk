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

import io.servicetalk.concurrent.api.Single;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class SelectorTestHelpers {

    static final Predicate<TestLoadBalancedConnection> PREDICATE = (ignored) -> true;

    private SelectorTestHelpers() {
    }

    static List<Host<String, TestLoadBalancedConnection>> connections(String... addresses) {
        final List<Host<String, TestLoadBalancedConnection>> results = new ArrayList<>(addresses.length);
        for (String addr : addresses) {
            results.add(mockHost(addr, TestLoadBalancedConnection.mockConnection(addr)));
        }
        return results;
    }

    private static Host mockHost(String addr, TestLoadBalancedConnection connection) {
        Host<String, TestLoadBalancedConnection> host = mock(Host.class);
        when(host.address()).thenReturn(addr);
        when(host.status(anyBoolean())).thenReturn(Host.Status.HEALTHY_ACTIVE);
        when(host.pickConnection(any(), any())).thenReturn(connection);
        when(host.newConnection(any(), anyBoolean(), any())).thenReturn(Single.succeeded(connection));
        return host;
    }
}
