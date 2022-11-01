/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.opentelemetry;

import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;

import io.opentelemetry.context.propagation.TextMapGetter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class HeadersPropagatorGetterTest {

    @Test
    void shouldGetAllKeys() {

        final TextMapGetter<HttpHeaders> getter = HeadersPropagatorGetter.INSTANCE;
        HttpHeaders httpHeaders = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        httpHeaders.set("a", "1");
        httpHeaders.set("b", "2");

        final Iterable<String> keys = getter.keys(httpHeaders);

        assertThat(keys).containsAll(Arrays.asList("a", "b"));
    }

    @Test
    void shouldReturnNullWhenThereIsNotKeyInCarrier() {

        final TextMapGetter<HttpHeaders> getter = HeadersPropagatorGetter.INSTANCE;

        HttpHeaders carrier = DefaultHttpHeadersFactory.INSTANCE.newHeaders();

        assertThat(getter.get(carrier, "c")).isNull();
    }

    @Test
    void shouldReturnValueWhenThereIsAKeyInCarrierCaseInsensitive() {

        final TextMapGetter<HttpHeaders> getter = HeadersPropagatorGetter.INSTANCE;

        HttpHeaders carrier = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        carrier.set("A", "1");

        assertThat(getter.get(carrier, "A")).isEqualTo("1");
    }

    @Test
    void shouldReturnNullWhenCarrierIsNull() {
        final TextMapGetter<HttpHeaders> getter = HeadersPropagatorGetter.INSTANCE;

        assertThat(getter.get(null, "A")).isNull();
    }
}
