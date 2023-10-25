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
import io.servicetalk.client.api.LoadBalancer;

import java.util.List;
import java.util.Map;

// An intermediate interface so we can universally expose the current list of addresses. This should become
// unnecessary once we extract the logic of managing the host list from the load balancer itself into it's
// own abstraction.
interface TestableLoadBalancer<ResolvedAddress, C extends LoadBalancedConnection> extends LoadBalancer<C> {

    List<Map.Entry<ResolvedAddress, List<C>>> usedAddresses();
}
