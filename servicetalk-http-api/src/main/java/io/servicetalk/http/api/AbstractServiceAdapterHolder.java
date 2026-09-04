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
package io.servicetalk.http.api;

import io.servicetalk.http.api.HttpApiConversions.ServiceAdapterHolder;

import static io.servicetalk.http.api.HttpContextKeys.INTERRUPT_BLOCKING_SERVICE_ON_CANCEL;

abstract class AbstractServiceAdapterHolder implements StreamingHttpService, ServiceAdapterHolder {

    private final HttpExecutionStrategy serviceInvocationStrategy;

    protected AbstractServiceAdapterHolder(final HttpExecutionStrategy serviceInvocationStrategy) {
        this.serviceInvocationStrategy = serviceInvocationStrategy;
    }

    /**
     * Resolves {@link HttpContextKeys#INTERRUPT_BLOCKING_SERVICE_ON_CANCEL}. Must be invoked on the request thread:
     * the request {@link io.servicetalk.context.api.ContextMap} is not thread-safe and is written to elsewhere on
     * the response path, so it must not be read from the thread that signals cancellation.
     *
     * @param request the request whose context carries the value
     * @return {@code true} if the service thread should be interrupted on cancellation
     */
    static boolean interruptOnCancel(final HttpRequestMetaData request) {
        final Boolean interrupt = request.context().get(INTERRUPT_BLOCKING_SERVICE_ON_CANCEL);
        return interrupt == null || interrupt;
    }

    @Override
    public StreamingHttpService adaptor() {
        return this;
    }

    @Override
    public HttpExecutionStrategy serviceInvocationStrategy() {
        return serviceInvocationStrategy;
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return serviceInvocationStrategy;
    }
}
