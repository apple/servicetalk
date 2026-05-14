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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpLifecycleObserver;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.servicetalk.http.utils.HttpLifecycleObservers.combine;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

class HttpLifecycleObserverFilterTest {

    @SuppressWarnings("unused")
    static Stream<Arguments> filters() {
        return Stream.of(
                Arguments.of("ServiceFilter",
                        (Function<HttpLifecycleObserver, List<? extends HttpLifecycleObserver>>)
                                o -> new HttpLifecycleObserverServiceFilter(o).lifecycleObservers()),
                Arguments.of("RequesterFilter",
                        (Function<HttpLifecycleObserver, List<? extends HttpLifecycleObserver>>)
                                o -> new HttpLifecycleObserverRequesterFilter(o).lifecycleObservers())
        );
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @MethodSource("filters")
    void singleObserver(String name, Function<HttpLifecycleObserver, List<? extends HttpLifecycleObserver>> unpack) {
        HttpLifecycleObserver observer = mock(HttpLifecycleObserver.class);
        List<? extends HttpLifecycleObserver> result = unpack.apply(observer);
        assertThat(result, hasSize(1));
        assertThat(result.get(0), is(sameInstance(observer)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @MethodSource("filters")
    void multipleObservers(String name, Function<HttpLifecycleObserver, List<? extends HttpLifecycleObserver>> unpack) {
        HttpLifecycleObserver first = mock(HttpLifecycleObserver.class);
        HttpLifecycleObserver second = mock(HttpLifecycleObserver.class);
        HttpLifecycleObserver third = mock(HttpLifecycleObserver.class);
        List<? extends HttpLifecycleObserver> result = unpack.apply(combine(first, second, third));
        assertThat(result, contains(sameInstance(first), sameInstance(second), sameInstance(third)));
    }
}
