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

import java.time.Duration;

import static io.servicetalk.concurrent.api.Completable.completed;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertSame;

public class CompletableExecutorPreservationTest {
    @RegisterExtension
    public static final ExecutorExtension EXEC = ExecutorExtension.withNamePrefix("test");

    private Completable completable;

    @BeforeEach
    public void setupCompletable() {
        completable = completed().publishAndSubscribeOnOverride(EXEC.executor());
    }

    @Test
    public void testTimeoutCompletable() {
        assertSame(EXEC.executor(), completable.idleTimeout(1, MILLISECONDS).executor());
        assertSame(EXEC.executor(), completable.idleTimeout(Duration.ofMillis(1)).executor());
    }

    @Test
    public void testBeforeFinallyCompletable() {
        assertSame(EXEC.executor(), completable.beforeFinally(() -> { /* NOOP */ }).executor());
    }
}
