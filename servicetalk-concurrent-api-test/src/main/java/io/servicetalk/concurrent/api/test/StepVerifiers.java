/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.test;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.test.CompletableFirstStep;
import io.servicetalk.concurrent.test.PublisherFirstStep;
import io.servicetalk.concurrent.test.SingleFirstStep;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;

/**
 * Create new test utilities to verify each step in the lifecycle of a {@link Publisher}, {@link Single},
 * and {@link Completable}. The steps are typically from the perspective of a {@link Subscriber}'s lifecycle.
 */
public final class StepVerifiers {
    private StepVerifiers() {
    }

    /**
     * Create a new {@link PublisherFirstStep}.
     * @param source The {@link Publisher} to verify.
     * @param <T> The type of {@link Publisher}.
     * @return A {@link PublisherFirstStep} that can be used to verify {@code source}'s signal emission(s).
     */
    public static <T> PublisherFirstStep<T> create(Publisher<T> source) {
        return io.servicetalk.concurrent.test.StepVerifiers.create(toSource(source));
    }

    /**
     * Create a new {@link SingleFirstStep}.
     * @param source The {@link Single} to verify.
     * @param <T> The type of {@link Single}.
     * @return A {@link SingleFirstStep} that can be used to verify {@code source}'s signal emission(s).
     */
    public static <T> SingleFirstStep<T> create(Single<T> source) {
        return io.servicetalk.concurrent.test.StepVerifiers.create(toSource(source));
    }

    /**
     * Create a new {@link CompletableFirstStep}.
     * @param source The {@link Completable} to verify.
     * @return A {@link CompletableFirstStep} that can be used to verify {@code source}'s signal emission(s).
     */
    public static CompletableFirstStep create(Completable source) {
        return io.servicetalk.concurrent.test.StepVerifiers.create(toSource(source));
    }
}
