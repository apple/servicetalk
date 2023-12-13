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
        when(host.isUnhealthy(anyBoolean())).thenReturn(false);
        when(host.isActive()).thenReturn(true);
        when(host.pickConnection(any(), any())).thenReturn(connection);
        when(host.newConnection(any(), anyBoolean(), any())).thenReturn(Single.succeeded(connection));
        return host;
    }
}
