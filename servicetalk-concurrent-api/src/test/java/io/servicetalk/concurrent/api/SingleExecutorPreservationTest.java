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

import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertSame;

class SingleExecutorPreservationTest {
    @RegisterExtension
    static final ExecutorExtension<Executor> EXEC = ExecutorExtension.withCachedExecutor("test");

    private Single<String> single;

    @BeforeEach
    void setupSingle() {
        single = Single.<String>never().publishAndSubscribeOnOverride(EXEC.executor());
    }

    @Test
    void testTimeoutSingle() {
        assertSame(EXEC.executor(), single.timeout(1, MILLISECONDS).executor());
        assertSame(EXEC.executor(), single.timeout(ofMillis(1)).executor());
    }

    @Test
    void testAfterFinallySingle() {
        assertSame(EXEC.executor(), single.afterFinally(() -> { /* NOOP */ }).executor());
    }
}
