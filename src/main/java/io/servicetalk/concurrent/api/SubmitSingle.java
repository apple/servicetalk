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

import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

final class SubmitSingle<T> extends AbstractSubmitSingle<T> {
    private final Callable<? extends T> callable;

    SubmitSingle(final Callable<? extends T> callable,
                 final Executor runExecutor) {
        super(runExecutor);
        this.callable = requireNonNull(callable);
    }

    @Override
    Callable<? extends T> getCallable() {
        return callable;
    }
}
