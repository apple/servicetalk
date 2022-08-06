/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.internal.ThrowableUtils;

import java.nio.channels.ClosedChannelException;

/**
 * {@link ClosedChannelException} that will not fill in the stacktrace but use a cheaper way of producing
 * limited stacktrace details for the user.
 */
public final class StacklessClosedChannelException extends ClosedChannelException {
    private static final long serialVersionUID = -5021225720136487769L;

    private StacklessClosedChannelException() { }

    @Override
    public Throwable fillInStackTrace() {
        // Don't fill in the stacktrace to reduce performance overhead
        return this;
    }

    /**
     * Creates a new {@link StacklessClosedChannelException} instance.
     *
     * @param clazz The class in which this {@link StacklessClosedChannelException} will be used.
     * @param method The method from which it will be thrown.
     * @return a new instance.
     */
    public static StacklessClosedChannelException newInstance(Class<?> clazz, String method) {
        return ThrowableUtils.unknownStackTrace(new StacklessClosedChannelException(), clazz, method);
    }
}
