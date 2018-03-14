/**
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

import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.function.BiPredicate;

import static java.util.Objects.requireNonNull;

/**
 * A POJO for holding a {@link BiPredicate} to be evaluated on the request and connection context, and an {@link HttpService}
 * to be called if the predicate matches.
 * @param <I> the type of the content in the {@link HttpRequest}s
 * @param <O> the type of the content in the {@link HttpResponse}s
 */
final class PredicateServicePair<I, O> {

    private final BiPredicate<ConnectionContext, HttpRequest<I>> predicate;
    private final HttpService<I, O> service;

    /**
     * Constructs a {@link PredicateServicePair} POJO.
     * @param predicate the {@link BiPredicate} to evaluate.
     * @param service the {@link HttpService} to route to.
     */
    PredicateServicePair(final BiPredicate<ConnectionContext, HttpRequest<I>> predicate, final HttpService<I, O> service) {
        this.predicate = requireNonNull(predicate);
        this.service = requireNonNull(service);
    }

    /**
     * Get the predicate.
     * @return the predicate.
     */
    BiPredicate<ConnectionContext, HttpRequest<I>> getPredicate() {
        return predicate;
    }

    /**
     * Get the service.
     * @return the service.
     */
    HttpService<I, O> getService() {
        return service;
    }
}
