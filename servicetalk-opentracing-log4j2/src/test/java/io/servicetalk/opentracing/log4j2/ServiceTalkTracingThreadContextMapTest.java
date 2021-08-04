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
package io.servicetalk.opentracing.log4j2;

import io.servicetalk.log4j2.mdc.utils.LoggerStringWriter;
import io.servicetalk.opentracing.inmemory.DefaultInMemoryTracer;
import io.servicetalk.opentracing.inmemory.api.InMemorySpan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static io.servicetalk.log4j2.mdc.utils.LoggerStringWriter.assertContainsMdcPair;
import static io.servicetalk.log4j2.mdc.utils.LoggerStringWriter.stableAccumulated;
import static io.servicetalk.opentracing.asynccontext.AsyncContextInMemoryScopeManager.SCOPE_MANAGER;
import static io.servicetalk.opentracing.internal.TracingIdUtils.idOrNullAsValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServiceTalkTracingThreadContextMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTalkTracingThreadContextMapTest.class);

    @BeforeEach
    public void setup() {
        LoggerStringWriter.reset();
    }

    @AfterEach
    public void tearDown() {
        LoggerStringWriter.remove();
    }

    @Test
    void testNullValues() {
        MDC.put("foo", null);
        assertNull(MDC.get("foo"));
    }

    @Test
    void tracingInfoDisplayedPresentInLogsViaMDC() throws Exception {
        DefaultInMemoryTracer tracer = new DefaultInMemoryTracer.Builder(SCOPE_MANAGER).build();
        InMemorySpan span = tracer.buildSpan("test").start();
        assertNotNull(span);
        tracer.activateSpan(span);

        LOGGER.debug("testing logging and MDC");
        String v = stableAccumulated(1000);
        assertContainsMdcPair(v, "traceId=", span.context().toTraceId());
        assertContainsMdcPair(v, "spanId=", span.context().toSpanId());
        assertContainsMdcPair(v, "parentSpanId=", idOrNullAsValue(span.context().parentSpanId()));
    }
}
