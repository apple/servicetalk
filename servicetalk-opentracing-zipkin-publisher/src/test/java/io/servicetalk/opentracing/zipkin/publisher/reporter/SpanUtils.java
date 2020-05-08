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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

final class SpanUtils {
    private SpanUtils() {
        // no instances
    }

    static Span newSpan() {
        return Span.newBuilder()
                .name("test operation")
                .traceId("1234")
                .id(2)
                .timestamp(123456789L)
                .duration(SECONDS.toMicros(1))
                .putTag("stringKey", "string")
                .putTag("boolKey", String.valueOf(true))
                .putTag("shortKey", String.valueOf(Short.MAX_VALUE))
                .putTag("intKey", String.valueOf(Integer.MAX_VALUE))
                .putTag("longKey", String.valueOf(Long.MAX_VALUE))
                .putTag("floatKey", String.valueOf(Float.MAX_VALUE))
                .putTag("doubleKey", String.valueOf(Double.MAX_VALUE))
                .addAnnotation(System.currentTimeMillis() * 1000, "some event happened")
                .build();
    }

    static void verifySpan(final Span span) {
        assertEquals("test operation", span.name());
        assertEquals("0000000000001234", span.traceId());
        assertEquals("0000000000000002", span.id());
        assertEquals(123456789L, (long) span.timestamp());
        assertEquals(1000 * 1000, (long) span.duration());
        Map<String, String> tags = span.tags();
        assertEquals("string", tags.get("stringKey"));
        assertEquals(Boolean.TRUE.toString(), tags.get("boolKey"));
        assertEquals(String.valueOf(Short.MAX_VALUE), tags.get("shortKey"));
        assertEquals(String.valueOf(Integer.MAX_VALUE), tags.get("intKey"));
        assertEquals(String.valueOf(Long.MAX_VALUE), tags.get("longKey"));
        assertEquals(String.valueOf(Float.MAX_VALUE), tags.get("floatKey"));
        assertEquals(String.valueOf(Double.MAX_VALUE), tags.get("doubleKey"));
        assertTrue(span.annotations().stream().anyMatch(a -> a.value().equals("some event happened")));
    }
}
