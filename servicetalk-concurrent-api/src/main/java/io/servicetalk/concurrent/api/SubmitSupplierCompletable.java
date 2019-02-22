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

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

final class SubmitSupplierCompletable extends AbstractSubmitCompletable {
    private final Supplier<Runnable> runnableSupplier;

    SubmitSupplierCompletable(final Supplier<Runnable> runnableSupplier,
                              final Executor runExecutor) {
        super(runExecutor);
        this.runnableSupplier = requireNonNull(runnableSupplier);
    }

    @Override
    Runnable runnable() {
        return runnableSupplier.get();
    }
}
