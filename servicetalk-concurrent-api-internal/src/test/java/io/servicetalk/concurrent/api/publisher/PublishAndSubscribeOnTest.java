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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.internal.OffloaderAwareExecutor;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReferenceArray;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.SignalOffloaders.defaultOffloaderFactory;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

public class PublishAndSubscribeOnTest extends AbstractPublishAndSubscribeOnTest {

    @Rule
    public final ExecutorRule<Executor> executorRule = ExecutorRule.withExecutor(() ->
            new OffloaderAwareExecutor(newCachedThreadExecutor(), defaultOffloaderFactory()));

    @Test
    public void testPublishOnNoOverride() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads =
                setupAndSubscribe(Publisher::publishOn, (io.servicetalk.concurrent.Executor) executorRule.executor());

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads.get(ORIGINAL_SUBSCRIBER_THREAD),
                sameThreadFactory(capturedThreads.get(ORIGINAL_SUBSCRIPTION_THREAD)));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads.get(OFFLOADED_SUBSCRIBER_THREAD),
                not(sameThreadFactory(capturedThreads.get(OFFLOADED_SUBSCRIPTION_THREAD))));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads.get(ORIGINAL_SUBSCRIBER_THREAD),
                not(sameThreadFactory(capturedThreads.get(OFFLOADED_SUBSCRIBER_THREAD))));
    }

    @Test
    public void testPublishOnOverride() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads =
                setupAndSubscribe(Publisher::publishOnOverride, executorRule.executor());

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads.get(ORIGINAL_SUBSCRIBER_THREAD),
                not(sameThreadFactory(capturedThreads.get(ORIGINAL_SUBSCRIPTION_THREAD))));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads.get(OFFLOADED_SUBSCRIBER_THREAD),
                not(sameThreadFactory(capturedThreads.get(OFFLOADED_SUBSCRIPTION_THREAD))));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads.get(ORIGINAL_SUBSCRIBER_THREAD),
                sameThreadFactory(capturedThreads.get(OFFLOADED_SUBSCRIBER_THREAD)));
    }

    @Test
    public void testSubscribeOnNoOverride() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads =
                setupAndSubscribe(Publisher::subscribeOn, (io.servicetalk.concurrent.Executor) executorRule.executor());

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads.get(ORIGINAL_SUBSCRIBER_THREAD),
                sameThreadFactory(capturedThreads.get(ORIGINAL_SUBSCRIPTION_THREAD)));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads.get(OFFLOADED_SUBSCRIBER_THREAD),
                not(sameThreadFactory(capturedThreads.get(OFFLOADED_SUBSCRIPTION_THREAD))));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads.get(ORIGINAL_SUBSCRIPTION_THREAD),
                not(sameThreadFactory(capturedThreads.get(OFFLOADED_SUBSCRIPTION_THREAD))));
    }

    @Test
    public void testSubscribeOnOverride() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads =
                setupAndSubscribe(Publisher::subscribeOnOverride, executorRule.executor());

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads.get(ORIGINAL_SUBSCRIBER_THREAD),
                not(sameThreadFactory(capturedThreads.get(ORIGINAL_SUBSCRIPTION_THREAD))));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads.get(OFFLOADED_SUBSCRIBER_THREAD),
                not(sameThreadFactory(capturedThreads.get(OFFLOADED_SUBSCRIPTION_THREAD))));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads.get(ORIGINAL_SUBSCRIPTION_THREAD),
                sameThreadFactory(capturedThreads.get(OFFLOADED_SUBSCRIPTION_THREAD)));
    }

    @Test
    public void testNoOverride() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads =
                setupAndSubscribe(Publisher::publishAndSubscribeOn, executorRule.executor());

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads.get(ORIGINAL_SUBSCRIBER_THREAD),
                sameThreadFactory(capturedThreads.get(ORIGINAL_SUBSCRIPTION_THREAD)));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads.get(OFFLOADED_SUBSCRIBER_THREAD),
                sameThreadFactory(capturedThreads.get(OFFLOADED_SUBSCRIPTION_THREAD)));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads.get(ORIGINAL_SUBSCRIPTION_THREAD),
                not(sameThreadFactory(capturedThreads.get(OFFLOADED_SUBSCRIPTION_THREAD))));
    }

    @Test
    public void testOverride() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads =
                setupAndSubscribe(Publisher::publishAndSubscribeOnOverride, executorRule.executor());

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads.get(ORIGINAL_SUBSCRIBER_THREAD),
                sameThreadFactory(capturedThreads.get(ORIGINAL_SUBSCRIPTION_THREAD)));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads.get(OFFLOADED_SUBSCRIBER_THREAD),
                sameThreadFactory(capturedThreads.get(OFFLOADED_SUBSCRIPTION_THREAD)));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads.get(ORIGINAL_SUBSCRIPTION_THREAD),
                sameThreadFactory(capturedThreads.get(OFFLOADED_SUBSCRIPTION_THREAD)));
    }

    TypeSafeMatcher<Thread> sameThreadFactory(Thread matchThread) {
        return new TypeSafeMatcher<Thread>() {
            final String matchPrefix = getNamePrefix(matchThread.getName());

            @Override
            public void describeTo(final Description description) {
                description.appendText("non-matching name prefix");
            }

            @Override
            protected boolean matchesSafely(final Thread item) {
                String prefix = getNamePrefix(item.getName());

                return matchPrefix.equals(prefix);
            }
        };
    }

    private static String getNamePrefix(String name) {
        int lastDash = name.lastIndexOf('-');
        return -1 == lastDash ?
                name :
                name.substring(0, lastDash);
    }
}
