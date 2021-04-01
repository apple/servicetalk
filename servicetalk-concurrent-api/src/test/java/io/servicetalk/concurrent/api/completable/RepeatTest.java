/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.completable;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class RepeatTest {

    @Test
    public void repeatValueSupplier() throws Exception {
        Collection<Integer> repeats = completed().repeat(count -> count < 2).map(new Function<Object, Integer>() {
            private int count;

            @Override
            public Integer apply(final Object o) {
                return ++count;
            }
        }).toFuture().get();
        assertThat("Unexpected items received.", repeats, contains(1, 2));
    }

    @Test
    public void repeatWhenValueSupplier() throws Exception {
        Collection<Integer> repeats = completed().repeatWhen(count ->
                        count < 2 ? completed() : failed(DELIBERATE_EXCEPTION)).map(
            new Function<Object, Integer>() {
                private int count;
            @Override
            public Integer apply(final Object o) {
                return ++count;
            }
        }).toFuture().get();
        assertThat("Unexpected items received.", repeats, contains(1, 2));
    }
}
