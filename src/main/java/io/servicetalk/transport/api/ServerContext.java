/**
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
package io.servicetalk.transport.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import java.net.SocketAddress;

/**
 * Context for servers.
 */
public interface ServerContext extends ListenableAsyncCloseable {

    /**
     * Listen address for the server associated with this context.
     *
     * @return Address which the associated server is listening at.
     */
    SocketAddress getListenAddress();

    /**
     * Shutdown the server associated with this context.
     *
     * @return {@link Completable} which terminates successfully when the close was successful.
     */
    @Override
    Completable closeAsync();

    /**
     * Listen to shutdown of the server associated with this context.
     *
     * @return {@link Completable} which terminates successfully when the server is successfully shutdown.
     */
    @Override
    Completable onClose();
}
