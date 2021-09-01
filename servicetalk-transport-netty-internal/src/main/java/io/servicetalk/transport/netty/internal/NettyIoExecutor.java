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
import io.servicetalk.transport.api.IoExecutor;

/**
 * {@link IoExecutor} for Netty.
 * <p><strong>Caution</strong></p>
 * Implementations of this interface assumes that they would not be used to run blocking code.
 * If this assumption is violated, it will impact eventloop responsiveness and hence should be avoided.
 */
public interface NettyIoExecutor extends IoExecutor {
    /**
     * Get an {@link Executor} which will use an {@link IoExecutor} thread for execution.
     * <p><strong>Caution</strong></p>
     * Implementation of this method assumes there would be no blocking code inside the submitted {@link Runnable}s.
     * If this assumption is violated, it will impact EventLoop responsiveness and hence should be avoided.
     * @return an {@link Executor} which will use an {@link IoExecutor} thread for execution.
     */
    Executor asExecutor();
}
