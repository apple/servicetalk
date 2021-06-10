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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static org.junit.jupiter.api.Assertions.fail;

class DefaultClientGroupTest {

    @Test
    void requestingClientFromClosedClientGroupShouldNotHang() throws Exception {
        DefaultClientGroup<String, ListenableAsyncCloseable> cg =
                new DefaultClientGroup<>(s -> emptyAsyncCloseable());
        cg.closeAsync().toFuture().get();

        try {
            cg.get("foo");
            fail("ClientGroup is closed, cg.get() should throw");
        } catch (IllegalStateException e) {
            // Expected
        }

        try {
            cg.get("foo"); // Ensure this doesn't hang
            fail("ClientGroup is closed, cg.get() should throw");
        } catch (IllegalStateException e) {
            // Expected
        }
    }
}
