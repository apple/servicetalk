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
package io.servicetalk.http.router.predicate;

import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.function.BiPredicate;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A POJO for holding a {@link BiPredicate} to be evaluated on the request and connection context, and an
 * {@link StreamingHttpService} to be called if the predicate matches.
 */
final class PredicateServicePair {

    private final BiPredicate<ConnectionContext, StreamingHttpRequest> predicate;
    private final StreamingHttpService service;
    @Nullable
    private final HttpExecutionStrategy routeStrategy;

    /**
     * Constructs a {@link PredicateServicePair} POJO.
     *
     * @param predicate the {@link BiPredicate} to evaluate.
     * @param service the {@link StreamingHttpService} to route to.
     */
    PredicateServicePair(final BiPredicate<ConnectionContext, StreamingHttpRequest> predicate,
                         final StreamingHttpService service, @Nullable final HttpExecutionStrategy strategy) {
        this.predicate = requireNonNull(predicate);
        this.service = requireNonNull(service);
        routeStrategy = strategy;
    }

    /**
     * Returns the predicate.
     *
     * @return the predicate.
     */
    BiPredicate<ConnectionContext, StreamingHttpRequest> predicate() {
        return predicate;
    }

    /**
     * Returns the service.
     *
     * @return the service.
     */
    StreamingHttpService service() {
        return service;
    }

    @Nullable
    HttpExecutionStrategy routeStrategy() {
        return routeStrategy;
    }
}
