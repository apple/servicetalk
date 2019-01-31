/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.transport.api.HostAndPort;

import static io.netty.util.NetUtil.isValidIpV6Address;

/**
 * A utility class to work with addresses.
 */
public final class AddressUtils {

    private AddressUtils() {
        // No instances
    }

    /**
     * Returns a {code HOST} header value based of the information in {@link HostAndPort}.
     *
     * @param hostAndPort to convert to the {@code HOST} header
     * @return a {@code HOST} header value
     */
    public static String hostHeader(final HostAndPort hostAndPort) {
        return isValidIpV6Address(hostAndPort.getHostName()) ?
                "[" + hostAndPort.getHostName() + "]:" + hostAndPort.getPort() : hostAndPort.toString();
    }
}
