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
package io.servicetalk.opentracing.log4j2;

import io.servicetalk.log4j2.mdc.utils.LoggerStringWriter;
import io.servicetalk.opentracing.inmemory.DefaultInMemoryTracer;
import io.servicetalk.opentracing.inmemory.api.InMemoryScope;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static io.servicetalk.log4j2.mdc.utils.LoggerStringWriter.assertContainsMdcPair;
import static io.servicetalk.log4j2.mdc.utils.LoggerStringWriter.stableAccumulated;
import static io.servicetalk.opentracing.asynccontext.AsyncContextInMemoryScopeManager.SCOPE_MANAGER;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ServiceTalkTracingThreadContextMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTalkTracingThreadContextMapTest.class);

    @Before
    public void setup() {
        LoggerStringWriter.reset();
    }

    @After
    public void tearDown() {
        LoggerStringWriter.remove();
    }

    @Test
    public void testNullValues() {
        MDC.put("foo", null);
        assertNull(MDC.get("foo"));
    }

    @Test
    public void tracingInfoDisplayedPresentInLogsViaMDC() throws Exception {
        DefaultInMemoryTracer tracer = new DefaultInMemoryTracer.Builder(SCOPE_MANAGER).build();
        try (InMemoryScope scope = tracer.buildSpan("test").startActive(true)) {
            assertNotNull(scope);
            InMemorySpan span = scope.span();
            assertNotNull(span);

            LOGGER.debug("testing logging and MDC");
            String v = stableAccumulated(1000);
            assertContainsMdcPair(v, "traceId=", span.traceIdHex());
            assertContainsMdcPair(v, "spanId=", span.spanIdHex());
            assertContainsMdcPair(v, "parentSpanId=", span.nonnullParentSpanIdHex());
        }
    }
}
