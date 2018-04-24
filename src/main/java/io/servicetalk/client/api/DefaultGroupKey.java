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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.transport.api.IoExecutor;

import static java.util.Objects.requireNonNull;

/**
 * A default implementation of {@link GroupKey}.
 */
public final class DefaultGroupKey<Address> implements GroupKey<Address> {

    private final Address address;
    private final IoExecutor ioExecutor;
    private final Executor executor;

    /**
     * Create a new instance.
     *
     * @param address The address of the remote peer to connect to.
     * @param ioExecutor The {@link IoExecutor} to used in {@link #getIoExecutor()}.
     * @param executor The {@link Executor} to used in {@link #getExecutor()}.
     */
    public DefaultGroupKey(final Address address, final IoExecutor ioExecutor, final Executor executor) {
        this.address = requireNonNull(address);
        this.ioExecutor = requireNonNull(ioExecutor);
        this.executor = requireNonNull(executor);
    }

    @Override
    public Address getAddress() {
        return address;
    }

    @Override
    public IoExecutor getIoExecutor() {
        return ioExecutor;
    }

    @Override
    public Executor getExecutor() {
        return executor;
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

        return address.equals(that.address) && ioExecutor.equals(that.ioExecutor) && executor.equals(that.executor);
    }

    @Override
    public int hashCode() {
        int result = address.hashCode();
        result = 31 * result + ioExecutor.hashCode();
        result = 31 * result + executor.hashCode();
        return result;
    }
}
