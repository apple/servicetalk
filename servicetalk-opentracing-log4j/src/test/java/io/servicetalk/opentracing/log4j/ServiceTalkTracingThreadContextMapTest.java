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
package io.servicetalk.opentracing.log4j;

import io.servicetalk.opentracing.inmemory.DefaultInMemoryTracer;
import io.servicetalk.opentracing.inmemory.api.InMemoryScope;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.opentracing.asynccontext.AsyncContextInMemoryScopeManager.SCOPE_MANAGER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

public class ServiceTalkTracingThreadContextMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTalkTracingThreadContextMapTest.class);

    @Before
    public void setup() {
        LoggerStringWriter.reset();
    }

    @Test
    public void tracingInfoDisplayedPresentInLogsViaMDC() {
        DefaultInMemoryTracer tracer = new DefaultInMemoryTracer.Builder(SCOPE_MANAGER).build();
        try (InMemoryScope scope = tracer.buildSpan("test").startActive(true)) {
            assertNotNull(scope);
            InMemorySpan span = scope.span();
            assertNotNull(span);

            LOGGER.debug("testing logging and MDC");
            String v = LoggerStringWriter.getAccumulated();
            assertStringContains(v, "traceId=", span.traceIdHex());
            assertStringContains(v, "spanId=", span.spanIdHex());
            assertStringContains(v, "parentSpanId=", span.nonnullParentSpanIdHex());
        }
    }

    private static void assertStringContains(String value, String expectedLabel, String expectedValue) {
        int x = value.indexOf(expectedLabel);
        assertThat("couldn't find expectedLabel: " + expectedLabel, x, is(greaterThanOrEqualTo(0)));
        int beginIndex = x + expectedLabel.length();
        assertThat(value.substring(beginIndex, beginIndex + expectedValue.length()), is(expectedValue));
    }
}
