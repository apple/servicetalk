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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.internal.DefaultThreadFactory;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.Await.await;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static java.lang.Runtime.getRuntime;
import static java.lang.Thread.NORM_PRIORITY;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * ServiceTalk's shared {@link ExecutionContext} with reasonable defaults for APIs when a user doesn't provide one.
 * <p>
 * A lazily initialized singleton {@link ExecutionContext}, the lifecycle of this instance is tied to the JVM and
 * shouldn't need to be managed by the user. Don't attempt to close the executors unless you have disabled the {@code
 * ShutdownHook} via {@link GlobalExecutionContext#disableShutdownHook()}.
 */
public final class GlobalExecutionContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExecutionContext.class);

    private GlobalExecutionContext() {
        // No instances
    }

    /**
     * Get the {@link GlobalExecutionContext}.
     *
     * @return the singleton instance
     */
    public static ExecutionContext globalExecutionContext() {
        return GlobalExecutionContextInitializer.INSTANCE;
    }

    /**
     * Skips closing the {@link GlobalExecutionContext}'s executors using a JVM ShutdownHook.
     * <p>
     * If a close from a {@code ShutdownHook} is not satisfactory use this method to disable it. The executors use
     * daemon threads, so closing is not essential but a good practice.
     */
    public static void disableShutdownHook() {
        if (getRuntime().removeShutdownHook(GlobalExecutionContextInitializer.SHUTDOWN_THREAD)) {
            LOGGER.warn("Disabled GlobalExecutionContext ShutdownHook per user request");
        }
    }

    private static final class GlobalExecutionContextInitializer {

        private static final ExecutionContext INSTANCE;
        private static final Thread SHUTDOWN_THREAD;

        static {
            GlobalExecutionContextInitializer ec = new GlobalExecutionContextInitializer();
            LOGGER.debug("Initialized GlobalExecutionContext");
            final Thread t = new Thread(() -> {
                try {
                    // 10 seconds was chosen as a reasonable default given most common process managers send SIGKILL
                    // after a specified timeout, with these default values:
                    // kubernetes 30s, mesos 5s, docker 10s, systemd 90s, supervisord 10s
                    await(ec.ioExecutor.closeAsyncGracefully()
                            .mergeDelayError(ec.executor.closeAsyncGracefully()), 10, SECONDS);
                } catch (Exception e) {
                    LOGGER.error("Failed to close the executor(s)", e);
                }
            }, "servicetalk-global-execution-context-shutdown");
            getRuntime().addShutdownHook(t);
            SHUTDOWN_THREAD = t;
            INSTANCE = new DefaultExecutionContext(DEFAULT_ALLOCATOR, ec.ioExecutor, ec.executor);
        }

        private final IoExecutor ioExecutor = createIoExecutor(0, new io.netty.util.concurrent.DefaultThreadFactory(
                "servicetalk-global-io-executor", true, NORM_PRIORITY));

        private final Executor executor = newCachedThreadExecutor(
                new DefaultThreadFactory("servicetalk-global-executor", true, NORM_PRIORITY));

        private GlobalExecutionContextInitializer() {
            // No instances
        }
    }
}
