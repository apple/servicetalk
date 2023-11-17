package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

    // TODO: docs
final class LoadBalancers {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadBalancers.class);

    private static final List<LoadBalancerBuilderProvider> PROVIDERS;

    static {
        // TODO: we can service load the providers until we make the interface public.
        PROVIDERS = new ArrayList<>();
    }

    private static <ResolvedAddress, C extends LoadBalancedConnection>
    LoadBalancerBuilder<ResolvedAddress, C> applyProviders(String id, LoadBalancerBuilder<ResolvedAddress, C> builder) {
        for (LoadBalancerBuilderProvider provider : PROVIDERS) {
            builder = provider.newBuilder(id, builder);
        }
        return builder;
    }

    /**
     * Creates the standard load balancer builder with reasonable defaults that can then be customized.
     * @param id
     * @return
     * @param <ResolvedAddress>
     * @param <C>
     */
    public static <ResolvedAddress, C extends LoadBalancedConnection>
    LoadBalancerBuilder<ResolvedAddress, C> builder(final String id) {
        return applyProviders(id, new DefaultLoadBalancerBuilder<>(id));
    }
}
