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
package io.servicetalk.grpc.api;

/**
 * A factory to create <a href="https://www.grpc.io">gRPC</a> client filters.
 *
 * @param <Filter> Type for client filter
 * @param <FilterableClient> Type of filterable client.
 * @deprecated gRPC Client Filters will be removed in future release of ServiceTalk. We encourage the use of
 * {@link io.servicetalk.http.api.StreamingHttpClientFilterFactory} and if the access to the decoded payload is
 * necessary, then performing that logic can be done in the particular {@link GrpcClient client implementation}.
 * Please use {@link io.servicetalk.http.api.SingleAddressHttpClientBuilder#appendClientFilter(
 * io.servicetalk.http.api.StreamingHttpClientFilterFactory)} upon the {@code builder} obtained using
 * {@link io.servicetalk.grpc.api.GrpcClientBuilder#initializeHttp(GrpcClientBuilder.HttpInitializer)}
 * if HTTP filters are acceptable in your use case.
 */
@Deprecated
public interface GrpcClientFilterFactory<Filter extends FilterableClient,
        FilterableClient extends FilterableGrpcClient> {

    /**
     * Create a {@link Filter} using the provided {@link Filter}.
     *
     * @param filterableClient {@link Filter} to filter
     * @return {@link Filter} using the provided {@link Filter}.
     */
    Filter create(FilterableClient filterableClient);
}
