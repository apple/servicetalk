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
package io.servicetalk.transport.netty.internal;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

class CloseState {

    private static final int OPEN = 0;
    private static final int GRACEFULLY_CLOSING = 1;
    private static final int CLOSING = 2;

    private static final AtomicIntegerFieldUpdater<CloseState> stateUpdater =
            AtomicIntegerFieldUpdater.newUpdater(CloseState.class, "state");

    @SuppressWarnings("unused")
    private volatile int state;

    boolean isClosing() {
        return state != OPEN;
    }

    final boolean tryCloseAsync() {
        return stateUpdater.getAndSet(this, CLOSING) != CLOSING;
    }

    final boolean tryCloseAsyncGracefully() {
        return stateUpdater.compareAndSet(CloseState.this, OPEN, GRACEFULLY_CLOSING);
    }
}
