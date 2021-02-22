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

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.CompletableFuture;

import static io.servicetalk.concurrent.api.Single.fromStage;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SingleToCompletableFutureToCompletionStageWrappingTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Test
    public void wrappedTerminationTerminates() throws Exception {
        CompletableFuture<String> composed = completedFuture("Hello")
                .thenCompose(s -> succeeded("Hello-Nested").toCompletionStage().toCompletableFuture());
        assertThat("Unexpected result.", composed.get(), is("Hello-Nested"));
    }

    @Test
    public void deferredWrappedTerminationTerminates() throws Exception {
        CompletableFuture<String> cf = new CompletableFuture<>();
        CompletableFuture<String> composed = completedFuture("Hello")
                .thenCompose(s -> fromStage(cf).toCompletionStage().toCompletableFuture());

        cf.complete("Hello-Nested");
        assertThat("Unexpected result.", composed.get(), is("Hello-Nested"));
    }

    @Test
    public void wrappedAndApplyTerminationTerminates() throws Exception {
        CompletableFuture<String> composed = completedFuture("Hello")
                .thenCompose(s -> succeeded("Hello-Nested").toCompletionStage().toCompletableFuture()
                        .thenApply(s1 -> s1));
        assertThat("Unexpected result.", composed.get(), is("Hello-Nested"));
    }

    @Test
    public void deferredWrappedAndApplyTerminationTerminates() throws Exception {
        CompletableFuture<String> cf = new CompletableFuture<>();
        CompletableFuture<String> composed = completedFuture("Hello")
                .thenCompose(s -> fromStage(cf).toCompletionStage().toCompletableFuture().thenApply(str -> str));

        cf.complete("Hello-Nested");
        assertThat("Unexpected result.", composed.get(), is("Hello-Nested"));
    }
}
