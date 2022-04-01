/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.grpc.api.GrpcLifecycleObserver;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.netty.HttpLifecycleObserverServiceFilter;

/**
 * An HTTP service filter that tracks events during gRPC request/response lifecycle.
 * <p>
 * The recommended approach is to use {@link GrpcServerBuilder#lifecycleObserver(GrpcLifecycleObserver)} to configure
 * an observer that captures entire state of the request processing. In cases when an observer should be moved down in
 * a filter chain or applied conditionally, this filter can be used.
 * <p>
 * This filter is recommended to be appended as the first filter at the
 * {@link HttpServerBuilder#appendNonOffloadingServiceFilter(StreamingHttpServiceFilterFactory)}
 * or {@link  HttpServerBuilder#appendServiceFilter(StreamingHttpServiceFilterFactory)}
 * (which can be configured using {@link GrpcServerBuilder#initializeHttp(GrpcServerBuilder.HttpInitializer)})
 * to account for all work done by other filters. If it's preferred to get visibility to information populated by other
 * filters (like tracing keys), it can be appended after those filters.
 */
public final class GrpcLifecycleObserverServiceFilter extends HttpLifecycleObserverServiceFilter {

    /**
     * Create a new instance.
     *
     * @param observer The {@link GrpcLifecycleObserver observer} implementation that consumes gRPC lifecycle events.
     */
    public GrpcLifecycleObserverServiceFilter(final GrpcLifecycleObserver observer) {
        super(new GrpcToHttpLifecycleObserverBridge(observer));
    }
}
