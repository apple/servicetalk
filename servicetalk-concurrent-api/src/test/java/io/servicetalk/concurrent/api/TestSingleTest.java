/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestSingleTest {

    private final TestSingleSubscriber<String> subscriber1 = new TestSingleSubscriber<>();
    private final TestSingleSubscriber<String> subscriber2 = new TestSingleSubscriber<>();

    @Test
    void testNonResubscribeableSingle() {
        TestSingle<String> source = new TestSingle.Builder<String>()
                .singleSubscriber()
                .build();

        source.subscribe(subscriber1);
        subscriber1.awaitSubscription();

        source.onSuccess("a");
        assertThat(subscriber1.awaitOnSuccess(), is("a"));

        source.subscribe(subscriber2);

        AssertionError e = assertThrows(AssertionError.class, () -> source.onSuccess("b"));
        assertEquals("Unexpected exception(s) encountered", e.getMessage());
        assertThat(e.getCause(), allOf(instanceOf(IllegalStateException.class),
                                       hasProperty("message",
                                                   startsWith("Duplicate subscriber"))));
    }

    @Test
    void testSequentialSubscribeSingle() {
        TestSingle<String> source = new TestSingle.Builder<String>()
                .build();

        source.subscribe(subscriber1);
        source.onSuccess("a");
        assertThat(subscriber1.awaitOnSuccess(), is("a"));

        source.subscribe(subscriber2);
        source.onSuccess("b");
        assertThat(subscriber2.awaitOnSuccess(), is("b"));
    }

    @Test
    void testConcurrentSubscribeSingle() {
        TestSingle<String> source = new TestSingle.Builder<String>()
                .concurrentSubscribers()
                .build();

        source.subscribe(subscriber1);

        source.subscribe(subscriber2);

        source.onSuccess("a");
        assertThat(subscriber1.awaitOnSuccess(), is("a"));
        assertThat(subscriber2.awaitOnSuccess(), is("a"));
    }
}
