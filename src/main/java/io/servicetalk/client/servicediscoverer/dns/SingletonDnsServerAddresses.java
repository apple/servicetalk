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
package io.servicetalk.client.servicediscoverer.dns;

import java.net.InetSocketAddress;

import static java.util.Objects.requireNonNull;

/**
 * A {@link DnsServerAddressStream} which always returns the same {@link InetSocketAddress}.
 */
public final class SingletonDnsServerAddresses implements DnsServerAddressStream {
    private final InetSocketAddress address;

    /**
     * Create a new instance.
     * @param address the address to return in {@link #next()}.
     */
    public SingletonDnsServerAddresses(InetSocketAddress address) {
        this.address = requireNonNull(address);
    }

    @Override
    public InetSocketAddress next() {
        return address;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public DnsServerAddressStream duplicate() {
        return this;
    }

    @Override
    public String toString() {
        return "singleton(" + address + ")";
    }
}
