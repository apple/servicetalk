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

import java.io.File;
import java.net.SocketAddress;

import static java.util.Objects.requireNonNull;

/**
 * An address which represents a socket belonging to the
 * <a href="https://man7.org/linux/man-pages/man7/unix.7.html">AF_UNIX socket family</a>.
 */
public final class DomainSocketAddress extends SocketAddress {
    private static final long serialVersionUID = 7522601114230727837L;

    private final String socketPath;

    /**
     * Create a new instance.
     * @param socketPath The file system path used to bind/connect for a UNIX domain socket.
     */
    public DomainSocketAddress(String socketPath) {
        this.socketPath = requireNonNull(socketPath);
    }

    /**
     * Create a new instance.
     * @param file Represents file system path used to bind/connect for a UNIX domain socket.
     */
    public DomainSocketAddress(File file) {
        this(file.getPath());
    }

    /**
     * The file system path used to bind/connect for a UNIX domain socket.
     * See <a href="https://man7.org/linux/man-pages/man7/unix.7.html">AF_UNIX socket family docs</a>.
     * @return The file system path used to bind/connect for a UNIX domain socket.
     */
    public String getPath() {
        return socketPath;
    }

    @Override
    public String toString() {
        return getPath();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof DomainSocketAddress && ((DomainSocketAddress) o).socketPath.equals(socketPath);
    }

    @Override
    public int hashCode() {
        return socketPath.hashCode();
    }
}
