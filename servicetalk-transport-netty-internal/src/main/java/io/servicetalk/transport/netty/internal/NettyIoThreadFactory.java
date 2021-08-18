/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

/**
 * Default {@link io.servicetalk.transport.api.IoThreadFactory} to create IO {@link IoThread}s.
 */
public final class NettyIoThreadFactory extends io.servicetalk.transport.netty.internal.IoThreadFactory {

    /**
     * Create a new instance.
     * @param threadNamePrefix the name prefix used for the created {@link Thread}s.
     */
    public NettyIoThreadFactory(String threadNamePrefix) {
        this(threadNamePrefix, true);
    }

    /**
     * Create a new instance.
     * @param threadNamePrefix the name prefix used for the created {@link Thread}s.
     * @param daemon {@code true} if the created {@link Thread} should be a daemon thread.
     */
    @SuppressWarnings("PMD.AvoidThreadGroup")
    public NettyIoThreadFactory(String threadNamePrefix, boolean daemon) {
        super(threadNamePrefix, daemon);
    }
}
