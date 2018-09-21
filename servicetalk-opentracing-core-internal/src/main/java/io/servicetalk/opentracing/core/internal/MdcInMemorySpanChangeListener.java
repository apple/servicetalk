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
package io.servicetalk.opentracing.core.internal;

import org.slf4j.MDC;

import javax.annotation.Nullable;

/**
 * {@link ListenableInMemoryScopeManager.InMemorySpanChangeListener} for setting trace keys into the current MDC.
 */
public final class MdcInMemorySpanChangeListener implements ListenableInMemoryScopeManager.InMemorySpanChangeListener {
    public static final MdcInMemorySpanChangeListener INSTANCE = new MdcInMemorySpanChangeListener();

    private MdcInMemorySpanChangeListener() {
        // singleton
    }

    @Override
    public void spanChanged(@Nullable final InMemorySpan oldSpan, @Nullable final InMemorySpan newSpan) {
        // If there was no oldSpan, or oldSpan and newSpan are of the same "type class" then we can directly
        // put/replace.
        if (oldSpan == null && newSpan != null) {
            putSpanInMDC(newSpan);
        } else {
            // oldSpan and newSpan are of different type classes, and we must remove then put.
            MDC.remove("traceId");
            MDC.remove("spanId");
            MDC.remove("parentSpanId");
            if (newSpan != null) {
                putSpanInMDC(newSpan);
            }
        }
    }

    private static void putSpanInMDC(InMemorySpan newSpan) {
        MDC.put("traceId", newSpan.traceIdHex());
        MDC.put("spanId", newSpan.spanIdHex());
        MDC.put("parentSpanId", newSpan.nonnullParentSpanIdHex());
    }
}
