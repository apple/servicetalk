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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.api.test.TimeSources.nanoTimeNormalized;

/**
 * Factory for the first steps that lead to {@link StepVerifier} test utility to verify each step in the lifecycle of
 * asynchronous sources {@link Publisher}, {@link Single}, and {@link Completable}.
 * <p>
 * The steps are typically from the perspective of a {@link Subscriber}'s lifecycle.
 */
public final class Verifiers {
    private Verifiers() {
    }

    /**
     * Create a new {@link PublisherFirstStep}.
     *
     * @param source The {@link Publisher} to verify.
     * @param <T> The type of {@link Publisher}.
     * @return A {@link PublisherFirstStep} that can be used to verify {@code source}'s signal emission(s).
     */
    public static <T> PublisherFirstStep<T> stepVerifier(Publisher<T> source) {
        return stepVerifierForSource(toSource(source));
    }

    /**
     * Create a new {@link PublisherFirstStep}.
     *
     * @param source The {@link PublisherSource} to verify.
     * @param <T> The type of {@link PublisherSource}.
     * @return A {@link PublisherFirstStep} that can be used to verify {@code source}'s signal emission(s).
     */
    public static <T> PublisherFirstStep<T> stepVerifierForSource(PublisherSource<T> source) {
        return new InlinePublisherFirstStep<>(source, nanoTimeNormalized());
    }

    /**
     * Create a new {@link SingleFirstStep}.
     *
     * @param source The {@link Single} to verify.
     * @param <T> The type of {@link Single}.
     * @return A {@link SingleFirstStep} that can be used to verify {@code source}'s signal emission(s).
     */
    public static <T> SingleFirstStep<T> stepVerifier(Single<T> source) {
        return stepVerifierForSource(toSource(source));
    }

    /**
     * Create a new {@link SingleFirstStep}.
     *
     * @param source The {@link SingleSource} to verify.
     * @param <T> The type of {@link SingleSource}.
     * @return A {@link SingleFirstStep} that can be used to verify {@code source}'s signal emission(s).
     */
    public static <T> SingleFirstStep<T> stepVerifierForSource(SingleSource<T> source) {
        return new InlineSingleFirstStep<>(source, nanoTimeNormalized());
    }

    /**
     * Create a new {@link CompletableFirstStep}.
     *
     * @param source The {@link Completable} to verify.
     * @return A {@link CompletableFirstStep} that can be used to verify {@code source}'s signal emission(s).
     */
    public static CompletableFirstStep stepVerifier(Completable source) {
        return stepVerifierForSource(toSource(source));
    }

    /**
     * Create a new {@link CompletableFirstStep}.
     *
     * @param source The {@link CompletableSource} to verify.
     * @return A {@link CompletableFirstStep} that can be used to verify {@code source}'s signal emission(s).
     */
    public static CompletableFirstStep stepVerifierForSource(CompletableSource source) {
        return new InlineCompletableFirstStep(source, nanoTimeNormalized());
    }
}
