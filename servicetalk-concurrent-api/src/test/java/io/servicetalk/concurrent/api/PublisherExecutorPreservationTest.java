/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.internal.DefaultThreadFactory;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static java.lang.Thread.NORM_PRIORITY;
import static org.junit.Assert.assertSame;

public class PublisherExecutorPreservationTest {
    @ClassRule
    public static final ExecutorRule EXEC = new ExecutorRule(() ->
            newCachedThreadExecutor(new DefaultThreadFactory("test-", true, NORM_PRIORITY)));

    private Publisher<String> publisher;

    @Before
    public void setupPublisher() {
        publisher = Publisher.<String>empty().publishAndSubscribeOnOverride(EXEC.getExecutor());
    }

    @Test
    public void testPubToSingle() {
        assertSame(EXEC.getExecutor(), publisher.first().getExecutor());
    }

    @Test
    public void testPubToCompletable() {
        assertSame(EXEC.getExecutor(), publisher.ignoreElements().getExecutor());
    }

    @Test
    public void testReduceSingle() {
        assertSame(EXEC.getExecutor(), publisher.reduce(() -> 0, (n, s) -> n += s.length()).getExecutor());
    }
}
