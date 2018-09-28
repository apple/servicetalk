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
package io.servicetalk.http.utils;

import io.servicetalk.buffer.api.Buffer;

import io.netty.util.NetUtil;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

final class LoggingUtils {
    private LoggingUtils() {
        // no instances.
    }

    static int estimateSize(Object o) {
        if (o instanceof Buffer) {
            return ((Buffer) o).getReadableBytes();
        }
        return 0;
    }

    static String formatCanonicalAddress(SocketAddress address) {
        // Try to return the "raw" address (without resolved host name, etc)
        if (address instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
            InetAddress inetAddress = inetSocketAddress.getAddress();
            // inetAddress could be null if SocketAddress is in an unresolved form
            if (inetAddress == null) {
                return address.toString();
            } else if (inetAddress instanceof Inet6Address) {
                return '[' + NetUtil.toAddressString(inetAddress) + "]:" + inetSocketAddress.getPort();
            } else {
                return NetUtil.toAddressString(inetAddress) + ':' + inetSocketAddress.getPort();
            }
        }
        return address != null ? address.toString() : "null";
    }
}
