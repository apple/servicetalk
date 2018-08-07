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

import java.net.SocketAddress;

/**
 * Special {@link SocketAddress} that allows to wrap an already existing filedescriptor.
 */
public final class FileDescriptorSocketAddress extends SocketAddress {
    private static final long serialVersionUID = -527549734215814232L;

    private final int fd;

    /**
     * Constructs a new instance which wraps an existing filedescriptor.
     *
     * @param fd the filedescriptor to wrap.
     */
    public FileDescriptorSocketAddress(int fd) {
        this.fd = fd;
    }

    /**
     * Return the filedescriptor value.
     *
     * @return the filedescriptor value.
     */
    public int getValue() {
        return fd;
    }

    @Override
    public String toString() {
        return "FileDescriptorSocketAddress{" +
                "fd=" + fd +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        return this == o || !(o == null || getClass() != o.getClass()) && fd == ((FileDescriptorSocketAddress) o).fd;
    }

    @Override
    public int hashCode() {
        return fd;
    }
}
