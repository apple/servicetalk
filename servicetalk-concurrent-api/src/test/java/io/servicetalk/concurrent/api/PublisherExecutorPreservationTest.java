/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertSame;

class PublisherExecutorPreservationTest {
    @RegisterExtension
    static final ExecutorExtension<Executor> EXEC = ExecutorExtension.withCachedExecutor("test");

    private Publisher<String> publisher;

    @BeforeEach
    void setupPublisher() {
        publisher = Publisher.<String>empty().publishAndSubscribeOnOverride(EXEC.executor());
    }

    @Test
    void testPubToSingle() {
        assertSame(EXEC.executor(), publisher.firstOrElse(() -> null).executor());
    }

    @Test
    void testPubToSingleOrError() {
        assertSame(EXEC.executor(), publisher.firstOrError().executor());
    }

    @Test
    void testPubToCompletable() {
        assertSame(EXEC.executor(), publisher.ignoreElements().executor());
    }

    @Test
    void testReduceSingle() {
        assertSame(EXEC.executor(), publisher.collect(() -> 0, (n, s) -> n += s.length()).executor());
    }
}
