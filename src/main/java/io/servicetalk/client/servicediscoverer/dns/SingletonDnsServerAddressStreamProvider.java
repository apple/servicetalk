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
package io.servicetalk.client.servicediscoverer.dns;

import static java.util.Objects.requireNonNull;

/**
 * A {@link DnsServerAddressStreamProvider} which always returns the same {@link DnsServerAddressStream}.
 */
public final class SingletonDnsServerAddressStreamProvider implements DnsServerAddressStreamProvider {
    private final DnsServerAddressStream stream;

    /**
     * Create a new instance.
     * @param stream The singelton to return from {@link #nameServerAddressStream(String)}.
     */
    public SingletonDnsServerAddressStreamProvider(DnsServerAddressStream stream) {
        this.stream = requireNonNull(stream);
    }

    @Override
    public DnsServerAddressStream nameServerAddressStream(String hostname) {
        return stream;
    }
}
