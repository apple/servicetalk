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

    /**
     * Creates the standard load balancer builder with reasonable defaults that can then be customized.
     * @param id
     * @return
     * @param <ResolvedAddress>
     * @param <C>
     */
    public static <ResolvedAddress, C extends LoadBalancedConnection>
    LoadBalancerBuilder<ResolvedAddress, C> builder(final String id) {
        return applyProviders(id, new LoadBalancerBuilder<>(id));
    }


    // TODO: if we want to offer this as an API pattern, we might want to let providers set a smart
    //  round-robin specific set of defaults (and p2c, etc). _If_ we want to go down this route we
    //  can send the providers an enum or something like it. Alternatively, we can make the current
    //  builder inspectable, something like `getSelector()` so we can set the round-robin lb and
    //  providers can then check if a selector is set and if so modify that specifically.
    /**
     * Creates a standard round-robin load balancer with reasonable defaults.
     * @param id
     * @return
     * @param <ResolvedAddress>
     * @param <C>
     */
    public static <ResolvedAddress, C extends LoadBalancedConnection>
    LoadBalancerBuilder<ResolvedAddress, C> roundRobin(final String id) {
        return applyProviders(id, new LoadBalancerBuilder<>(id));
    }
}
