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

import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestCompletableTest {

    private final TestCompletableSubscriber subscriber1 = new TestCompletableSubscriber();
    private final TestCompletableSubscriber subscriber2 = new TestCompletableSubscriber();

    @Test
    void testNonResubscribeableCompletable() {
        TestCompletable source = new TestCompletable.Builder()
                .singleSubscriber()
                .build();

        source.subscribe(subscriber1);
        subscriber1.awaitSubscription();

        source.onComplete();
        subscriber1.awaitOnComplete();

        source.subscribe(subscriber2);

        Exception e = assertThrows(RuntimeException.class, () -> source.onComplete());
        assertEquals("Unexpected exception(s) encountered", e.getMessage());
        assertThat(e.getCause(), allOf(instanceOf(IllegalStateException.class),
                hasProperty("message", startsWith("Duplicate subscriber"))));
    }

    @Test
    void testSequentialSubscribeCompletable() {
        TestCompletable source = new TestCompletable.Builder()
                .build();

        source.subscribe(subscriber1);
        source.onComplete();
        subscriber1.awaitOnComplete();

        source.subscribe(subscriber2);
        assertThat(subscriber2.pollTerminal(10, MILLISECONDS), is(nullValue()));
        source.onComplete();
        subscriber2.awaitOnComplete();
    }

    @Test
    void testConcurrentSubscribeCompletable() {
        TestCompletable source = new TestCompletable.Builder()
                .concurrentSubscribers()
                .build();

        source.subscribe(subscriber1);

        source.subscribe(subscriber2);

        assertThat(subscriber1.pollTerminal(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber2.pollTerminal(10, MILLISECONDS), is(nullValue()));

        source.onComplete();
        subscriber1.awaitOnComplete();
        subscriber2.awaitOnComplete();
    }
}
