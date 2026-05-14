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
package io.servicetalk.grpc.utils;

import io.servicetalk.grpc.api.GrpcLifecycleObserver;

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.servicetalk.grpc.utils.GrpcLifecycleObservers.combine;
import static io.servicetalk.grpc.utils.GrpcLifecycleObservers.unpack;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

class GrpcLifecycleObserversTest {

    @Test
    void unpackSingleObserver() {
        GrpcLifecycleObserver observer = mock(GrpcLifecycleObserver.class);
        List<GrpcLifecycleObserver> result = unpack(observer);
        assertThat(result, hasSize(1));
        assertThat(result.get(0), is(sameInstance(observer)));
    }

    @Test
    void unpackCombinedTwo() {
        GrpcLifecycleObserver first = mock(GrpcLifecycleObserver.class);
        GrpcLifecycleObserver second = mock(GrpcLifecycleObserver.class);
        GrpcLifecycleObserver combined = combine(first, second);
        List<GrpcLifecycleObserver> result = unpack(combined);
        assertThat(result, contains(sameInstance(first), sameInstance(second)));
    }

    @Test
    void unpackCombinedThree() {
        GrpcLifecycleObserver first = mock(GrpcLifecycleObserver.class);
        GrpcLifecycleObserver second = mock(GrpcLifecycleObserver.class);
        GrpcLifecycleObserver third = mock(GrpcLifecycleObserver.class);
        GrpcLifecycleObserver combined = combine(first, second, third);
        List<GrpcLifecycleObserver> result = unpack(combined);
        assertThat(result, contains(sameInstance(first), sameInstance(second), sameInstance(third)));
    }

    @Test
    void unpackNestedCombined() {
        GrpcLifecycleObserver a = mock(GrpcLifecycleObserver.class);
        GrpcLifecycleObserver b = mock(GrpcLifecycleObserver.class);
        GrpcLifecycleObserver c = mock(GrpcLifecycleObserver.class);
        GrpcLifecycleObserver d = mock(GrpcLifecycleObserver.class);
        GrpcLifecycleObserver inner = combine(a, b);
        GrpcLifecycleObserver outer = combine(inner, combine(c, d));
        List<GrpcLifecycleObserver> result = unpack(outer);
        assertThat(result, contains(sameInstance(a), sameInstance(b), sameInstance(c), sameInstance(d)));
    }
}
