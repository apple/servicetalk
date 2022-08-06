/*
 * Copyright © 2019, 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.internal;

import java.util.concurrent.CancellationException;

/**
 * {@link CancellationException} that will not fill in the stacktrace but use a cheaper way of producing
 * limited stacktrace details for the user.
 */
public final class StacklessCancellationException extends CancellationException {
    private static final long serialVersionUID = 3373620908107542062L;

    private StacklessCancellationException(String message) {
        super(message);
    }

    // Override fillInStackTrace() so we not populate the backtrace via a native call and so leak the
    // Classloader.
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    /**
     * Creates a new {@link StacklessCancellationException} instance.
     *
     * @param message The description message.
     * @param clazz The class in which this {@link StacklessCancellationException} will be used.
     * @param method The method from which it will be thrown.
     * @return a new instance.
     */
    public static StacklessCancellationException newInstance(String message, Class<?> clazz, String method) {
        return ThrowableUtils.unknownStackTrace(new StacklessCancellationException(message), clazz, method);
    }
}
