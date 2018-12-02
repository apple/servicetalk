/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentracing.http;

import io.servicetalk.opentracing.inmemory.api.InMemorySpan;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanEventListener;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

final class TestUtils {
    private TestUtils() { } // no instantiation

    static String randomHexId() {
        return getRandomHexString(16);
    }

    private static String getRandomHexString(int numchars) {
        StringBuilder sb = new StringBuilder(numchars);
        do {
            sb.append(Integer.toHexString(ThreadLocalRandom.current().nextInt()));
        } while (sb.length() < numchars);

        return sb.toString().substring(0, numchars);
    }

    static Matcher<String> isHexId() {
        return new TypeSafeMatcher<String>() {
            @Override
            protected boolean matchesSafely(String s) {
                return s.length() == 16 && s.chars().allMatch(i ->
                        (i >= '0' && i <= '9') || (i >= 'a' && i <= 'f') || (i >= 'A' && i <= 'F'));
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("should contain exactly 16 hex digits [0-9a-fA-F]{16}");
            }
        };
    }

    static final class CountingInMemorySpanEventListener implements InMemorySpanEventListener {
        private final AtomicInteger finishCount;
        @Nullable
        private volatile InMemorySpan lastFinishedSpan;

        CountingInMemorySpanEventListener() {
            finishCount = new AtomicInteger();
        }

        @Override
        public void onSpanStarted(final InMemorySpan span) {
        }

        @Override
        public void onEventLogged(final InMemorySpan span, final long epochMicros, final String eventName) {
        }

        @Override
        public void onEventLogged(final InMemorySpan span, final long epochMicros,
                                  final Map<String, ?> fields) {
        }

        @Override
        public void onSpanFinished(final InMemorySpan span, final long durationMicros) {
            lastFinishedSpan = span;
            finishCount.incrementAndGet();
        }

        @Nullable
        InMemorySpan lastFinishedSpan() {
            return lastFinishedSpan;
        }

        int spanFinishedCount() {
            return finishCount.get();
        }
    }
}
