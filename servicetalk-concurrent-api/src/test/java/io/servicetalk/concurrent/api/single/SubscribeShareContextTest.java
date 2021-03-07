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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SubscribeShareContextTest {

    public static final AsyncContextMap.Key<String> KEY = AsyncContextMap.Key.newKey("share-context-key");

    @Test
    public void contextIsShared() throws Exception {
        AsyncContext.put(KEY, "v1");
        succeeded(1).beforeOnSuccess(__ -> AsyncContext.put(KEY, "v2")).subscribeShareContext().toFuture().get();
        assertThat("Unexpected value found in the context.", AsyncContext.get(KEY), is("v2"));
    }

    @Test
    public void contextIsNotSharedIfNotLastOperator() throws Exception {
        // When we support this feature, then we can change this test
        AsyncContext.put(KEY, "v1");
        succeeded(1).beforeOnSuccess(__ -> AsyncContext.put(KEY, "v2"))
                .subscribeShareContext().beforeOnSuccess(__ -> { }).toFuture().get();
        assertThat("Unexpected value found in the context.", AsyncContext.get(KEY), is("v1"));
    }
}
