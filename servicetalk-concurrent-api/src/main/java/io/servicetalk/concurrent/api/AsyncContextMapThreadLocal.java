/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.context.api.ContextMapHolder;

import static java.lang.ThreadLocal.withInitial;

final class AsyncContextMapThreadLocal {
    static final ThreadLocal<ContextMap> CONTEXT_THREAD_LOCAL = withInitial(AsyncContextMapThreadLocal::newContextMap);

    private static ContextMap newContextMap() {
        return new CopyOnWriteAsyncContextMap();
    }

    ContextMap get() {
        final Thread t = Thread.currentThread();
        if (t instanceof ContextMapHolder) {
            final ContextMapHolder contextMapHolder = (ContextMapHolder) t;
            ContextMap map = contextMapHolder.context();
            if (map == null) {
                map = newContextMap();
                contextMapHolder.context(map);
            }
            return map;
        } else {
            return CONTEXT_THREAD_LOCAL.get();
        }
    }
}
