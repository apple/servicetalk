/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.context.api.ContextMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import static io.servicetalk.concurrent.api.DefaultAsyncContextProvider.INSTANCE;

final class ContextAwareExecutorUtils {

    private ContextAwareExecutorUtils() {
        // no instances
    }

    static <X> Collection<? extends Callable<X>> wrap(Collection<? extends Callable<X>> tasks) {
        List<Callable<X>> wrappedTasks = new ArrayList<>(tasks.size());
        ContextMap contextMap = INSTANCE.context();
        for (Callable<X> task : tasks) {
            wrappedTasks.add(new ContextPreservingCallable<>(task, contextMap));
        }
        return wrappedTasks;
    }
}
