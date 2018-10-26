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
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static java.lang.Thread.NORM_PRIORITY;

/**
 * ServiceTalk's shared {@link ExecutionContext} with reasonable defaults for APIs when a user doesn't provide one.
 * <p>
 * A lazily initialized singleton {@link ExecutionContext}, the lifecycle of this instance shouldn't need to be managed
 * by the user. Don't attempt to close the executors.
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

    private static final class GlobalExecutionContextInitializer {

        static final ExecutionContext INSTANCE;

        static {
            final IoExecutor ioExecutor = createIoExecutor(new io.netty.util.concurrent.DefaultThreadFactory(
                    "servicetalk-global-io-executor", true, NORM_PRIORITY));
            final Executor executor = newCachedThreadExecutor(
                    new DefaultThreadFactory("servicetalk-global-executor", true, NORM_PRIORITY));
            INSTANCE = new DefaultExecutionContext(DEFAULT_ALLOCATOR, ioExecutor, executor);
            LOGGER.debug("Initialized GlobalExecutionContext");
        }

        private GlobalExecutionContextInitializer() {
            // No instances
        }
    }
}
