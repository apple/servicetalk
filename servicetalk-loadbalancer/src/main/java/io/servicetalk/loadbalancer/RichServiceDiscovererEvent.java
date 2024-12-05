/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ServiceDiscovererEvent;

import java.util.Objects;

import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static java.util.Objects.requireNonNull;

/**
 * A richer {@link ServiceDiscovererEvent} that can carry weight and priority information.
 * @param <ResolvedAddress> the type of the resolved address.
 */
final class RichServiceDiscovererEvent<ResolvedAddress> implements ServiceDiscovererEvent<ResolvedAddress> {

    private final ResolvedAddress address;
    private final Status status;
    private final double weight;
    private final int priority;

    RichServiceDiscovererEvent(ResolvedAddress address, Status status, double weight, int priority) {
        if (weight < 0d) {
            throw new IllegalArgumentException("Illegal weight: " + weight);
        }
        this.address = requireNonNull(address, "address");
        this.status = requireNonNull(status, "status");
        this.weight = weight;
        this.priority = ensureNonNegative(priority, "priority");
    }

    @Override
    public ResolvedAddress address() {
        return address;
    }

    @Override
    public Status status() {
        return status;
    }

    /**
     * The relative weight this endpoint should be given for load balancing decisions.
     * @return the relative weight this endpoint should be given for load balancing decisions.
     */
    public double loadBalancingWeight() {
        return weight;
    }

    /**
     * Priority group this endpoint belongs to.
     * @return the priority group this endpoint belongs to.
     */
    public int priority() {
        return priority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RichServiceDiscovererEvent<?> that = (RichServiceDiscovererEvent<?>) o;
        return Double.compare(that.weight, weight) == 0 && priority == that.priority &&
                address.equals(that.address) && status.equals(that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, status, weight, priority);
    }

    @Override
    public String toString() {
        return "RichServiceDiscovererEvent{" +
                "address=" + address +
                ", status=" + status +
                ", weight=" + weight +
                ", priority=" + priority +
                '}';
    }
}
