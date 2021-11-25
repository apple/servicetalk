/*
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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

abstract class AbstractNettyIoExecutor<T extends EventLoopGroup> implements NettyIoExecutor, Executor {

    protected final boolean isIoThreadSupported;
    protected final T eventLoop;
    protected final boolean interruptOnCancel;

    AbstractNettyIoExecutor(T eventLoop, boolean interruptOnCancel) {
        this(eventLoop, interruptOnCancel, false);
    }

    AbstractNettyIoExecutor(T eventLoop, boolean interruptOnCancel, boolean isIoThreadSupported) {
        this.eventLoop = eventLoop;
        this.interruptOnCancel = interruptOnCancel;
        this.isIoThreadSupported = isIoThreadSupported;
    }

    @Override
    public Completable closeAsync() {
        return new NettyFutureCompletable(() -> eventLoop.shutdownGracefully(0, 0, NANOSECONDS));
    }

    @Override
    public Completable closeAsyncGracefully() {
        return new NettyFutureCompletable(eventLoop::shutdownGracefully);
    }

    @Override
    public final Completable onClose() {
        return new NettyFutureCompletable(eventLoop::terminationFuture);
    }

    @Override
    public final boolean isUnixDomainSocketSupported() {
        return NativeTransportUtils.isUnixDomainSocketSupported(eventLoop);
    }

    @Override
    public boolean isFileDescriptorSocketAddressSupported() {
        return NativeTransportUtils.isFileDescriptorSocketAddressSupported(eventLoop);
    }

    @Override
    public final boolean isIoThreadSupported() {
        return isIoThreadSupported;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractNettyIoExecutor<?> that = (AbstractNettyIoExecutor<?>) o;

        return interruptOnCancel == that.interruptOnCancel && eventLoop.equals(that.eventLoop);
    }

    @Override
    public int hashCode() {
        int result = eventLoop.hashCode();
        result = 31 * result + (interruptOnCancel ? 1 : 0);
        return result;
    }

    @Override
    public Executor asExecutor() {
        return this;
    }

    @Override
    public Cancellable execute(final Runnable task) throws RejectedExecutionException {
        Future<?> future = eventLoop.submit(task);
        return () -> future.cancel(interruptOnCancel);
    }

    @Override
    public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit)
            throws RejectedExecutionException {
        ScheduledFuture<?> future = eventLoop.schedule(task, delay, unit);
        return () -> future.cancel(interruptOnCancel);
    }

    @Override
    public long currentTime() {
        return System.nanoTime();
    }

    @Override
    public TimeUnit currentTimeUnits() {
        return NANOSECONDS;
    }
}
