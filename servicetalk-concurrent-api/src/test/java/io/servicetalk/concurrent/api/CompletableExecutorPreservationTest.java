/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;

import static io.servicetalk.concurrent.api.Completable.completed;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertSame;

public class CompletableExecutorPreservationTest {
    @ClassRule
    public static final ExecutorRule EXEC = ExecutorRule.withNamePrefix("test-");

    private Completable completable;

    @Before
    public void setupCompletable() {
        completable = completed().publishAndSubscribeOnOverride(EXEC.executor());
    }

    @Test
    public void testTimeoutCompletable() {
        assertSame(EXEC.executor(), completable.timeout(1, MILLISECONDS).executor());
        assertSame(EXEC.executor(), completable.timeout(Duration.ofMillis(1)).executor());
    }

    @Test
    public void testDoBeforeFinallyCompletable() {
        assertSame(EXEC.executor(), completable.doBeforeFinally(() -> { /* NOOP */ }).executor());
    }
}
