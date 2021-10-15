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
}
