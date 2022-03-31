/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.client.api;

import io.servicetalk.context.api.ContextMap;

import java.util.function.Predicate;

/**
 * A hint from {@link LoadBalancer#eventStream()} that the internal state of the {@link LoadBalancer} is ready such
 * {@link LoadBalancer#selectConnection(Predicate, ContextMap)} is not likely to fail. Note that the return status of
 * {@link LoadBalancer#selectConnection(Predicate, ContextMap)} may depend upon many factors including but not limited
 * to:
 * <ul>
 *     <li>Instantaneous demand vs the amount of resources (e.g. connections) on hand</li>
 *     <li>If the {@link LoadBalancer} favors queuing requests or "fail fast" behavior</li>
 *     <li>The dynamic nature of host availability may result in no hosts being available</li>
 * </ul>
 * This is meant to emphasize that {@link #isReady()} returning {@code true} doesn't necessarily mean
 * {@link LoadBalancer#selectConnection(Predicate, ContextMap)} will always return successfully.
 */
public interface LoadBalancerReadyEvent {
    /**
     * A {@link LoadBalancerReadyEvent} that returns {@code true} for {@link #isReady()}.
     */
    LoadBalancerReadyEvent LOAD_BALANCER_READY_EVENT = () -> true;

    /**
     * A {@link LoadBalancerReadyEvent} that returns {@code false} for {@link #isReady()}.
     */
    LoadBalancerReadyEvent LOAD_BALANCER_NOT_READY_EVENT = () -> false;

    /**
     * A hint which can be used to determine if the {@link LoadBalancer} is "ready".
     * @return {@code true} if there were no hosts available and now there are hosts available. {@code false}
     * if there were hosts available and now there are no hosts available.
     */
    boolean isReady();
}
