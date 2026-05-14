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
package io.servicetalk.grpc.netty;

import io.servicetalk.grpc.api.GrpcLifecycleObserver;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.servicetalk.grpc.utils.GrpcLifecycleObservers.combine;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

class GrpcLifecycleObserverFilterTest {

    @SuppressWarnings("unused")
    static Stream<Arguments> filters() {
        return Stream.of(
                Arguments.of("ServiceFilter",
                        (Function<GrpcLifecycleObserver, List<? extends GrpcLifecycleObserver>>)
                                o -> new GrpcLifecycleObserverServiceFilter(o).lifecycleObservers()),
                Arguments.of("RequesterFilter",
                        (Function<GrpcLifecycleObserver, List<? extends GrpcLifecycleObserver>>)
                                o -> new GrpcLifecycleObserverRequesterFilter(o).lifecycleObservers())
        );
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @MethodSource("filters")
    void singleObserver(String name, Function<GrpcLifecycleObserver, List<? extends GrpcLifecycleObserver>> unpack) {
        GrpcLifecycleObserver observer = mock(GrpcLifecycleObserver.class);
        List<? extends GrpcLifecycleObserver> result = unpack.apply(observer);
        assertThat(result, hasSize(1));
        assertThat(result.get(0), is(sameInstance(observer)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @MethodSource("filters")
    void multipleObservers(String name, Function<GrpcLifecycleObserver, List<? extends GrpcLifecycleObserver>> unpack) {
        GrpcLifecycleObserver first = mock(GrpcLifecycleObserver.class);
        GrpcLifecycleObserver second = mock(GrpcLifecycleObserver.class);
        GrpcLifecycleObserver third = mock(GrpcLifecycleObserver.class);
        List<? extends GrpcLifecycleObserver> result = unpack.apply(combine(first, second, third));
        assertThat(result, contains(sameInstance(first), sameInstance(second), sameInstance(third)));
    }
}
