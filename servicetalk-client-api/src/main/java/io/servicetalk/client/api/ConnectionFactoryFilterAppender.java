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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.transport.api.ExecutionStrategy;
import static java.util.Objects.requireNonNull;

public class ConnectionFactoryFilterAppender<ResolvedAddress, C extends ListenableAsyncCloseable>
        implements ConnectionFactoryFilter<ResolvedAddress, C> {

    private final ConnectionFactoryFilter<ResolvedAddress, C> first;
    private final ConnectionFactoryFilter<ResolvedAddress, C> second;
    private final ExecutionStrategy executionStrategy;

    public ConnectionFactoryFilterAppender(final ConnectionFactoryFilter<ResolvedAddress, C> first,
                                           final ConnectionFactoryFilter<ResolvedAddress, C> second) {
        this.first = requireNonNull(first);
        this.second = requireNonNull(second);
        executionStrategy = first.requiredOffloads().merge(second.requiredOffloads());
    }

    @Override
    public ConnectionFactory<ResolvedAddress, C> create(final ConnectionFactory<ResolvedAddress, C> service) {
        return first.create(second.create(
                new DeprecatedToNewConnectionFactoryFilter<ResolvedAddress, C>().create(service)));
    }

    @Override
    public ExecutionStrategy requiredOffloads() {
        return executionStrategy;
    }
}
