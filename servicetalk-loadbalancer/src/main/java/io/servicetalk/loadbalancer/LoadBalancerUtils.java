/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.internal.ThrowableUtil.unknownStackTrace;

final class LoadBalancerUtils {

    static final IllegalStateException LB_CLOSED_SELECT_CNX_EXCEPTION =
            unknownStackTrace(new IllegalStateException("LoadBalancer has closed"), LoadBalancer.class,
                    "selectConnection(..)");

    static final NoAvailableHostException NO_ACTIVE_HOSTS_SELECT_CNX_EXCEPTION =
            unknownStackTrace(new NoAvailableHostException("No hosts are available to connect."),
                    AddressSelector.class, "select()");

    private LoadBalancerUtils() {  /* no instances */ }

    static RuntimeException selectAddressFailedSelectCnxException(Throwable cause) {
        return new RuntimeException("No hosts selected, failed executing selector", cause);
    }

    static RuntimeException selectConnectionFailedSelectCnxException(Throwable cause) {
        return new RuntimeException("No connection selected, failed executing selector", cause);
    }

    static RuntimeException noAvailableAddressesSelectMatchCnxException() {
        return new RuntimeException("No hosts matching predicate are available to select.");
    }

    static RuntimeException noAvailableConnectionSelectCnxException() {
        return new RuntimeException("No connections are available to select.");
    }

    static RuntimeException noAvailableConnectionSelectMatchCnxException() {
        return new RuntimeException("No connections matching predicate are available to select.");
    }

    static ListenableAsyncCloseable newCloseable(Supplier<Iterable<? extends AsyncCloseable>> closablesSupplier) {
        return AsyncCloseables.toAsyncCloseable(graceful -> {
            final CompositeCloseable cc = newCompositeCloseable().prependAll(closablesSupplier.get());
            return graceful ? cc.closeAsyncGracefully() : cc.closeAsync();
        });
    }
}
