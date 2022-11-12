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
package io.servicetalk.concurrent.api.internal;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlockingUtilsTest {

    @Test
    void testSingle() {
        test(t -> BlockingUtils.blockingInvocation(Single.failed(t)));
    }

    @Test
    void testCompletable() {
        test(t -> BlockingUtils.blockingInvocation(Completable.failed(t)));
    }

    private static void test(BlockingTask blockingTask) {
        DeliberateException expected = new DeliberateException();
        DeliberateException e = assertThrows(DeliberateException.class, () -> blockingTask.run(expected));
        assertThat(e, is(sameInstance(expected)));
        assertThat(e.getSuppressed(), is(arrayWithSize(1)));
        Throwable ee = e.getSuppressed()[0];
        assertThat(ee, is(instanceOf(ExecutionException.class)));
        assertThat(ee.getStackTrace(), is(not(emptyArray())));
    }

    @FunctionalInterface
    private interface BlockingTask {
        void run(Throwable t) throws Exception;
    }
}
