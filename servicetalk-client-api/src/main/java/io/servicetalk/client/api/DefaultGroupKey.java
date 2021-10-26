/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ExecutionContext;

import static java.util.Objects.requireNonNull;

/**
 * A default implementation of {@link GroupKey}.
 *
 * @param <Address> The type of address used by clients (typically this is unresolved address).
 */
public final class DefaultGroupKey<Address> implements GroupKey<Address> {

    private final Address address;
    private final ExecutionContext<?> executionContext;

    /**
     * Create a new instance.
     *
     * @param address The address of the remote peer to connect to.
     * @param executionContext The {@link ExecutionContext} to use for {@link #executionContext()}.
     */
    public DefaultGroupKey(final Address address,
                           final ExecutionContext<?> executionContext) {
        this.address = requireNonNull(address);
        this.executionContext = requireNonNull(executionContext);
    }

    @Override
    public Address address() {
        return address;
    }

    @Override
    public ExecutionContext<?> executionContext() {
        return executionContext;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultGroupKey<?> that = (DefaultGroupKey<?>) o;

        return address.equals(that.address) && executionContext.equals(that.executionContext);
    }

    @Override
    public int hashCode() {
        return 31 * address.hashCode() + executionContext.hashCode();
    }
}
