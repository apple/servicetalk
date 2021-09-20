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
package io.servicetalk.grpc.api;

import java.util.Collection;

/**
 * A <a href="https://www.grpc.io">gRPC</a> service that can generate {@link GrpcServiceFactory factory} instances bound
 * to itself.
 *
 * @param <F> Filter type
 * @param <S> Service Type
 * @param <FF> FilterFactory type
 */
public interface GrpcBindableService<F extends S, S extends GrpcService,
        FF extends GrpcServiceFilterFactory<F, S>> {

    /**
     * Return an appropriate service factory bound to this service.
     *
     * @return A service factory bound to this service with other appropriate configuration.
     */
    GrpcServiceFactory<F, S, FF> bindService();

    /**
     * Get a {@link Collection} of all {@link MethodDescriptor}s for this service.
     * @return a {@link Collection} of all {@link MethodDescriptor}s for this service.
     */
    Collection<MethodDescriptor<?, ?>> methodDescriptors();
}
