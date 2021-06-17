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

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;

import static java.lang.Integer.min;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

public class PublishAndSubscribeOnTest extends AbstractPublishAndSubscribeOnTest {

    @Rule
    public final ExecutorRule<Executor> executorRule = ExecutorRule.withNamePrefix(OFFLOAD_EXECUTOR_PREFIX);

    @Test
    public void testPublishOnNoOverride() throws InterruptedException {
        Thread[] capturedThreads =
                setupAndSubscribe(Publisher::publishOn, executorRule.executor());

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD],
                sameThreadFactory(capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads[OFFLOADED_SUBSCRIBER_THREAD],
                not(sameThreadFactory(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD])));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD],
                not(sameThreadFactory(capturedThreads[OFFLOADED_SUBSCRIBER_THREAD])));
    }

    @Test
    public void testPublishOnOverride() throws InterruptedException {
        Thread[] capturedThreads =
                setupAndSubscribe(Publisher::publishOnOverride, executorRule.executor());

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD],
                not(sameThreadFactory(capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD])));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads[OFFLOADED_SUBSCRIBER_THREAD],
                not(sameThreadFactory(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD])));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD],
                sameThreadFactory(capturedThreads[OFFLOADED_SUBSCRIBER_THREAD]));
    }

    @Test
    public void testSubscribeOnNoOverride() throws InterruptedException {
        Thread[] capturedThreads =
                setupAndSubscribe(Publisher::subscribeOn, executorRule.executor());

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD],
                sameThreadFactory(capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads[OFFLOADED_SUBSCRIBER_THREAD],
                not(sameThreadFactory(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD])));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD],
                not(sameThreadFactory(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD])));
    }

    @Test
    public void testSubscribeOnOverride() throws InterruptedException {
        Thread[] capturedThreads =
                setupAndSubscribe(Publisher::subscribeOnOverride, executorRule.executor());

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD],
                not(sameThreadFactory(capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD])));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads[OFFLOADED_SUBSCRIBER_THREAD],
                not(sameThreadFactory(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD])));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD],
                sameThreadFactory(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));
    }

    @Test
    public void testNoOverride() throws InterruptedException {
        Thread[] capturedThreads =
                setupAndSubscribe(Publisher::publishAndSubscribeOn, executorRule.executor());

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD],
                sameThreadFactory(capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads[OFFLOADED_SUBSCRIBER_THREAD],
                sameThreadFactory(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD],
                not(sameThreadFactory(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD])));
    }

    @Test
    public void testOverride() throws InterruptedException {
        Thread[] capturedThreads =
                setupAndSubscribe(Publisher::publishAndSubscribeOnOverride, executorRule.executor());

        assertThat("Unexpected threads for subscription and subscriber for original source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD],
                sameThreadFactory(capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads[OFFLOADED_SUBSCRIBER_THREAD],
                sameThreadFactory(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIPTION_THREAD],
                sameThreadFactory(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));
    }

    TypeSafeMatcher<Thread> sameThreadFactory(Thread matchThread) {
        return new TypeSafeMatcher<Thread>() {
            final String matchPrefix = getNamePrefix(matchThread.getName());

            @Override
            public void describeTo(final Description description) {
                description.appendText("non-matching name prefix");
            }

            @Override
            public void describeMismatchSafely(Thread item, Description mismatchDescription) {
                String threadName = item.getName();
                String mismatch = threadName.substring(0, min(threadName.length(), matchPrefix.length()));
                mismatchDescription
                        .appendText("was ")
                        .appendValue(mismatch);
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
