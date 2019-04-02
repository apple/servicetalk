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
/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.servicetalk.http.api;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.servicetalk.http.api.HttpUri.decodeComponent;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Splits an HTTP query string into a path string and key-value parameter pairs.
 * This decoder is for one time use only.  Create a new instance for each URI:
 * <pre>
 * {@link QueryStringDecoder} decoder = new {@link QueryStringDecoder}("/hello?recipient=world&x=1;y=2");
 * assert decoder.path().equals("/hello");
 * assert decoder.parameters().get("recipient").get(0).equals("world");
 * assert decoder.parameters().get("x").get(0).equals("1");
 * assert decoder.parameters().get("y").get(0).equals("2");
 * </pre>
 * <p>
 * This decoder can also decode the content of an HTTP POST request whose
 * content type is <code>application/x-www-form-urlencoded</code>:
 * <pre>
 * {@link QueryStringDecoder} decoder = new {@link QueryStringDecoder}("recipient=world&x=1;y=2", false);
 * ...
 * </pre>
 *
 * <h3>HashDOS vulnerability fix</h3>
 * <p>
 * As a workaround to the <a href="http://netty.io/s/hashdos">HashDOS</a> vulnerability, the decoder
 * limits the maximum number of decoded key-value parameter pairs, up to {@literal 1024} by
 * default, and you can configure it when you construct the decoder by passing an additional
 * integer parameter.
 *
 * @see QueryStringEncoder
 */
final class QueryStringDecoder {
    private static final int DEFAULT_MAX_PARAMS = 1024;

    private QueryStringDecoder() {
        // no instances
    }

    /**
     * Decode the specified raw query. The decoder will assume that the query string is encoded in UTF-8.
     */
    static Map<String, List<String>> decodeParams(final String rawQuery) {
        return decodeParams(rawQuery, UTF_8);
    }

    /**
     * Decode the specified raw query with the specified {@code charset}.
     */
    static Map<String, List<String>> decodeParams(final String rawQuery, final Charset charset) {
        return decodeParams(rawQuery, charset, DEFAULT_MAX_PARAMS);
    }

    /**
     * Decode the specified raw query with the specified {@code charset} for the specified maximum number of parameters.
     */
    static Map<String, List<String>> decodeParams(final String rawQuery, final Charset charset, final int maxParams) {
        if (maxParams <= 0) {
            throw new IllegalArgumentException("maxParams: " + maxParams + " (expected: > 0)");
        }

        if (rawQuery.isEmpty()) {
            return new LinkedHashMap<>(2);
        }

        final Map<String, List<String>> params = new LinkedHashMap<>();
        int paramCountDown = maxParams;
        final int from = rawQuery.charAt(0) == '?' ? 1 : 0;
        final int len = rawQuery.length();
        int nameStart = from;
        int valueStart = -1;
        int i;
        loop:
        for (i = from; i < len; i++) {
            switch (rawQuery.charAt(i)) {
                case '=':
                    if (nameStart == i) {
                        nameStart = i + 1;
                    } else if (valueStart < nameStart) {
                        valueStart = i + 1;
                    }
                    break;
                case '&':
                case ';':
                    if (addParam(rawQuery, nameStart, valueStart, i, charset, params)) {
                        paramCountDown--;
                        if (paramCountDown == 0) {
                            return params;
                        }
                    }
                    nameStart = i + 1;
                    break;
                case '#':
                    break loop;
                default:
                    // continue
            }
        }
        addParam(rawQuery, nameStart, valueStart, i, charset, params);
        return params;
    }

    private static boolean addParam(final String s, final int nameStart, int valueStart, final int valueEnd,
                                    final Charset charset,
                                    final Map<String, List<String>> params) {
        if (nameStart >= valueEnd) {
            return false;
        }
        if (valueStart <= nameStart) {
            valueStart = valueEnd + 1;
        }
        final String name = decodeComponent(s, nameStart, valueStart - 1, false, charset);
        final String value = decodeComponent(s, valueStart, valueEnd, false, charset);
        final List<String> values = params.computeIfAbsent(name, k -> new ArrayList<>(1)); // Often there's only 1 value
        values.add(value);
        return true;
    }
}
