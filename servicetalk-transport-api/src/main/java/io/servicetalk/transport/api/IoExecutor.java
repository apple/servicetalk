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

import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import java.util.function.BooleanSupplier;

/**
 * {@link Executor} that handles IO.
 */
public interface IoExecutor extends Executor, ListenableAsyncCloseable {

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

    /**
     * Determine if threads used by this {@link IoExecutor} are marked with {@link IoThreadFactory.IoThread} interface.
     *
     * @return {@code true} if supported
     * @see IoThreadFactory.IoThread
     */
    boolean isIoThreadSupported();

    /**
     * Returns a boolean supplier, if this IoExecutor supports {@link IoThreadFactory.IoThread} markers, that
     * conditionally recommends offloading if the current thread is an IO thread. If this IoExecutor does not support
     * IoThread marker interface then the boolean supplier will always return {@code true}.
     *
     * @return a Boolean supplier to recommend offloading appropriately based upon IoExecutor configuration.
     */
    default BooleanSupplier shouldOffloadSupplier() {
        return isIoThreadSupported() ?
                IoThreadFactory.IoThread::currentThreadIsIoThread : // offload if on IO thread
                Boolean.TRUE::booleanValue; // unconditional
    }
}
