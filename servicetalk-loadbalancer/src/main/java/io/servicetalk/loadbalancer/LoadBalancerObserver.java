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

import io.servicetalk.client.api.NoActiveHostException;

interface LoadBalancerObserver<ResolvedAddress> {

    HostObserver<ResolvedAddress> hostObserver();

    OutlierObserver<ResolvedAddress> outlierEventObserver();

    void noHostsAvailable();

    void noActiveHostsAvailable(int hostSetSize, NoActiveHostException exn);

    interface HostObserver<ResolvedAddress> {
        void hostMarkedExpired(ResolvedAddress address, int connectionCount);

        void expiredHostRemoved(ResolvedAddress address);

        void expiredHostRevived(ResolvedAddress address, int connectionCount);

        void unavailableHostRemoved(ResolvedAddress address, int connectionCount);

        void hostCreated(ResolvedAddress address);
    }

    interface OutlierObserver<ResolvedAddress> {
        void hostMarkedUnhealthy(ResolvedAddress address, Throwable cause);

        void hostRevived(ResolvedAddress address);
    }
}
