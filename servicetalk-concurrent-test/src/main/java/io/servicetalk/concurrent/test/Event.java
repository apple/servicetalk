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
package io.servicetalk.concurrent.test;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static java.util.Arrays.copyOfRange;

abstract class Event<T> {
    private final StackTraceElement[] originalStackTrace;

    Event() {
        originalStackTrace = Thread.currentThread().getStackTrace();
    }

    abstract void verify(T subscriber) throws Exception;

    abstract void verify(T subscriber, long timeout, TimeUnit unit) throws Exception;

    final AssertionError newException(String message, Throwable cause, String stepClassName) {
        return StepAssertionError.newInstance(message, cause, stepClassName, originalStackTrace);
    }

    static <T> boolean notEqualsOnNext(@Nullable final T expected, @Nullable final T actual) {
        return (expected == null || !expected.equals(actual)) && (expected != null || actual != null);
    }

    static void verifyNoTerminal(@Nullable Supplier<Throwable> onTerminalSupplier) {
        if (onTerminalSupplier != null) {
            Throwable cause = onTerminalSupplier.get();
            if (cause == null) {
                throw new IllegalStateException("expected no signals, actual onComplete");
            }
            throw new IllegalStateException("expected no signals, actual onError", cause);
        }
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        @Nullable
        T get() throws Exception;
    }

    private static final class StepAssertionError extends AssertionError {
        private StepAssertionError(String message, Throwable cause) {
            super(message, cause);
        }

        private static StepAssertionError newInstance(String message, Throwable cause, String stepClassName,
                                                      StackTraceElement[] originalStackTrace) {
            StepAssertionError e = new StepAssertionError(message, cause);
            e.setStackTrace(filterStackTrace(stepClassName, originalStackTrace));
            return e;
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }

        private static StackTraceElement[] filterStackTrace(String stepClassName, StackTraceElement[] elements) {
            assert elements.length > 1 && elements[1].getClassName().startsWith(Event.class.getName());

            // 4th element is typically where we should start, but just go through every element for simplicity.
            for (int i = 2; i < elements.length; ++i) {
                StackTraceElement element = elements[i];
                if (!element.getClassName().startsWith(stepClassName)) {
                    return copyOfRange(elements, i, elements.length);
                }
            }

            return elements;
        }

        @Override
        public String toString() {
            String s = StepAssertionError.class.getSimpleName();
            String message = getLocalizedMessage();
            return (message != null) ? (s + ": " + message) : s;
        }
    }
}
