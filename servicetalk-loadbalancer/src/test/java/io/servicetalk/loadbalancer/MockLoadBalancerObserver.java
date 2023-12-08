/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

interface MockLoadBalancerObserver extends LoadBalancerObserver<String> {

    interface MockHostObserver extends LoadBalancerObserver.HostObserver<String> {

    }

    interface MockOutlierObserver extends LoadBalancerObserver.OutlierObserver<String> {

    }

    static MockLoadBalancerObserver mockObserver() {
        MockHostObserver mockHostObserver = mock(MockHostObserver.class);
        MockOutlierObserver mockOutlierObserver = mock(MockOutlierObserver.class);
        MockLoadBalancerObserver loadBalancerObserver = mock(MockLoadBalancerObserver.class);
        when(loadBalancerObserver.outlierEventObserver()).thenReturn(mockOutlierObserver);
        when(loadBalancerObserver.hostObserver()).thenReturn(mockHostObserver);
        return loadBalancerObserver;
    }
}
