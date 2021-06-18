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
package io.servicetalk.concurrent.api.internal;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Utilities for capturing threads during test execution.
 */
public class CaptureThreads {

    protected final AtomicReferenceArray<Thread> threads;

    public CaptureThreads(int size) {
        threads = new AtomicReferenceArray<>(size);
    }

    public void capture(final int index) {
        Thread was = threads.getAndSet(index, currentThread());
        assertThat("Thread already recorded at index: " + index, was, nullValue());
    }

    public Thread[] verify() {
        Thread[] asArray = asArray();
        for (int i = 0; i < asArray.length; i++) {
            assertThat("No captured thread at index: " + i, asArray[i], notNullValue());
        }

        return asArray;
    }

    public Thread[] asArray() {
        return IntStream.range(0, threads.length())
                .mapToObj(threads::get)
                .toArray(Thread[]::new);
    }

    @Override
    public String toString() {
        return IntStream.range(0, threads.length())
                .mapToObj(threads::get)
                .map(thread -> null == thread ? "null" : thread.getName())
                .collect(Collectors.joining(", ", "[ ", " ]"));
    }
}
