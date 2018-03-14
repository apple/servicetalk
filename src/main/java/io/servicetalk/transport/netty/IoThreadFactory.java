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

import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.ThreadFactory;

/**
 * Default {@link ThreadFactory} to create IO {@link Thread}s.
 */
public final class IoThreadFactory implements ThreadFactory {

    // Just delegate to the Netty ThreadFactory and not extend it so we do not leak netty types in our public api.
    private final ThreadFactory factory;

    /**
     * Create a new instance.
     * @param threadNamePrefix the name prefix used for the created {@link Thread}s.
     */
    public IoThreadFactory(String threadNamePrefix) {
        factory = new DefaultThreadFactory(threadNamePrefix, false, Thread.NORM_PRIORITY);
    }

    /**
     * Create a new instance.
     * @param threadNamePrefix the name prefix used for the created {@link Thread}s.
     * @param daemon {@code true} if the created {@link Thread} should be a daemon thread.
     */
    public IoThreadFactory(String threadNamePrefix, boolean daemon) {
        factory = new DefaultThreadFactory(threadNamePrefix, daemon, Thread.NORM_PRIORITY);
    }

    @Override
    public Thread newThread(Runnable r) {
        return factory.newThread(r);
    }
}
