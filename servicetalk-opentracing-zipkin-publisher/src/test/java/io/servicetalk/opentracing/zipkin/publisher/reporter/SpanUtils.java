/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentracing.zipkin.publisher.reporter;

import zipkin2.Span;

import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpanUtils {

    private static final long TIMESTAMP = 123456789L;
    private static final String TRACE_ID = "0000000000001234";
    private static final String SPAN_ID = "0000000000000002";
    private static final long DURATION = SECONDS.toMicros(1);
    private static final String STRING_KEY_TAG_NAME = "stringKey";
    private static final String STRING_KEY_TAG_VALUE = "string";
    private static final String BOOL_KEY_TAG_NAME = "boolKey";
    private static final String SHORT_KEY_TAG_NAME = "shortKey";
    private static final String INT_KEY_TAG_NAME = "intKey";
    private static final String LONG_KEY_TAG_NAME = "longKey";
    private static final String FLOAT_KEY_TAG_NAME = "floatKey";
    private static final String DOUBLE_KEY_TAG_NAME = "doubleKey";
    private static final String ANNOTATION_VAL = "some event happened";

    private SpanUtils() {
        // no instances
    }

    static Span newSpan(final String name) {
        return Span.newBuilder()
                .name(name)
                .traceId(TRACE_ID)
                .id(SPAN_ID)
                .timestamp(TIMESTAMP)
                .duration(DURATION)
                .putTag(STRING_KEY_TAG_NAME, STRING_KEY_TAG_VALUE)
                .putTag(BOOL_KEY_TAG_NAME, String.valueOf(true))
                .putTag(SHORT_KEY_TAG_NAME, String.valueOf(Short.MAX_VALUE))
                .putTag(INT_KEY_TAG_NAME, String.valueOf(Integer.MAX_VALUE))
                .putTag(LONG_KEY_TAG_NAME, String.valueOf(Long.MAX_VALUE))
                .putTag(FLOAT_KEY_TAG_NAME, String.valueOf(Float.MAX_VALUE))
                .putTag(DOUBLE_KEY_TAG_NAME, String.valueOf(Double.MAX_VALUE))
                .addAnnotation(System.currentTimeMillis() * 1000, ANNOTATION_VAL)
                .build();
    }

    static void verifySpan(final Span span, final String expectedName) {
        assertEquals(expectedName, span.name());
        assertEquals(TRACE_ID, span.traceId());
        assertEquals(SPAN_ID, span.id());
        assertEquals(TIMESTAMP, (long) span.timestamp());
        assertEquals(DURATION, (long) span.duration());
        Map<String, String> tags = span.tags();
        assertEquals(STRING_KEY_TAG_VALUE, tags.get(STRING_KEY_TAG_NAME));
        assertEquals(Boolean.TRUE.toString(), tags.get(BOOL_KEY_TAG_NAME));
        assertEquals(String.valueOf(Short.MAX_VALUE), tags.get(SHORT_KEY_TAG_NAME));
        assertEquals(String.valueOf(Integer.MAX_VALUE), tags.get(INT_KEY_TAG_NAME));
        assertEquals(String.valueOf(Long.MAX_VALUE), tags.get(LONG_KEY_TAG_NAME));
        assertEquals(String.valueOf(Float.MAX_VALUE), tags.get(FLOAT_KEY_TAG_NAME));
        assertEquals(String.valueOf(Double.MAX_VALUE), tags.get(DOUBLE_KEY_TAG_NAME));
        assertTrue(span.annotations().stream().anyMatch(a -> a.value().equals(ANNOTATION_VAL)));
    }
}
