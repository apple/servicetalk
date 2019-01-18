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

import static java.lang.ThreadLocal.withInitial;
import static java.util.Objects.requireNonNull;

final class AsyncContextMapThreadLocal {
    private static final ThreadLocal<AsyncContextMap> contextLocal = withInitial(
            AsyncContextMapThreadLocal::newContextMap);

    static AsyncContextMap newContextMap() {
        return new CopyOnWriteAsyncContextMap();
    }

    AsyncContextMap get() {
        Thread t = Thread.currentThread();
        if (t instanceof AsyncContextMapHolder) {
            AsyncContextMapHolder asyncContextMapHolder = (AsyncContextMapHolder) t;
            AsyncContextMap map = asyncContextMapHolder.asyncContextMap();
            if (map == null) {
                map = newContextMap();
                asyncContextMapHolder.asyncContextMap(map);
            }
            return map;
        }
        return contextLocal.get();
    }

    void set(AsyncContextMap asyncContextMap) {
        requireNonNull(asyncContextMap);
        Thread t = Thread.currentThread();
        if (t instanceof AsyncContextMapHolder) {
            ((AsyncContextMapHolder) t).asyncContextMap(asyncContextMap);
        } else {
            contextLocal.set(asyncContextMap);
        }
    }

    void remove() {
        Thread t = Thread.currentThread();
        if (t instanceof AsyncContextMapHolder) {
            ((AsyncContextMapHolder) t).asyncContextMap(null);
        } else {
            contextLocal.remove();
        }
    }
}
