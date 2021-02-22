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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.Single;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Single.fromFuture;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

public class FutureToSingleTest extends AbstractFutureToSingleTest {
    @Override
    Single<String> from(final CompletableFuture<String> future) {
        return fromFuture(future);
    }

    @Test
    public void failure() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        Single<String> single = from(future);
        jdkExecutor.execute(() -> future.completeExceptionally(DELIBERATE_EXCEPTION));
        try {
            single.toFuture().get();
            fail("Single expected to fail.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat("Unexpected cause.", cause, instanceOf(ExecutionException.class));
            Throwable nestedCause = cause.getCause();
            assertThat("Unexpected nested cause.", nestedCause, is(DELIBERATE_EXCEPTION));
        }
    }
}
