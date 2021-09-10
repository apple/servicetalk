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
package io.servicetalk.grpc.netty;

import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcLifecycleObserver;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.netty.HttpLifecycleObserverRequesterFilter;

/**
 * An HTTP requester filter that tracks events during gRPC request/response lifecycle.
 * <p>
 * This filter is recommended to be appended as the first filter at the
 * {@link GrpcClientBuilder#appendHttpClientFilter(StreamingHttpClientFilterFactory) client builder} to account
 * for all work done by other filters.
 */
public final class GrpcLifecycleObserverRequesterFilter extends HttpLifecycleObserverRequesterFilter {

    /**
     * Create a new instance.
     *
     * @param observer The {@link GrpcLifecycleObserver observer} implementation that consumes gRPC lifecycle events.
     */
    public GrpcLifecycleObserverRequesterFilter(final GrpcLifecycleObserver observer) {
        super(new GrpcToHttpLifecycleObserverBridge(observer));
    }
}
