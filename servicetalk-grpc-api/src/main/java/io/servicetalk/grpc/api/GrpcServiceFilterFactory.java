/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import static java.util.Objects.requireNonNull;

/**
 * A factory to create <a href="https://www.grpc.io">gRPC</a> service filters.
 *
 * @param <Filter> Type for service filter
 * @param <Service> Type for service
 * @deprecated gRPC Service Filters will be removed in future release of ServiceTalk. We encourage the use of
 * {@link StreamingHttpServiceFilterFactory} and if the access to the decoded payload is necessary, then performing
 * that logic can be done in the particular {@link GrpcService service implementation}.
 * Please use {@link io.servicetalk.http.api.HttpServerBuilder#appendServiceFilter(StreamingHttpServiceFilterFactory)}
 * upon the {@code builder} obtained using {@link GrpcServerBuilder#initializeHttp(GrpcServerBuilder.HttpInitializer)}
 * if HTTP filters are acceptable in your use case.
 */
@FunctionalInterface
@Deprecated
public interface GrpcServiceFilterFactory<Filter extends Service, Service> {

    /**
     * Create a {@link Filter} using the provided {@link Service}.
     *
     * @param service {@link Service} to filter.
     * @return {@link Filter} using the provided {@link Service}.
     */
    Filter create(Service service);

    /**
     * Returns a composed factory that first applies the {@code before} factory to its input, and then applies
     * this factory to the result.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; service
     * </pre>
     *
     * @deprecated Use {@link GrpcServiceFactory#appendServiceFilter(GrpcServiceFilterFactory)}
     * @param before the factory to apply before this factory is applied.
     * @return a composed factory that first applies the {@code before} factory and then applies this factory.
     */
    @Deprecated
    default GrpcServiceFilterFactory<Filter, Service> append(
            GrpcServiceFilterFactory<Filter, Service> before) {
        requireNonNull(before);
        return service -> create(before.create(service));
    }
}
