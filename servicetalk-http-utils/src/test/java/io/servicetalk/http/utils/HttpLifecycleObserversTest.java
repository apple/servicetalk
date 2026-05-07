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
package io.servicetalk.http.utils;

import io.servicetalk.http.api.HttpLifecycleObserver;

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.servicetalk.http.utils.HttpLifecycleObservers.combine;
import static io.servicetalk.http.utils.HttpLifecycleObservers.unpack;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

class HttpLifecycleObserversTest {

    @Test
    void unpackSingleObserver() {
        HttpLifecycleObserver observer = mock(HttpLifecycleObserver.class);
        List<HttpLifecycleObserver> result = unpack(observer);
        assertThat(result, hasSize(1));
        assertThat(result.get(0), is(sameInstance(observer)));
    }

    @Test
    void unpackCombinedTwo() {
        HttpLifecycleObserver first = mock(HttpLifecycleObserver.class);
        HttpLifecycleObserver second = mock(HttpLifecycleObserver.class);
        HttpLifecycleObserver combined = combine(first, second);
        List<HttpLifecycleObserver> result = unpack(combined);
        assertThat(result, contains(sameInstance(first), sameInstance(second)));
    }

    @Test
    void unpackCombinedThree() {
        HttpLifecycleObserver first = mock(HttpLifecycleObserver.class);
        HttpLifecycleObserver second = mock(HttpLifecycleObserver.class);
        HttpLifecycleObserver third = mock(HttpLifecycleObserver.class);
        HttpLifecycleObserver combined = combine(first, second, third);
        List<HttpLifecycleObserver> result = unpack(combined);
        assertThat(result, contains(sameInstance(first), sameInstance(second), sameInstance(third)));
    }

    @Test
    void unpackNestedCombined() {
        HttpLifecycleObserver a = mock(HttpLifecycleObserver.class);
        HttpLifecycleObserver b = mock(HttpLifecycleObserver.class);
        HttpLifecycleObserver c = mock(HttpLifecycleObserver.class);
        HttpLifecycleObserver d = mock(HttpLifecycleObserver.class);
        HttpLifecycleObserver inner = combine(a, b);
        HttpLifecycleObserver outer = combine(inner, combine(c, d));
        List<HttpLifecycleObserver> result = unpack(outer);
        assertThat(result, contains(sameInstance(a), sameInstance(b), sameInstance(c), sameInstance(d)));
    }
}
