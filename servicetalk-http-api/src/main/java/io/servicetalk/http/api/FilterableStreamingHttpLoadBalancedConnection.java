/*
 * Copyright © 2019, 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;

/**
 * A {@link FilterableStreamingHttpConnection} that supported filtering and implements the {@link
 * LoadBalancedConnection} contract.
 */
public interface FilterableStreamingHttpLoadBalancedConnection extends FilterableStreamingHttpConnection,
        LoadBalancedConnection, ReservedStreamingHttpConnection, ReservableRequestConcurrencyController {

    // FIXME: 0.43 - consider removing default implementations
    @Override
    default ReservedHttpConnection asConnection() {
        throw new UnsupportedOperationException(
                "FilterableStreamingHttpLoadBalancedConnection#asConnection() is not supported by " + getClass());
    }

    @Override
    default ReservedBlockingStreamingHttpConnection asBlockingStreamingConnection() {
        throw new UnsupportedOperationException(
                "FilterableStreamingHttpLoadBalancedConnection#asBlockingStreamingConnection() is not supported by " +
                        getClass());
    }

    @Override
    default ReservedBlockingHttpConnection asBlockingConnection() {
        throw new UnsupportedOperationException(
                "FilterableStreamingHttpLoadBalancedConnection#asBlockingConnection() is not supported by " +
                        getClass());
    }

    @Override
    default Result tryRequest() {
        throw new UnsupportedOperationException(
                "FilterableStreamingHttpLoadBalancedConnection#tryRequest() is not supported by " + getClass());
    }

    @Override
    default void requestFinished() {
        throw new UnsupportedOperationException(
                "FilterableStreamingHttpLoadBalancedConnection#requestFinished() is not supported by " + getClass());
    }

    @Override
    default boolean tryReserve() {
        throw new UnsupportedOperationException(
                "FilterableStreamingHttpLoadBalancedConnection#tryReserve() is not supported by " + getClass());
    }

    @Override
    default Completable releaseAsync() {
        return Completable.failed(new UnsupportedOperationException(
                "FilterableStreamingHttpLoadBalancedConnection#releaseAsync() is not supported by " + getClass()));
    }
}
