package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.servicetalk.utils.internal.ServiceLoaderUtils.loadProviders;

    // TODO: docs
public final class LoadBalancers {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadBalancers.class);

    interface LoadBalancerBuilderProvider {
        <ResolvedAddress, C extends LoadBalancedConnection> LoadBalancerBuilder<ResolvedAddress, C>
        newBuilder(String id, LoadBalancerBuilder<ResolvedAddress, C> builder);
    }

    private static final List<LoadBalancerBuilderProvider> PROVIDERS;

    static {
        final ClassLoader classLoader = LoadBalancers.class.getClassLoader();
        PROVIDERS = loadProviders(LoadBalancerBuilderProvider.class, classLoader, LOGGER);
    }

    private static <ResolvedAddress, C extends LoadBalancedConnection>
    LoadBalancerBuilder<ResolvedAddress, C> applyProviders(String id, LoadBalancerBuilder<ResolvedAddress, C> builder) {
        for (LoadBalancerBuilderProvider provider : PROVIDERS) {
            builder = provider.newBuilder(id, builder);
        }
        return builder;
    }

    public static <ResolvedAddress, C extends LoadBalancedConnection>
    LoadBalancerBuilder<ResolvedAddress, C> builder(final String id) {
        return applyProviders(id, new LoadBalancerBuilder<>(id));
    }
}
