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
package io.servicetalk.concurrent.test;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.SingleSource;

/**
 * Create new test utilities to verify each step in the lifecycle of a {@link PublisherSource}, {@link SingleSource},
 * and {@link CompletableSource}. The steps are typically from the perspective of a {@link Subscriber}'s lifecycle.
 */
public final class StepVerifiers {
    private StepVerifiers() {
    }

    /**
     * Create a new {@link PublisherFirstStep}.
     * @param source The {@link PublisherSource} to verify.
     * @param <T> The type of {@link PublisherSource}.
     * @return A {@link PublisherFirstStep} that can be used to verify {@code source}'s signal emission(s).
     */
    public static <T> PublisherFirstStep<T> create(PublisherSource<T> source) {
        return new DefaultPublisherFirstStep<>(source);
    }

    /**
     * Create a new {@link SingleFirstStep}.
     * @param source The {@link SingleSource} to verify.
     * @param <T> The type of {@link SingleSource}.
     * @return A {@link SingleFirstStep} that can be used to verify {@code source}'s signal emission(s).
     */
    public static <T> SingleFirstStep<T> create(SingleSource<T> source) {
        return new DefaultSingleFirstStep<>(source);
    }

    /**
     * Create a new {@link CompletableFirstStep}.
     * @param source The {@link CompletableSource} to verify.
     * @return A {@link CompletableFirstStep} that can be used to verify {@code source}'s signal emission(s).
     */
    public static CompletableFirstStep create(CompletableSource source) {
        return new DefaultCompletableFirstStep(source);
    }
}
