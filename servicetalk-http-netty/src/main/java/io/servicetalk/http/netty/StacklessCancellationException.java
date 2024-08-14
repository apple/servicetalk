/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.internal.ThrowableUtils;

import java.util.concurrent.CancellationException;

final class StacklessCancellationException extends CancellationException {
    private static final long serialVersionUID = 5434529104526587317L;

    private StacklessCancellationException(String message) {
        super(message);
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    static StacklessCancellationException newInstance(String message, Class<?> clazz, String method) {
        return ThrowableUtils.unknownStackTrace(new StacklessCancellationException(message), clazz, method);
    }
}
