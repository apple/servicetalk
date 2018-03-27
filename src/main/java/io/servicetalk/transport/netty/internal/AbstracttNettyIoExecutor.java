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

import io.netty.channel.EventLoopGroup;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;

import java.util.concurrent.TimeUnit;

import static io.servicetalk.concurrent.internal.ExecutorUtil.executeOnService;
import static io.servicetalk.concurrent.internal.ExecutorUtil.scheduleForSubscriber;

abstract class AbstracttNettyIoExecutor<T extends EventLoopGroup> implements NettyIoExecutor {

    protected final T eventLoop;
    protected final boolean interruptOnCancel;

    AbstracttNettyIoExecutor(T eventLoop, boolean interruptOnCancel) {
        this.eventLoop = eventLoop;
        this.interruptOnCancel = interruptOnCancel;
    }

    @Override
    public Completable closeAsync(long quietPeriod, long timeout, TimeUnit unit) {
        return new NettyFutureCompletable(() -> eventLoop.shutdownGracefully(quietPeriod, timeout, unit));
    }

    @Override
    public final Completable onClose() {
        return new NettyFutureCompletable(eventLoop::terminationFuture);
    }

    @Override
    public final boolean isUnixDomainSocketSupported() {
        return NativeTransportUtil.isUnixDomainSocketSupported(eventLoop);
    }

    @Override
    public boolean isFileDescriptorSocketAddressSupported() {
        return NativeTransportUtil.isFileDescriptorSocketAddressSupported(eventLoop);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstracttNettyIoExecutor<?> that = (AbstracttNettyIoExecutor<?>) o;

        return interruptOnCancel == that.interruptOnCancel && eventLoop.equals(that.eventLoop);
    }

    @Override
    public int hashCode() {
        int result = eventLoop.hashCode();
        result = 31 * result + (interruptOnCancel ? 1 : 0);
        return result;
    }

    @Override
    public Cancellable executeOnEventloop(Runnable task) {
        return executeOnService(eventLoop, task, interruptOnCancel);
    }

    @Override
    public Completable scheduleOnEventloop(long duration, TimeUnit durationUnit) {
        return new Completable() {
            @Override
            protected void handleSubscribe(Subscriber subscriber) {
                scheduleForSubscriber(subscriber, eventLoop, interruptOnCancel, duration, durationUnit);
            }
        };
    }
}
