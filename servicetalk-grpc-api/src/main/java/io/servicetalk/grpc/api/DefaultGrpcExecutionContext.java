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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.transport.api.IoExecutor;

import static java.util.Objects.requireNonNull;

final class DefaultGrpcExecutionContext implements GrpcExecutionContext {
    private final HttpExecutionContext delegate;
    private final GrpcExecutionStrategy strategy;

    DefaultGrpcExecutionContext(HttpExecutionContext httpExecutionContext) {
        delegate = requireNonNull(httpExecutionContext);
        strategy = GrpcExecutionStrategy.from(httpExecutionContext.executionStrategy());
    }

    @Override
    public BufferAllocator bufferAllocator() {
        return delegate.bufferAllocator();
    }

    @Override
    public IoExecutor ioExecutor() {
        return delegate.ioExecutor();
    }

    @Override
    public Executor executor() {
        return delegate.executor();
    }

    @Override
    public GrpcExecutionStrategy executionStrategy() {
        return strategy;
    }
}
