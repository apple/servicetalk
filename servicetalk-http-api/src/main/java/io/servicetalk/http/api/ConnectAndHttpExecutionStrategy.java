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
package io.servicetalk.http.api;

import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.api.ExecutionStrategy;

import java.util.Objects;

/**
 * Combines a {@link ConnectExecutionStrategy} and an {@link HttpExecutionStrategy}.
 */
public final class ConnectAndHttpExecutionStrategy implements ConnectExecutionStrategy, HttpExecutionStrategy {

    final ConnectExecutionStrategy connectStrategy;
    final HttpExecutionStrategy httpStrategy;

    public ConnectAndHttpExecutionStrategy(ConnectExecutionStrategy connectStrategy) {
        this(connectStrategy, HttpExecutionStrategies.anyStrategy());
    }

    public ConnectAndHttpExecutionStrategy(HttpExecutionStrategy httpStrategy) {
        this(ConnectExecutionStrategy.anyStrategy(), httpStrategy);
    }

    public ConnectAndHttpExecutionStrategy(ConnectExecutionStrategy connect, HttpExecutionStrategy http) {
        this.connectStrategy = connect;
        this.httpStrategy = http;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ConnectAndHttpExecutionStrategy that = (ConnectAndHttpExecutionStrategy) o;
        return connectStrategy.equals(that.connectStrategy) && httpStrategy.equals(that.httpStrategy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectStrategy, httpStrategy);
    }

    @Override
    public String toString() {
        return connectStrategy + " " + httpStrategy;
    }

    @Override
    public boolean hasOffloads() {
        return connectStrategy.hasOffloads() || httpStrategy.hasOffloads();
    }

    @Override
    public boolean isMetadataReceiveOffloaded() {
        return httpStrategy.isMetadataReceiveOffloaded();
    }

    @Override
    public boolean isDataReceiveOffloaded() {
        return httpStrategy.isDataReceiveOffloaded();
    }

    @Override
    public boolean isSendOffloaded() {
        return httpStrategy.isSendOffloaded();
    }

    @Override
    public boolean isEventOffloaded() {
        return httpStrategy.isEventOffloaded();
    }

    @Override
    public boolean isConnectOffloaded() {
        return connectStrategy.isConnectOffloaded();
    }

    @Override
    public ConnectAndHttpExecutionStrategy merge(final ExecutionStrategy other) {
        if (other instanceof ConnectAndHttpExecutionStrategy) {
            return merge((ConnectAndHttpExecutionStrategy) other);
        } else if (other instanceof HttpExecutionStrategy) {
            return merge((HttpExecutionStrategy) other);
        } else if (other instanceof ConnectExecutionStrategy) {
            return merge((ConnectExecutionStrategy) other);
        } else {
            return other.hasOffloads() ?
                    new ConnectAndHttpExecutionStrategy(
                            ConnectExecutionStrategy.offload(),
                            HttpExecutionStrategies.offloadAll())
                    : this;
        }
    }

    private ConnectAndHttpExecutionStrategy merge(final ConnectExecutionStrategy other) {
        ConnectExecutionStrategy merged = connectStrategy.merge(other);
        return merged == connectStrategy ? this : new ConnectAndHttpExecutionStrategy(merged, httpStrategy);
    }

    @Override
    public ConnectAndHttpExecutionStrategy merge(final HttpExecutionStrategy other) {
        HttpExecutionStrategy merged = httpStrategy.merge(other);
        return merged == httpStrategy ? this : new ConnectAndHttpExecutionStrategy(connectStrategy, merged);
    }

    private ConnectAndHttpExecutionStrategy merge(final ConnectAndHttpExecutionStrategy other) {
        ConnectExecutionStrategy mergedConnect = connectStrategy.merge(other);
        HttpExecutionStrategy mergedHttp = httpStrategy.merge(other);
        return mergedConnect == connectStrategy && mergedHttp == httpStrategy ?
                this : new ConnectAndHttpExecutionStrategy(mergedConnect, mergedHttp);
    }

    /**
     * Returns the {@link HttpExecutionStrategy} portion of this strategy.
     *
     * @return the {@link HttpExecutionStrategy} portion of this strategy.
     */
    public HttpExecutionStrategy httpStrategy() {
        return httpStrategy;
    }

    /**
     * Returns the {@link ConnectExecutionStrategy} portion of this strategy.
     *
     * @return the {@link ConnectExecutionStrategy} portion of this strategy.
     */
    public ConnectExecutionStrategy connectStrategy() {
        return connectStrategy;
    }

    /**
     * Converts the provided execution strategy to a {@link ConnectExecutionStrategy}. If the provided strategy is
     * already {@link ConnectExecutionStrategy} it is returned unchanged. For other strategies, if the strategy
     * {@link ExecutionStrategy#hasOffloads()} then {@link ConnectExecutionStrategy#offload()} is returned otherwise
     * {@link ConnectExecutionStrategy#anyStrategy()} is returned.
     *
     * @param executionStrategy The {@link ExecutionStrategy} to convert
     * @return converted {@link ConnectExecutionStrategy}.
     */
    public static ConnectAndHttpExecutionStrategy from(ExecutionStrategy executionStrategy) {
        return executionStrategy instanceof ConnectAndHttpExecutionStrategy ?
                (ConnectAndHttpExecutionStrategy) executionStrategy :
                    new ConnectAndHttpExecutionStrategy(
                            ConnectExecutionStrategy.anyStrategy(), HttpExecutionStrategies.defaultStrategy())
                            .merge(executionStrategy);
    }
}
