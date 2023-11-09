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
import io.servicetalk.concurrent.api.Single;

import java.util.List;

import static io.servicetalk.concurrent.api.Single.failed;
import static java.util.Objects.requireNonNull;

abstract class BaseHostSelector<ResolvedAddress, C extends LoadBalancedConnection>
        implements HostSelector<ResolvedAddress, C> {

    private final String targetResource;
    BaseHostSelector(final String targetResource) {
        this.targetResource = requireNonNull(targetResource, "targetResource");
    }

    protected final String getTargetResource() {
        return targetResource;
    }

    protected final Single<C> noActiveHosts(List<Host<ResolvedAddress, C>> usedHosts) {
        return failed(Exceptions.StacklessNoActiveHostException.newInstance("Failed to pick an active host for " +
                        getTargetResource() + ". Either all are busy, expired, or unhealthy: " + usedHosts,
                this.getClass(), "selectConnection(...)"));
    }
}
