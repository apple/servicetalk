/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.spi.Provider;

import java.lang.reflect.Field;

/**
 * Provider for {@link java.util.ServiceLoader} to initialize {@link ServiceTalkTracingThreadContextMap}.
 * <p>
 * This class is service loaded by log4j2 and is used to provide an MDC context map implementation that will work
 * with ServiceTalk's reactive primitives and additionally exposes tracing information (traceId, spanId, parentSpanId).
 */
public final class ServiceTalkTracingThreadContextMapProvider extends Provider {

    // Log4j2 selects a single Provider, the one with the highest priority (log4j-core's own Log4jProvider uses 10).
    // This provider must outrank the other ServiceTalk providers because
    // ServiceTalkTracingThreadContextMap is a superset of them: it keeps the AsyncContext-backed MDC behavior and
    // adds tracing keys on top. Keep in sync with the priorities used by those providers:
    //   20 - io.servicetalk.log4j2.mdc.DefaultServiceTalkThreadContextMapProvider
    private static final int PRIORITY = 25;

    private static final String DEFAULT_CURRENT_VERSION = "2.6.0";

    /**
     * Creates a new instance.
     * <p>
     * The zero-argument constructor is required by {@link java.util.ServiceLoader}.
     */
    public ServiceTalkTracingThreadContextMapProvider() {
        super(PRIORITY, getCurrentVersion(), Log4jContextFactory.class, ServiceTalkTracingThreadContextMap.class);
    }

    private static String getCurrentVersion() {
        // The CURRENT_VERSION field is only available as of 2.24.0. Once we drop support for older versions and can
        // guarantee 2.24+ at runtime we can reference Provider.CURRENT_VERSION directly.
        try {
            Field field = Provider.class.getDeclaredField("CURRENT_VERSION");
            return (String) field.get(null /* static field */);
        } catch (Exception ex) {
            return DEFAULT_CURRENT_VERSION;
        }
    }
}
