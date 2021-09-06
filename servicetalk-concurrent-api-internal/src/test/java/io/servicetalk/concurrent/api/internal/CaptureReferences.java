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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Utilities for capturing references during test execution.
 */
public class CaptureReferences<E extends Enum<E>, R> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaptureReferences.class);

    protected final E[] values;
    protected final AtomicReferenceArray<R> references;
    protected final Supplier<R> capturer;

    /**
     * A capture slot will be created for each enum value.
     *
     * @param clazz The enum class for this capture
     * @param capturer Supplier which captures the desired reference.
     */
    protected CaptureReferences(Class<E> clazz, Supplier<R> capturer) {
        values = clazz.getEnumConstants();
        this.capturer = capturer;
        references = new AtomicReferenceArray<>(values.length);
    }

    @Override
    public String toString() {
        return Arrays.stream(values)
                .map(slot -> {
                    R reference = captured(slot);
                    return slot + "=" + (null == reference ? "null" : "\"" + reference + "\"");
                })
                .collect(Collectors.joining(", ", "[ ", " ]"));
    }

    /**
     * Capture the current reference for the specified slot and assert that there was no previous capture for this slot.
     *
     * @param slot the slot to capture
     * @throws AssertionError if the specified slot already contained a captured reference.
     */
    public void capture(final E slot) {
        int index = slot.ordinal();
        R current = capturer.get();
        assertThat("Reference captured was null", current, notNullValue());
        R was = references.getAndSet(index, current);
        assertThat("Reference already captured at " + slot, was, nullValue());
        LOGGER.trace("Capture {} at {}", current, slot);
    }

    /**
     * Returns the reference captured for a specific slot.
     *
     * @return The reference captured for the specified slot or {@code null} if no reference has been captured.
     */
    public @Nullable
    R captured(final E slot) {
        return references.get(slot.ordinal());
    }

    /**
     * Asserts that the reference captured in the specified slot matches the provided matcher.
     *
     * @param slot slot of interest
     * @param matcher the matcher which will be applied to the captured reference
     * @throws AssertionError if the captured value of the slot does not match
     */
    public void assertCaptured(E slot, Matcher<? super R> matcher) throws AssertionError {
        assertCaptured("Unexpected reference", slot, matcher);
    }

    /**
     * Asserts that the reference captured in the specified slot matches the provided matcher.
     *
     * @param reason message to include for failed matches
     * @param slot slot of interest
     * @param matcher the matcher which will be applied to the captured reference
     * @throws AssertionError if the captured value of the slot does not match
     */
    public void assertCaptured(String reason, E slot, Matcher<? super R> matcher) throws AssertionError {
        org.hamcrest.MatcherAssert.assertThat(reason + " : " + slot, captured(slot), matcher);
    }

    /**
     * Asserts that the reference captured in the specified slot matches the provided matcher.
     *
     * @param reason message to include for failed matches
     * @param stack the stack to include
     * @param slot slot of interest
     * @param matcher the matcher which will be applied to the captured reference
     * @throws AssertionError if the captured value of the slot does not match
     */
    public void assertCaptured(String reason, Throwable stack, E slot, Matcher<? super R> matcher)
            throws AssertionError {
        try {
            org.hamcrest.MatcherAssert.assertThat(reason + " : " + slot, captured(slot), matcher);
        } catch (AssertionError assertionError) {
            assertionError.initCause(stack);
            throw assertionError;
        }
    }
}
