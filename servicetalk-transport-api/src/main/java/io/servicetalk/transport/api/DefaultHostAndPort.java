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

import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.NetworkUtils.isValidIpV4Address;
import static io.servicetalk.utils.internal.NetworkUtils.isValidIpV6Address;
import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNull;

/**
 * A default immutable implementation of {@link HostAndPort}.
 */
final class DefaultHostAndPort implements HostAndPort {
    /**
     * {@code xxx.xxx.xxx.xxx:yyyyy}
     */
    private static final int MAX_IPV4_LEN = 21;
    /**
     * {@code [xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx]:yyyyy} = 47 chars w/out zone id
     */
    private static final int MAX_IPV6_LEN = 47 + 12 /* some limit for zone id length */;
    private static final String STR_IPV6 = "_ipv6_";
    private final String hostName;
    @Nullable
    private String toString;
    private final int port;

    /**
     * Create a new instance.
     * @param hostName the host name.
     * @param port the port.
     */
    DefaultHostAndPort(String hostName, int port) {
        if (isValidIpV6Address(requireNonNull(hostName))) { // Normalize ipv6 so equals/hashCode works as expected
            this.hostName = hostName.charAt(0) == '[' ?
                    compressIPv6(hostName, 1, hostName.length() - 1) : compressIPv6(hostName, 0, hostName.length());
            this.toString = STR_IPV6;
        } else {
            this.hostName = hostName;
        }
        this.port = port;
    }

    DefaultHostAndPort(String hostName, int port, boolean isIPv6) {
        this.hostName = requireNonNull(hostName);
        this.port = port;
        this.toString = isIPv6 ? STR_IPV6 : null;
    }

    /**
     * Parse IPv4 {@code xxx.xxx.xxx.xxx:yyyyy} and IPv6 {@code [xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx]:yyyyy} style
     * addresses.
     * @param ipPort An IPv4 {@code xxx.xxx.xxx.xxx:yyyyy} or IPv6
     * {@code [xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx]:yyyyy} addresses.
     * @param startIndex The index at which the address parsing starts.
     * @return A {@link HostAndPort} where the hostname is the IP address and the port is parsed from the string.
     */
    static HostAndPort parseFromIpPort(String ipPort, int startIndex) {
        String inetAddress;
        final boolean isv6;
        int i;
        if (ipPort.charAt(startIndex) == '[') { // check if ipv6
            if (ipPort.length() - startIndex > MAX_IPV6_LEN) {
                throw new IllegalArgumentException("Invalid IPv6 address: " + ipPort.substring(startIndex));
            }
            i = ipPort.indexOf(']');
            if (i <= startIndex) {
                throw new IllegalArgumentException("unable to find end ']' of IPv6 address: " +
                        ipPort.substring(startIndex));
            }
            inetAddress = ipPort.substring(startIndex + 1, i);
            ++i;
            isv6 = true;
            if (i >= ipPort.length()) {
                throw new IllegalArgumentException("no port found after ']' of IPv6 address: " +
                        ipPort.substring(startIndex));
            } else if (ipPort.charAt(i) != ':') {
                throw new IllegalArgumentException("':' expected after ']' for IPv6 address: " +
                        ipPort.substring(startIndex));
            }
        } else {
            if (ipPort.length() - startIndex > MAX_IPV4_LEN) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + ipPort.substring(startIndex));
            }
            i = ipPort.lastIndexOf(':');
            if (i < 0) {
                throw new IllegalArgumentException("no port found: " + ipPort.substring(startIndex));
            }
            inetAddress = ipPort.substring(startIndex, i);
            isv6 = false;
        }

        final int port;
        try {
            port = parseInt(ipPort.substring(i + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid port " + ipPort.substring(startIndex), e);
        }
        if (!isValidPort(port) || ipPort.charAt(i + 1) == '+') { // parseInt allows '+' but we don't want this
            throw new IllegalArgumentException("invalid port " + ipPort.substring(startIndex));
        }

        if (isv6) {
            inetAddress = compressIPv6(inetAddress, 0, inetAddress.length());
            if (!isValidIpV6Address(inetAddress)) {
                throw new IllegalArgumentException("Invalid IPv6 address: " + inetAddress);
            }
            return new DefaultHostAndPort(inetAddress, port, true);
        }
        if (!isValidIpV4Address(inetAddress)) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + inetAddress);
        }
        return new DefaultHostAndPort(inetAddress, port, false);
    }

    @Override
    public String hostName() {
        return hostName;
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public String toString() {
        String str = toString;
        if (str == null) {
            toString = str = hostName + ':' + port;
        } else if (str == STR_IPV6) {
            toString = str = '[' + hostName + "]:" + port;
        }
        return str;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHostAndPort)) {
            return false;
        }
        DefaultHostAndPort rhs = (DefaultHostAndPort) o;
        return port == rhs.port() && hostName.equalsIgnoreCase(rhs.hostName());
    }

    @Override
    public int hashCode() {
        return 31 * (31 + port) + hostName.hashCode();
    }

    private static boolean isValidPort(int port) {
        return port >= 0 && port <= 65535;
    }

    private static String compressIPv6(String rawIp, int start, int end) {
        if (end - start <= 0) {
            throw new IllegalArgumentException("Empty IPv6 address");
        }
        // https://datatracker.ietf.org/doc/html/rfc5952#section-2
        // JDK doesn't do IPv6 compression, or remove leading 0s. This may lead to inconsistent String representation
        // which will yield different hash-codes and equals comparisons to fail when it shouldn't.
        int longestZerosCount = 0;
        int longestZerosBegin = -1;
        int longestZerosEnd = -1;
        int zerosCount = 0;
        int zerosBegin = rawIp.charAt(start) != '0' ? -1 : 0;
        int zerosEnd = -1;
        boolean isCompressed = false;
        char prevChar = '\0';
        StringBuilder compressedIPv6Builder = new StringBuilder(end - start);
        for (int i = start; i < end; ++i) {
            final char c = rawIp.charAt(i);
            switch (c) {
                case '0':
                    if (zerosBegin < 0 || i == end - 1) {
                        compressedIPv6Builder.append('0');
                    }
                    break;
                case ':':
                    if (prevChar == ':') {
                        isCompressed = true;
                        compressedIPv6Builder.append(':');
                    } else if (zerosBegin >= 0) {
                        ++zerosCount;
                        compressedIPv6Builder.append("0:");
                        zerosEnd = compressedIPv6Builder.length();
                    } else {
                        compressedIPv6Builder.append(':');
                        zerosBegin = compressedIPv6Builder.length();
                    }
                    break;
                default:
                    // https://datatracker.ietf.org/doc/html/rfc5952#section-4.2.3
                    // if there is a tie in the longest length, we must choose the first to compress.
                    if (zerosEnd > 0 && zerosCount > longestZerosCount) {
                        longestZerosCount = zerosCount;
                        longestZerosBegin = zerosBegin;
                        longestZerosEnd = zerosEnd;
                    }
                    zerosBegin = zerosEnd = -1;
                    zerosCount = 0;
                    compressedIPv6Builder.append(c);
                    break;
            }
            prevChar = c;
        }
        // https://datatracker.ietf.org/doc/html/rfc5952#section-4.2.2
        // The symbol "::" MUST NOT be used to shorten just one 16-bit 0 field.
        if (!isCompressed && longestZerosBegin >= 0 && longestZerosCount > 1) {
            compressedIPv6Builder.replace(longestZerosBegin, longestZerosEnd, longestZerosBegin == 0 ? "::" : ":");
        }
        return compressedIPv6Builder.toString();
    }
}
