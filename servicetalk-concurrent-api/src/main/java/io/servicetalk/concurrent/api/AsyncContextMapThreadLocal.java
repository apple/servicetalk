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

final class AsyncContextMapThreadLocal {
    static final ThreadLocal<AsyncContextMap> contextThreadLocal =
            withInitial(AsyncContextMapThreadLocal::newContextMap);

    private static AsyncContextMap newContextMap() {
        return new CopyOnWriteAsyncContextMap();
    }

    AsyncContextMap get() {
        final Thread t = Thread.currentThread();
        if (t instanceof AsyncContextMapHolder) {
            final AsyncContextMapHolder asyncContextMapHolder = (AsyncContextMapHolder) t;
            AsyncContextMap map = asyncContextMapHolder.asyncContextMap();
            if (map == null) {
                map = newContextMap();
                asyncContextMapHolder.asyncContextMap(map);
            }
            return map;
        } else {
            return contextThreadLocal.get();
        }
    }

    void set(AsyncContextMap asyncContextMap) {
        final Thread t = Thread.currentThread();
        if (t instanceof AsyncContextMapHolder) {
            ((AsyncContextMapHolder) t).asyncContextMap(asyncContextMap);
        } else {
            contextThreadLocal.set(asyncContextMap);
        }
    }

    void remove() {
        final Thread t = Thread.currentThread();
        if (t instanceof AsyncContextMapHolder) {
            ((AsyncContextMapHolder) t).asyncContextMap(null);
        } else {
            contextThreadLocal.remove();
        }
    }
}
