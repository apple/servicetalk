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
package io.servicetalk.opentracing.inmemory;

import io.servicetalk.opentracing.inmemory.api.InMemoryTraceState;
import io.servicetalk.opentracing.inmemory.api.InMemoryTraceStateFormat;

import javax.annotation.Nullable;

import static io.servicetalk.opentracing.inmemory.SingleLineValue.format;
import static io.servicetalk.opentracing.internal.HexUtils.validateHexBytes;
import static io.servicetalk.opentracing.internal.TracingConstants.NO_PARENT_ID;

/**
 * Single-line serialization format. Examples:
 * <ul>
 * <li>00000000000B75A2.00000000000B75A2&lt;:000000000015C003 (no sampling information)</li>
 * <li>00000000000B75A2.00000000000B75A2&lt;:000000000015C003:1 (sampling=true)</li>
 * </ul>
 */
public final class SingleLineFormatter implements InMemoryTraceStateFormat<SingleLineValue> {
    /**
     * Singleton instance.
     */
    public static final SingleLineFormatter INSTANCE = new SingleLineFormatter();

    private SingleLineFormatter() {
        // singleton
    }

    @Override
    public void inject(InMemoryTraceState state, SingleLineValue carrier) {
        carrier.set(format(state.traceIdHex(), state.spanIdHex(), state.parentSpanIdHex(), state.isSampled()));
    }

    @Nullable
    @Override
    public InMemoryTraceState extract(SingleLineValue carrier) {
        String value = carrier.get();
        if (value == null) {
            return null;
        }

        // If a value is present we assume it is well-formed. Exceptions will be thrown if
        // the value is not in a valid format.
        int cursor = 0;

        int i1 = value.indexOf('.');
        String traceIdHex = validateHexBytes(value.substring(cursor, i1));
        cursor = i1 + 1;

        int i2 = value.indexOf("<:", cursor);
        String spanIdHex = validateHexBytes(value.substring(cursor, i2));
        cursor = i2 + 2;

        int i3 = value.indexOf(':', cursor);
        if (i3 < 0) {
            i3 = value.length();
        }
        String parentSpanIdHex = value.substring(cursor, i3);
        String parentSpanIdResolved = NO_PARENT_ID.equals(parentSpanIdHex) ? null : validateHexBytes(parentSpanIdHex);

        // If the sampling flag is present, i3 should be pointing to the next-to-last
        // character of the string (......:0)
        boolean sampled = false;
        if (i3 == value.length() - 2) {
            sampled = '1' == value.charAt(i3 + 1);
        }

        return new DefaultInMemoryTraceState(traceIdHex, spanIdHex, parentSpanIdResolved, sampled);
    }
}
