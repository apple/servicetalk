/*
 * Copyright © 2019-2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.dns.discovery.netty;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import io.netty.channel.EventLoopGroup;
import io.netty.util.internal.PlatformDependent;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.util.Arrays.setAll;

final class DnsTestUtils {

    private static final int[] NUMBERS = new int[254];
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private static final String[] IPV6_ADDRESSES = {
            "0:0:0:0:0:0:1:1",
            "0:0:0:0:0:1:1:1",
            "0:0:0:0:1:1:1:1",
            "0:0:0:1:1:1:1:1",
            "0:0:1:1:1:1:1:1",
            "0:1:1:1:1:1:1:1",
            "1:1:1:1:1:1:1:1",
    };

    private static final AtomicInteger ipv6Idx = new AtomicInteger();

    static {
        setAll(NUMBERS, i -> i + 1);
    }

    private DnsTestUtils() {
        // No instances
    }

    static String nextIp() {
        return ipPart() + "." + ipPart() + '.' + ipPart() + '.' + ipPart();
    }

    static String nextIp6() {
        return IPV6_ADDRESSES[ipv6Idx.getAndIncrement() % IPV6_ADDRESSES.length];
    }

    private static int index(int arrayLength) {
        return PlatformDependent.threadLocalRandom().nextInt(arrayLength);
    }

    private static int ipPart() {
        return NUMBERS[index(NUMBERS.length)];
    }

    static final class NettyIoExecutorWithTestTimer implements EventLoopAwareNettyIoExecutor {

        private final EventLoopAwareNettyIoExecutor delegate;
        private final TestExecutor timer;

        NettyIoExecutorWithTestTimer(EventLoopAwareNettyIoExecutor delegate, TestExecutor timer) {
            // We need to take a single EventLoopIoExecutor to make sure that isCurrentThreadEventLoop() works.
            this.delegate = delegate.next();
            this.timer = timer;
        }

        @Override
        public Completable closeAsync() {
            return delegate.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return delegate.closeAsyncGracefully();
        }

        @Override
        public Completable onClose() {
            return delegate.onClose();
        }

        @Override
        public Completable onClosing() {
            return delegate.onClosing();
        }

        @Override
        public boolean isUnixDomainSocketSupported() {
            return delegate.isUnixDomainSocketSupported();
        }

        @Override
        public boolean isFileDescriptorSocketAddressSupported() {
            return delegate.isFileDescriptorSocketAddressSupported();
        }

        @Override
        public boolean isIoThreadSupported() {
            return delegate.isIoThreadSupported();
        }

        @Override
        public boolean isCurrentThreadEventLoop() {
            return delegate.isCurrentThreadEventLoop();
        }

        @Override
        public EventLoopGroup eventLoopGroup() {
            return delegate.eventLoopGroup();
        }

        @Override
        public EventLoopAwareNettyIoExecutor next() {
            return this;
        }

        @Override
        public Cancellable execute(final Runnable task) throws RejectedExecutionException {
            return delegate.execute(task);
        }

        @Override
        public Completable submit(final Runnable runnable) {
            return delegate.submit(runnable);
        }

        @Override
        public Completable submitRunnable(final Supplier<Runnable> runnableSupplier) {
            return delegate.submitRunnable(runnableSupplier);
        }

        @Override
        public <T> Single<T> submit(final Callable<? extends T> callable) {
            return delegate.submit(callable);
        }

        @Override
        public <T> Single<T> submitCallable(final Supplier<? extends Callable<? extends T>> callableSupplier) {
            return delegate.submitCallable(callableSupplier);
        }

        @Override
        public Executor asExecutor() {
            return this;
        }

        @Override
        public long currentTime(final TimeUnit unit) {
            return timer.currentTime(unit);
        }

        @Override
        public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit)
                throws RejectedExecutionException {
            // Original IoExecutor schedules and executes tasks on EventLoop. If we move scheduling to TestExecutor,
            // we need to offload the task back to EventLoop thread.
            return timer.schedule(() -> delegate.execute(task), delay, unit);
        }

        @Override
        public Cancellable schedule(final Runnable task, final Duration delay) throws RejectedExecutionException {
            // Original IoExecutor schedules and executes tasks on EventLoop. If we move scheduling to TestExecutor,
            // we need to offload the task back to EventLoop thread.
            return timer.schedule(() -> delegate.execute(task), delay);
        }

        @Override
        public Completable timer(final long delay, final TimeUnit unit) {
            return timer.timer(delay, unit).publishOn(delegate);
        }

        @Override
        public Completable timer(final Duration delay) {
            return timer.timer(delay).publishOn(delegate);
        }
    }
}
