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

import org.hamcrest.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;

import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

/**
 * Utilities for capturing threads during test execution.
 */
public abstract class CaptureThreads<E extends Enum<E>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaptureThreads.class);

    protected final E[] values;
    protected final AtomicReferenceArray<Thread> threads;

    /**
     * A capture slot will be created for each enum value.
     *
     * @param clazz The enum class for this capture
     */
    protected CaptureThreads(Class<E> clazz) {
        values = clazz.getEnumConstants();
        threads = new AtomicReferenceArray<>(values.length);
    }

    @Override
    public String toString() {
        return Arrays.stream(values)
                .map(slot -> {
                    Thread thread = captured(slot);
                    return slot + "=" + (null == thread ? "null" : "\"" + thread.getName() + "\"");
                })
                .collect(Collectors.joining(", ", "[ ", " ]"));
    }

    /**
     * Capture the current thread for the specified slot and assert that there was no previous capture of the same slot.
     *
     * @param slot the slot to capture
     * @throws AssertionError if the specified slot already contained a captured thread.
     */
    public void capture(final E slot) {
        int index = slot.ordinal();
        Thread current = currentThread();
        Thread was = threads.getAndSet(index, current);
        assertThat("Thread already captured at " + slot, was, nullValue());
        LOGGER.trace("Capture {} at {}", current, slot);
    }

    /**
     * Returns the thread captured for a specific slot.
     *
     * @return The thread captured for the specified slot or {@code null} if no thread has been captured.
     */
    public @Nullable Thread captured(final E slot) {
        return threads.get(slot.ordinal());
    }

    /**
     * Asserts that the thread captured in the specified slot matches the provided matcher.
     *
     * @param slot slot of interest
     * @param matcher the matcher which will be applied to the captured thread
     * @throws AssertionError if the captured value of the slot does not match
     */
    public void assertCaptured(E slot, Matcher<Thread> matcher) throws AssertionError {
        assertCaptured("Unexpected thread", slot, matcher);
    }

    /**
     * Asserts that the thread captured in the specified slot matches the provided matcher.
     *
     * @param reason message to include for failed matches
     * @param slot slot of interest
     * @param matcher the matcher which will be applied to the captured thread
     * @throws AssertionError if the captured value of the slot does not match
     */
    public void assertCaptured(String reason, E slot, Matcher<Thread> matcher) throws AssertionError {
        org.hamcrest.MatcherAssert.assertThat(reason + " : " + this + " : " + slot, captured(slot), matcher);
    }

    /**
     * Perform validation of the captured threads.
     *
     * @throws AssertionError if the captured threads do not match expected state.
     */
    public abstract void assertCaptured() throws AssertionError;

    /**
     * Returns all of the captured thread slots as an array.
     *
     * @return an array of the captured threads.
     */
    public Thread[] asArray() {
        return IntStream.range(0, threads.length())
                .mapToObj(threads::get)
                .toArray(Thread[]::new);
    }
}
