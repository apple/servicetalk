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

import static org.junit.Assert.assertSame;

public class PublisherExecutorPreservationTest {
    @ClassRule
    public static final ExecutorRule EXEC = ExecutorRule.withNamePrefix("test-");

    private Publisher<String> publisher;

    @Before
    public void setupPublisher() {
        publisher = Publisher.<String>empty().publishAndSubscribeOnOverride(EXEC.executor());
    }

    @Test
    public void testPubToSingle() {
        assertSame(EXEC.executor(), publisher.first().executor());
    }

    @Test
    public void testPubToSingleOrError() {
        assertSame(EXEC.executor(), publisher.toSingleOrError().executor());
    }

    @Test
    public void testPubToCompletable() {
        assertSame(EXEC.executor(), publisher.ignoreElements().executor());
    }

    @Test
    public void testReduceSingle() {
        assertSame(EXEC.executor(), publisher.reduce(() -> 0, (n, s) -> n += s.length()).executor());
    }
}
