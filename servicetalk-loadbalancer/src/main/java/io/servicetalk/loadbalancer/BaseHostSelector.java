package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.concurrent.api.Single;

import java.util.List;

import static io.servicetalk.concurrent.api.Single.failed;
import static java.util.Objects.requireNonNull;

abstract class BaseHostSelector<ResolvedAddress, C extends LoadBalancedConnection> implements HostSelector<ResolvedAddress, C> {

    private final String targetResource;
    BaseHostSelector(final String targetResource) {
        this.targetResource = requireNonNull(targetResource, "targetResource");
    }

    final protected String getTargetResource() {
        return targetResource;
    }

    final protected Single<C> noActiveHostsException(List<Host<ResolvedAddress, C>> usedHosts) {
        return failed(Exceptions.StacklessNoActiveHostException.newInstance("Failed to pick an active host for " +
                        getTargetResource() + ". Either all are busy, expired, or unhealthy: " + usedHosts,
                this.getClass(), "selectConnection(...)"));
    }
}
