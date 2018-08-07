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

/**
 * Set of utilities to create {@link Executor}s that are merged with other {@link Executor}s.
 */
final class MergedExecutors {

    private MergedExecutors() {
        // No instances.
    }

    /**
     * For scenarios where we need an {@link Executor} for selectively offloading some signals, we need to merge
     * {@link Executor}s so that we can use an appropriate {@link Executor} for offloading specific signals. This method
     * does such merging when the {@code publishOnExecutor} {@link Executor} only needs to offload publish signals.
     *
     * @param fallback {@link Executor} that we need to merge {@code publishOnExecutor} with.
     * @param publishOnExecutor {@link Executor} that is to be merged with {@code fallback}.
     * @return {@link Executor} which will use {@code fallback} for all signals that are not offloaded by
     * {@code publishOnExecutor}.
     */
    static Executor mergeAndOffloadPublish(final Executor fallback, final Executor publishOnExecutor) {
        return new MergedOffloadPublishExecutor(publishOnExecutor, fallback);
    }

    /**
     * For scenarios where we need an {@link Executor} for selectively offloading some signals, we need to merge
     * {@link Executor}s so that we can use an appropriate {@link Executor} for offloading specific signals. This method
     * does such merging when the {@code subscribeOnExecutor} {@link Executor} only needs to offload subscribe signals.
     *
     * @param fallback {@link Executor} that we need to merge {@code subscribeOnExecutor} with.
     * @param subscribeOnExecutor {@link Executor} that is to be merged with {@code fallback}.
     * @return {@link Executor} which will use {@code fallback} for all signals that are not offloaded by
     * {@code subscribeOnExecutor}.
     */
    static Executor mergeAndOffloadSubscribe(final Executor fallback, final Executor subscribeOnExecutor) {
        return new MergedOffloadSubscribeExecutor(subscribeOnExecutor, fallback);
    }
}
