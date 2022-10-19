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

import io.servicetalk.http.api.HttpHeaders;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;

class HeadersPropagatorGetterTest {

    @Test
    void shouldGetAllKeys() {

        final HeadersPropagatorGetter getter = new HeadersPropagatorGetter();
        HttpHeaders carrier = mock(HttpHeaders.class);
        Set<CharSequence> set = new HashSet<>();
        set.add("a");
        set.add("b");
        doReturn(set).when(carrier).names();

        final Iterable<String> keys = getter.keys(carrier);

        assertThat(keys).containsAll(Arrays.asList("a", "b"));
    }

    @Test
    void shouldReturnNullWhenThereIsNotKeyInCarrier() {

        final HeadersPropagatorGetter getter = new HeadersPropagatorGetter();

        HttpHeaders carrier = mock(HttpHeaders.class);

        doReturn(false).when(carrier).contains(anyString());

        assertThat(getter.get(carrier, "c")).isNull();
    }

    @Test
    void shouldReturnValueWhenThereIsAKeyInCarrierCaseInsensitive() {

        final HeadersPropagatorGetter getter = new HeadersPropagatorGetter();

        HttpHeaders carrier = mock(HttpHeaders.class);

        doReturn(true).when(carrier).contains(eq("A"));
        doReturn("1").when(carrier).get(eq("A"));

        assertThat(getter.get(carrier, "A")).isEqualTo("1");
    }

    @Test
    void shouldReturnNullWhenCarrierIsNull() {
        final HeadersPropagatorGetter getter = new HeadersPropagatorGetter();

        assertThat(getter.get(null, "A")).isNull();
    }
}
