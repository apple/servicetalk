/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.dns.discovery.netty;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link DnsServerAddressStreamProvider} which is backed by a sequential list of DNS servers.
 */
public final class SequentialDnsServerAddressStreamProvider implements DnsServerAddressStreamProvider {
    private final InetSocketAddress[] addresses;

    /**
     * Create a new instance.
     * @param addresses The addresses which will be be returned in sequential order.
     */
    public SequentialDnsServerAddressStreamProvider(final InetSocketAddress... addresses) {
        if (addresses.length == 0) {
            throw new IllegalArgumentException("addresses must have >0 elements");
        }
        this.addresses = addresses.clone();
    }

    /**
     * Create a new instance.
     * @param addresses The addresses which will be be returned in sequential order.
     */
    public SequentialDnsServerAddressStreamProvider(final List<InetSocketAddress> addresses) {
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("addresses must have >0 elements");
        }
        this.addresses = addresses.toArray(new InetSocketAddress[0]);
    }

    @Override
    public DnsServerAddressStream nameServerAddressStream(final String hostname) {
        return new SequentialDnsServerAddressStream(addresses);
    }

    private static final class SequentialDnsServerAddressStream implements DnsServerAddressStream {
        private final InetSocketAddress[] addresses;
        private int index;

        SequentialDnsServerAddressStream(InetSocketAddress[] addresses) {
            this(addresses, 0);
        }

        SequentialDnsServerAddressStream(InetSocketAddress[] addresses, int index) {
            this.addresses = addresses;
            this.index = index;
        }

        @Override
        public InetSocketAddress next() {
            InetSocketAddress next = addresses[index++];
            if (index == addresses.length) {
                index = 0;
            }
            return next;
        }

        @Override
        public int size() {
            return addresses.length;
        }

        @Override
        public DnsServerAddressStream duplicate() {
            return new SequentialDnsServerAddressStream(addresses, index);
        }

        @Override
        public String toString() {
            return "index: " + index + " addresses: " + Arrays.toString(addresses);
        }
    }
}
