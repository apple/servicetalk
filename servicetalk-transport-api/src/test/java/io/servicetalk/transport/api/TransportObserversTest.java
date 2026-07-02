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
package io.servicetalk.transport.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.servicetalk.transport.api.TransportObservers.asSafeObserver;
import static io.servicetalk.transport.api.TransportObservers.combine;
import static io.servicetalk.transport.api.TransportObservers.unpack;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;

class TransportObserversTest {

    @Test
    void unpackSingleObserver() {
        TransportObserver observer = mock(TransportObserver.class);
        List<TransportObserver> result = unpack(observer);
        assertThat(result, hasSize(1));
        assertThat(result.get(0), is(sameInstance(observer)));
    }

    @Test
    void unpackSafeObserver() {
        TransportObserver observer = mock(TransportObserver.class);
        List<TransportObserver> result = unpack(asSafeObserver(observer));
        assertThat(result, contains(sameInstance(observer)));
    }

    @Test
    void unpackCombinedTwo() {
        TransportObserver first = mock(TransportObserver.class);
        TransportObserver second = mock(TransportObserver.class);
        TransportObserver combined = combine(first, second);
        List<TransportObserver> result = unpack(combined);
        assertThat(result, contains(sameInstance(first), sameInstance(second)));
    }

    @Test
    void unpackCombinedThree() {
        TransportObserver first = mock(TransportObserver.class);
        TransportObserver second = mock(TransportObserver.class);
        TransportObserver third = mock(TransportObserver.class);
        TransportObserver combined = combine(first, second, third);
        List<TransportObserver> result = unpack(combined);
        assertThat(result, contains(sameInstance(first), sameInstance(second), sameInstance(third)));
    }

    @Test
    void unpackNestedCombined() {
        TransportObserver a = mock(TransportObserver.class);
        TransportObserver b = mock(TransportObserver.class);
        TransportObserver c = mock(TransportObserver.class);
        TransportObserver d = mock(TransportObserver.class);
        TransportObserver inner = combine(a, b);
        TransportObserver outer = combine(inner, combine(c, d));
        List<TransportObserver> result = unpack(outer);
        assertThat(result, contains(sameInstance(a), sameInstance(b), sameInstance(c), sameInstance(d)));
    }
}
