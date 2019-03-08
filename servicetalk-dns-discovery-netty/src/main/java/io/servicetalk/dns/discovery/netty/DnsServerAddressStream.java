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
package io.servicetalk.dns.discovery.netty;

import java.net.InetSocketAddress;

/**
 * An infinite stream of DNS server addresses.
 */
public interface DnsServerAddressStream {
    /**
     * Retrieves the next DNS server address from the stream.
     * @return the next DNS server address from the stream.
     */
    InetSocketAddress next();

    /**
     * Get the number of times {@link #next()} will return a distinct element before repeating or terminating.
     * @return the number of times {@link #next()} will return a distinct element before repeating or terminating.
     */
    int size();

    /**
     * Duplicate this object. The result of this should be able to be independently iterated over via {@link #next()}.
     * <p>
     * Note that {@link Object#clone()} isn't used because it may make sense for some implementations to have the
     * following relationship {@code x.duplicate() == x}.
     * @return A duplicate of this object.
     */
    DnsServerAddressStream duplicate();
}
