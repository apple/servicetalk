/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class SingleCastTest {
    private final TestSingle<Object> source = new TestSingle<>();
    private final TestSingleSubscriber<Integer> subscriber = new TestSingleSubscriber<>();

    @ParameterizedTest
    @MethodSource("correctTypeParams")
    void correctTypeSucceeds(Integer v) {
        toSource(source.cast(Integer.class)).subscribe(subscriber);
        subscriber.awaitSubscription();
        source.onSuccess(v);
        assertThat(subscriber.awaitOnSuccess(), is(v));
    }

    @Test
    void mixedTypeFails() {
        toSource(source.cast(Integer.class)).subscribe(subscriber);
        subscriber.awaitSubscription();
        source.onSuccess("error");
        assertThat(subscriber.awaitOnError(), instanceOf(ClassCastException.class));
    }

    private static Stream<Integer> correctTypeParams() {
        return Stream.of(1, null);
    }
}
