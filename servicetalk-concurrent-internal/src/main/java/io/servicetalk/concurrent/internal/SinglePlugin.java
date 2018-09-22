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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.Single;
import io.servicetalk.concurrent.Single.Subscriber;

import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Extensibility point provided by {@link Single} for visibility into asynchronous events.
 */
public interface SinglePlugin {
    /**
     * Extension point for {@link Single#subscribe(Subscriber)}.
     * @param subscriber The {@link Subscriber}.
     * @param offloader The {@link SignalOffloader}.
     * @param handleSubscribe The resulting {@link Single#subscribe(Subscriber)} method.
     */
    void handleSubscribe(Subscriber<?> subscriber, SignalOffloader offloader,
                         BiConsumer<? super Subscriber, SignalOffloader> handleSubscribe);

    /**
     * Extension point for {@link Single} conversion to {@link CompletionStage}.
     * @param single The {@link Single} being converted.
     * @param executor The {@link Executor} used for the conversion.
     * @param completionStageFactory The
     * @param <X> The type of {@link Single}.
     * @return The {@link CompletionStage} post plugin processing.
     */
    <X> CompletionStage<X> toCompletionStage(Single<X> single, Executor executor,
                                             BiFunction<Single<X>, Executor, CompletionStage<X>>
                                                           completionStageFactory);

    /**
     * Extension point for {@link CompletionStage} to {@link Single} conversion.
     * @param completionStage The {@link Single} being converted.
     * @param <X> The type of {@link Single}.
     * @return The {@link CompletionStage} post plugin processing.
     */
    <X> CompletionStage<X> fromCompletionStage(CompletionStage<X> completionStage);
}
