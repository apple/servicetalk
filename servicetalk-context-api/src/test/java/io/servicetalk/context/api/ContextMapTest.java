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
package io.servicetalk.context.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class ContextMapTest {

    @Test
    void testType() {
        ContextMap.Key<CharSequence> key = newKey("key", CharSequence.class);
        assertThat(key.type(), equalTo(CharSequence.class));
        assertThat(key.type().isInstance("test"), is(true));
        assertThat(key.type().isInstance(1), is(false));
        assertThat(key.type().isAssignableFrom(String.class), is(true));
        assertThat(key.type().isAssignableFrom(Integer.class), is(false));
        CharSequence castCs = key.type().cast("test");
        assertThat(castCs, equalTo("test"));
    }

    @Test
    void testGenericType() {
        @SuppressWarnings("unchecked")
        ContextMap.Key<List<String>> key = newKey("key", (Class<List<String>>) (Class<?>) List.class);
        assertThat(key.type(), equalTo(List.class));
        assertThat(key.type().isInstance(Arrays.asList("test")), is(true));
        assertThat(key.type().isInstance(Collections.singleton("test")), is(false));
        assertThat(key.type().isAssignableFrom(ArrayList.class), is(true));
        assertThat(key.type().isAssignableFrom(HashSet.class), is(false));
        List<String> castList = key.type().cast(Arrays.asList("test"));
        assertThat(castList, contains("test"));
    }
}
