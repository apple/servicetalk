/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.api;

import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import javax.annotation.Nullable;

/**
 * A context for a connection.
 */
public interface ConnectionContext extends ConnectionInfo, ListenableAsyncCloseable {

    /**
     * Returns a reference to a parent {@link ConnectionContext} if any.
     * <p>
     * This method is useful when multiple virtual streams are multiplexed over a single connection to get access to the
     * actual {@link ConnectionContext} that represents network.
     *
     * @return a reference to a parent {@link ConnectionContext} if any. Otherwise, returns {@code null}.
     */
    @Nullable
    default ConnectionContext parent() {    // FIXME: 0.43 - consider removing default impl
        return null;
    }
}
