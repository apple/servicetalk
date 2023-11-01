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
import io.servicetalk.context.api.ContextMap;

import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface abstracting away the method of host selection.
 */
interface HostSelector<ResolvedAddress, C extends LoadBalancedConnection> {

    /**
     * Select or establish a new connection from an existing Host.
     *
     * This method will be called concurrently with other selectConnection calls and
     * hostSetChanged calls and must be thread safe under those conditions.
     */
    Single<C> selectConnection(@Nonnull List<Host<ResolvedAddress, C>> hosts, @Nonnull Predicate<C> selector,
                               @Nullable ContextMap context, boolean forceNewConnectionAndReserve);
}
