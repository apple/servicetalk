/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.api;

import io.servicetalk.concurrent.api.AsyncContextMapHolder;
import io.servicetalk.transport.api.IoThreadFactory.IoThread;

import java.util.concurrent.ThreadFactory;

/**
 * Thread factory for use with {@link IoExecutor}.
 *
 * @param <T> Type of threads created
 */
@FunctionalInterface
public interface IoThreadFactory<T extends Thread & IoThread> extends ThreadFactory {

    /**
     * Marker interface for IO Threads. All threads created by a {@link IoThreadFactory} are expected to implement this
     * interface.
     */
    // FIXME: 0.42 - replace AsyncContextMapHolder with ContextMapHolder
    interface IoThread extends AsyncContextMapHolder {
        /**
         * Returns {@code true} if the current thread is an {@link IoThread} otherwise {code false}.
         *
         * @return {@code true} if the current thread is an {@link IoThread} otherwise {code false}.
         */
        static boolean currentThreadIsIoThread() {
            return isIoThread(Thread.currentThread());
        }

        /**
         * Returns {@code true} if the specified thread is an {@link IoThread} otherwise {code false}.
         *
         * @return {@code true} if the specified thread is an {@link IoThread} otherwise {code false}.
         */
        static boolean isIoThread(Thread thread) {
            return thread instanceof IoThread;
        }
    }

    @Override
    T newThread(Runnable r);
}
