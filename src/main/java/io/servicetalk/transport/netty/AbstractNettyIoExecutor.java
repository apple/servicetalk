/**
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.netty;

import io.netty.util.concurrent.EventExecutor;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.context.AsyncContext;
import io.servicetalk.transport.api.IoExecutor;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

abstract class AbstractNettyIoExecutor<T extends EventExecutor> implements IoExecutor {
    private static final Runnable NOOP = () -> { };

    final T executor;

    /**
     * Create a new instance.
     * @param executor the {@link EventExecutor} to delegate to.
     */
    AbstractNettyIoExecutor(T executor) {
        this.executor = Objects.requireNonNull(executor);
    }

    @Override
    public final boolean inIoThread() {
        return executor.inEventLoop();
    }

    @Override
    public final IoExecutor next() {
        return this;
    }

    @Override
    public Completable closeAsync(long quietPeriod, long timeout, TimeUnit unit) {
        return new NettyFutureCompletable(() -> executor.shutdownGracefully(quietPeriod, timeout, unit));
    }

    @Override
    public final Completable onClose() {
        return new NettyFutureCompletable(executor::terminationFuture);
    }

    // Request Context Preservation
    // We should preserve request context even if the Immediate Executor is used, because the Immediate Executor may
    // prevent Stack Overflow and execute jobs later in order. To prevent different behavior from the perspective of
    // request context we should always preserve the request context.
    @Override
    public final void execute(Runnable command) {
        executor.execute(AsyncContext.wrap(command));
    }
    // Request Context Preservation

    @Override
    public Completable immediate() {
        return new NettyFutureCompletable(() -> executor.submit(NOOP));
    }

    @Override
    public Completable timer(long time, TimeUnit unit) {
        return new NettyFutureCompletable(() -> executor.schedule(NOOP, time, unit));
    }

    @Override
    public final boolean isUnixDomainSocketSupported() {
        return NativeTransportUtil.isUnixDomainSocketSupported(executor.parent());
    }

    @Override
    public boolean isFileDescriptorSocketAddressSupported() {
        return NativeTransportUtil.isFileDescriptorSocketAddressSupported(executor.parent());
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof NettyIoExecutorGroup) {
            return executor.equals(((NettyIoExecutorGroup) o).group);
        }
        if (o instanceof AbstractNettyIoExecutor) {
            return executor.equals(((AbstractNettyIoExecutor) o).executor);
        }
        return executor.equals(o);
    }

    @Override
    public final int hashCode() {
        return executor.hashCode();
    }
}
