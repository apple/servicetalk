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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.api.StreamingHttpServiceToOffloadedStreamingHttpService;

import java.util.function.BooleanSupplier;

/**
 * An {@link StreamingHttpServiceFilterFactory} implementation which offloads filters using a provided strategy.
 */
final class OffloadingFilter implements StreamingHttpServiceFilterFactory {

    private final HttpExecutionStrategy strategy;
    private final StreamingHttpServiceFilterFactory offloaded;
    private final BooleanSupplier shouldOffload;

    /**
     * @param strategy Execution strategy for the offloaded filters
     * @param offloaded Filters to be offloaded
     * @param shouldOffload returns true if offloading is appropriate for the current execution context.
     */
    OffloadingFilter(HttpExecutionStrategy strategy, StreamingHttpServiceFilterFactory offloaded,
                     BooleanSupplier shouldOffload) {
        this.strategy = strategy;
        this.offloaded = offloaded;
        this.shouldOffload = shouldOffload;
    }

    @Override
    public StreamingHttpServiceFilter create(StreamingHttpService service) {
        StreamingHttpService offloadedService = StreamingHttpServiceToOffloadedStreamingHttpService.offloadService(
                strategy, null, shouldOffload, offloaded.create(service));
        return new StreamingHttpServiceFilter(offloadedService);
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        // We do our own offloading
        return HttpExecutionStrategies.offloadNone();
    }
}
