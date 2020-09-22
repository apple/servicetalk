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

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;

import java.util.Set;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.grpc.api.GrpcMessageEncodings.none;
import static java.util.Collections.singleton;

/**
 * A <a href="https://www.grpc.io">gRPC</a> service.
 */
public interface GrpcService extends AsyncCloseable {

    /**
     * The set of {@link GrpcMessageEncoding} encodings supported for this
     * <a href="https://www.grpc.io">gRPC</a> service.
     *
     * @return The set of {@link GrpcMessageEncoding} encodings supported for this
     * <a href="https://www.grpc.io">gRPC</a> service.
     */
    default Set<GrpcMessageEncoding> supportedEncodings() {
        return singleton(none());
    }

    @Override
    default Completable closeAsync() {
        return completed();
    }
}
