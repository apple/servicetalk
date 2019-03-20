/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.SingleSource;

/**
 * Utility class for {@link SingleSource.Processor} and {@link CompletableSource.Processor}.
 */
public final class Processors {
    private Processors() {
        // no instances
    }

    /**
     * Create a new {@link CompletableSource.Processor} that allows for multiple
     * {@link CompletableSource.Subscriber#subscribe(CompletableSource.Subscriber) subscribes}.
     *
     * @return a new {@link CompletableSource.Processor} that allows for multiple
     * {@link CompletableSource.Subscriber#subscribe(CompletableSource.Subscriber) subscribes}.
     */
    public static CompletableSource.Processor newCompletableProcessor() {
        return new CompletableProcessor();
    }

    /**
     * Create a new {@link SingleSource.Processor} that allows for multiple
     * {@link SingleSource.Subscriber#subscribe(SingleSource.Subscriber) subscribes}.
     *
     * @param <T> The {@link SingleSource} type and {@link SingleSource.Subscriber} type of the
     * {@link SingleSource.Processor}.
     * @return a new {@link SingleSource.Processor} that allows for multiple
     * {@link SingleSource.Subscriber#subscribe(SingleSource.Subscriber) subscribes}.
     */
    public static <T> SingleSource.Processor<T, T> newSingleProcessor() {
        return new SingleProcessor<>();
    }
}
