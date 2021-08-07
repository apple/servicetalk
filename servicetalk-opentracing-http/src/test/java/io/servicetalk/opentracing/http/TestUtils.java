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

import io.servicetalk.http.api.HttpSerializerDeserializer;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanEventListener;
import io.servicetalk.opentracing.internal.TracingConstants;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.data.jackson.JacksonSerializerFactory.JACKSON;
import static io.servicetalk.http.api.HttpSerializers.jsonSerializer;
import static io.servicetalk.log4j2.mdc.utils.LoggerStringWriter.assertContainsMdcPair;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TestUtils {
    static final String[] TRACING_TEST_LOG_LINE_PREFIX = new String[] {
            "filter request path={}",
            "filter response map path={}",
            "filter response transform path={}",
            "filter response onSubscribe path={}",
            "filter response onNext path={}",
            "filter response terminated path={}"};
    static final HttpSerializerDeserializer<TestSpanState> SPAN_STATE_SERIALIZER =
            jsonSerializer(JACKSON.serializerDeserializer(TestSpanState.class));

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

    static void verifyTraceIdPresentInLogs(String logs, String requestPath, String traceId, String spanId,
                                           @Nullable String parentSpanId, String[] logLinePrefix) {
        if (parentSpanId == null) {
            parentSpanId = TracingConstants.NO_PARENT_ID;
        }
        String[] lines = logs.split("\\r?\\n");
        for (final String linePrefix : logLinePrefix) {
            String prefix = linePrefix.replaceFirst("\\{}", requestPath);
            boolean foundMatch = false;
            for (String line : lines) {
                int matchIndex = line.indexOf(prefix);
                if (matchIndex != -1) {
                    foundMatch = true;
                    try {
                        assertContainsMdcPair(line, "traceId=", traceId);
                        assertContainsMdcPair(line, "spanId=", spanId);
                        assertContainsMdcPair(line, "parentSpanId=", parentSpanId);
                    } catch (Throwable cause) {
                        cause.addSuppressed(new AssertionError("failed on prefix: " + prefix));
                        throw cause;
                    }
                    break;
                }
            }
            assertTrue(foundMatch, "could not find log line with prefix: " + prefix);
        }
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
