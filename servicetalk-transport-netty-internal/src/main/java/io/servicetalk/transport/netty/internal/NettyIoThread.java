/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.transport.api.IoThreadFactory.IoThread;

import io.netty.util.concurrent.FastThreadLocalThread;

import javax.annotation.Nullable;

final class NettyIoThread extends FastThreadLocalThread implements IoThread {
    @Nullable
    private AsyncContextMap asyncContextMap;

    NettyIoThread(@Nullable ThreadGroup group, Runnable target, String name) {
        super(group, target, name);
    }

    @Override
    public void asyncContextMap(@Nullable final AsyncContextMap asyncContextMap) {
        this.asyncContextMap = asyncContextMap;
    }

    @Nullable
    @Override
    public AsyncContextMap asyncContextMap() {
        return asyncContextMap;
    }
}
