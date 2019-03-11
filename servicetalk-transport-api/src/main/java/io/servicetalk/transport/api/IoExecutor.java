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
package io.servicetalk.transport.api;

import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import java.util.concurrent.Executor;

/**
 * {@link Executor} that handles IO.
 */
public interface IoExecutor extends ListenableAsyncCloseable {

    /**
     * Determine if <a href="https://en.wikipedia.org/wiki/Unix_domain_socket">Unix Domain Sockets</a> are supported.
     *
     * @return {@code true} if <a href="https://en.wikipedia.org/wiki/Unix_domain_socket">Unix Domain Sockets</a> are
     * supported.
     */
    boolean isUnixDomainSocketSupported();

    /**
     * Determine if fd addresses are supported.
     *
     * @return {@code true} if supported
     */
    boolean isFileDescriptorSocketAddressSupported();
}
