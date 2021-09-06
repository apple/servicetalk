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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.IoThreadFactory;

final class OffloadFromIOExecutionStrategy implements ExecutionStrategy {

    static final ExecutionStrategy OFFLOAD_FROM_IO_STRATEGY = new OffloadFromIOExecutionStrategy();

    private OffloadFromIOExecutionStrategy() {
        // Singleton
    }

    @Override
    public <T> Single<T> offloadSend(final Executor executor, final Single<T> original) {
        return original.subscribeOn(executor, IoThreadFactory.IoThread::currentThreadIsIoThread);
    }

    @Override
    public <T> Single<T> offloadReceive(final Executor executor, final Single<T> original) {
        return original.publishOn(executor, IoThreadFactory.IoThread::currentThreadIsIoThread);
    }

    @Override
    public <T> Publisher<T> offloadSend(final Executor executor, final Publisher<T> original) {
        return original.subscribeOn(executor, IoThreadFactory.IoThread::currentThreadIsIoThread);
    }

    @Override
    public <T> Publisher<T> offloadReceive(final Executor executor, final Publisher<T> original) {
        return original.publishOn(executor, IoThreadFactory.IoThread::currentThreadIsIoThread);
    }
}
