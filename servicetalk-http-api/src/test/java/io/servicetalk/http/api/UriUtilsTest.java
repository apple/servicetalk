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
package io.servicetalk.http.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static io.servicetalk.http.api.DefaultHttpRequestMetaData.DEFAULT_MAX_QUERY_PARAMS;
import static io.servicetalk.http.api.UriUtils.decodeQueryParams;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UriUtilsTest {

    @ParameterizedTest(name = "{displayName} [{index}] query={0}")
    @ValueSource(strings = {"a=1&b=2", "a=1&b=2&", "a=1;b=2", "?a=1&b=2#f"})
    void decodeQueryParamsAcceptsMaxParams(String query) {
        Map<String, List<String>> params = decodeQueryParams(query, UTF_8, 2);
        assertThat(params.keySet(), contains("a", "b"));
        assertThat(params.get("a"), is(singletonList("1")));
        assertThat(params.get("b"), is(singletonList("2")));
    }

    @ParameterizedTest(name = "{displayName} [{index}] query={0}")
    @ValueSource(strings = {"a", "a=1", "a&", "a=1&"})
    void decodeQueryParamsAcceptsSingleParamWithMaxOne(String query) {
        assertThat(decodeQueryParams(query, UTF_8, 1).keySet(), contains("a"));
    }

    @Test
    void decodeQueryParamsAcceptsDefaultMaxParams() {
        StringBuilder query = new StringBuilder();
        for (int i = 0; i < DEFAULT_MAX_QUERY_PARAMS; ++i) {
            query.append('p').append(i).append("=v&");
        }
        query.setLength(query.length() - 1);
        assertThat(decodeQueryParams(query.toString(), UTF_8, DEFAULT_MAX_QUERY_PARAMS),
                aMapWithSize(DEFAULT_MAX_QUERY_PARAMS));
        query.append("&extra");
        assertThrows(IllegalStateException.class,
                () -> decodeQueryParams(query.toString(), UTF_8, DEFAULT_MAX_QUERY_PARAMS));
    }

    @ParameterizedTest(name = "{displayName} [{index}] query={0}")
    @ValueSource(strings = {"a=1&b=2&c=3", "a=1&b=2&c=3&", "a;b;c"})
    void decodeQueryParamsThrowsIfMoreThanMaxParams(String query) {
        assertThrows(IllegalStateException.class, () -> decodeQueryParams(query, UTF_8, 2));
    }
}
