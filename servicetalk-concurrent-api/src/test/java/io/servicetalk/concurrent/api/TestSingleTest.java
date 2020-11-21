/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

public class TestSingleTest {

    @Rule
    public final ExpectedException expected = ExpectedException.none();

    private final TestSingleSubscriber<String> subscriber1 = new TestSingleSubscriber<>();
    private final TestSingleSubscriber<String> subscriber2 = new TestSingleSubscriber<>();

    @Test
    public void testNonResubscribeableSingle() {
        TestSingle<String> source = new TestSingle.Builder<String>()
                .singleSubscriber()
                .build();

        source.subscribe(subscriber1);
        subscriber1.awaitSubscription();

        source.onSuccess("a");
        assertThat(subscriber1.awaitOnSuccess(), is("a"));

        source.subscribe(subscriber2);
        expected.expect(RuntimeException.class);
        expected.expectMessage("Unexpected exception(s) encountered");
        expected.expectCause(allOf(instanceOf(IllegalStateException.class), hasProperty("message",
                startsWith("Duplicate subscriber"))));
        source.onSuccess("b");
    }

    @Test
    public void testSequentialSubscribeSingle() {
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
    public void testConcurrentSubscribeSingle() {
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
