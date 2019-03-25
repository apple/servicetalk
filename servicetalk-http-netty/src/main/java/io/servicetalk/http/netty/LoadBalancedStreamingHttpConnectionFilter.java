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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.internal.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.http.api.ReservedStreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;

import static java.util.Objects.requireNonNull;

/**
 * Makes the wrapped {@link StreamingHttpConnection} aware of the {@link LoadBalancer}.
 */
final class LoadBalancedStreamingHttpConnectionFilter extends ReservedStreamingHttpConnectionFilter
        implements ReservableRequestConcurrencyController {
    private final ReservableRequestConcurrencyController limiter;

    LoadBalancedStreamingHttpConnectionFilter(StreamingHttpConnectionFilter delegate,
                                              ReservableRequestConcurrencyController limiter) {
        super(delegate);
        this.limiter = requireNonNull(limiter);
    }

    @Override
    public boolean tryReserve() {
        return limiter.tryReserve();
    }

    @Override
    public Result tryRequest() {
        return limiter.tryRequest();
    }

    @Override
    public void requestFinished() {
        limiter.requestFinished();
    }

    @Override
    public Completable releaseAsync() {
        return limiter.releaseAsync();
    }
}
