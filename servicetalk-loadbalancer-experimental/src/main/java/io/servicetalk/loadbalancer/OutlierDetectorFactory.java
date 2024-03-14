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

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.concurrent.api.Executor;

/**
 * A factory of {@link OutlierDetector} instances. The factory will be used by load balancer
 * builders and may make more than one health checker per-load balancer.
 * @param <ResolvedAddress> the type of the resolved address.
 * @param <C> the type of the load balanced connection.
 */
public interface OutlierDetectorFactory<ResolvedAddress, C extends LoadBalancedConnection> {
    /**
     * Create a new {@link OutlierDetector}.
     * @param executor the {@link Executor} to use for scheduling tasks and obtaining the current time.
     * @param lbDescription a description of the load balancer for logging purposes.
     * @return a new {@link OutlierDetector}.
     */
    OutlierDetector<ResolvedAddress, C> newOutlierDetector(Executor executor, String lbDescription);
}
