/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.transport.api.ConnectionContext;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpHeaderNames.FORWARDED;
import static io.servicetalk.http.api.HttpHeaderNames.X_FORWARDED_FOR;

/**
 * Client helper functions to extract actual client IP from headers when request goes
 * through a forwarding proxy/proxies.
 * Worth noting that these headers can be altered by the originator, so additional validation may be required
 * to validate the origin.
 */
public final class ClientIpUtils {

    private ClientIpUtils() {
        // no instances
    }

    /**
     * Extracts the client IP address from request headers or connection context.
     *
     * @param headers an {@link HttpHeaders} of the request
     * @param ctx a {@link ConnectionContext} of the request
     * @param fallbackToSocketAddress whether to fallback to the remote address in {@link ConnectionContext} if no
     * client IP header is present
     * @return inferred client IP address as {@link CharSequence}
     */
    @Nullable
    static CharSequence extractClientIp(final HttpHeaders headers,
                                        final ConnectionContext ctx,
                                        final boolean fallbackToSocketAddress) {

        final CharSequence ip = parseXForwardedFor(headers.get(X_FORWARDED_FOR));
        if (ip != null) {
            return ip;
        }

        final CharSequence ipFromForwarded = parseForwarded(headers.get(FORWARDED));
        if (ipFromForwarded != null) {
            return ipFromForwarded;
        }

        return fallbackToSocketAddress ? toHostString(ctx.remoteAddress()) : null;
    }

    /**
     * Retrieves the first IP in a comma+space separated list of IPs, or the value as-is if it contains no comma.
     * <p>
     * This is useful for cases when the IP is retrieved from the {@link HttpHeaderNames#X_FORWARDED_FOR} which value is
     * of the form: {@code client, proxy1, proxy2, ...}
     *
     * @param value a value of the header, which could contain client IP
     * @return extracted client IP address or {@code null}
     */
    @Nullable
    private static CharSequence parseXForwardedFor(@Nullable final CharSequence value) {
        if (value == null || value.length() == 0) {
            return null;
        }

        final int idx = indexOf(value, ',');
        final CharSequence ip = idx < 0 ? value : value.subSequence(0, idx);
        return ip.length() == 0 ? null : ip;
    }

    /**
     * Parses a value of {@link HttpHeaderNames#FORWARDED} header to extract legal IP address. For more information, see
     * <a href="https://tools.ietf.org/html/rfc7239">RFC 7239: Forwarded HTTP Extension</a>.
     *
     * @param value a value of {@link HttpHeaderNames#FORWARDED} header
     * @return extracted client IP address or {@code null} if not presented
     */
    @Nullable
    private static CharSequence parseForwarded(@Nullable final CharSequence value) {
        if (value == null || value.length() < 11) {
            // 11 is the shortest length of "Forwarded" header, which may contain a valid origin IP address:
            // 'for="[::1]"' or 'for=0.0.0.0'
            return null;
        }

        final String str = value.toString().toLowerCase();  // required for case-insensitive indexOf
        int beginIdx = str.indexOf("for=");
        if (beginIdx < 0) {
            return null;
        }

        beginIdx += 4;
        if (str.length() - beginIdx < 7) {
            // not enough data to extract the shortest valid origin IP address: "[::1]" or 0.0.0.0
            return null;
        }

        int endIdx = computeForParamEndIdx(str, beginIdx);
        if (endIdx - beginIdx < 7) {
            // not enough data to extract the shortest valid origin IP address: "[::1]" or 0.0.0.0
            return null;
        }

        if (str.charAt(beginIdx) == '"') {
            // unwrap quoted value
            beginIdx++;
            endIdx--;

            if (endIdx - beginIdx < 5) {
                // not enough data to extract the shortest valid origin IP address: [::1]
                return null;
            }
        }

        if (str.charAt(beginIdx) == '_') {
            // obfuscated identifier, not an IP address
            return null;
        }

        if (str.charAt(beginIdx) == '[') {
            // unwrap IPv6 address
            endIdx = str.indexOf(']', beginIdx + 1);
            if (endIdx - beginIdx < 4) {
                // illegal origin IPv6 address, the value is shorter than ::1
                return null;
            }
            return str.substring(beginIdx + 1, endIdx);
        }

        final int colonIdx = str.indexOf(':', beginIdx);
        if (colonIdx >= 0 && colonIdx < endIdx) {
            // drop a port number
            endIdx = colonIdx;
        }

        if (endIdx - beginIdx < 7) {
            // illegal IPv4 address, value is shorter than 0.0.0.0
            return null;
        }

        return str.substring(beginIdx, endIdx);
    }

    /**
     * Computes the end index of the value of "{@code for=}" parameter.
     *
     * @param value a value of {@link HttpHeaderNames#FORWARDED} header
     * @param beginIdx a begin index of "{@code for=}" parameter value
     * @return the end index of "{@code for=}" parameter value
     */
    private static int computeForParamEndIdx(final String value, final int beginIdx) {
        int endIdx = value.length();

        final int commaIdx = value.indexOf(',', beginIdx);
        if (commaIdx >= 0) {
            endIdx = commaIdx;
        }

        final int semicolonIdx = value.indexOf(';', beginIdx);
        if (semicolonIdx >= 0 && semicolonIdx < endIdx) {
            endIdx = semicolonIdx;
        }
        return endIdx;
    }

    /**
     * Converts a {@link SocketAddress} to its host address string.
     *
     * @param address a {@link SocketAddress} to convert
     * @return {@link String} representation of the host address
     */
    private static String toHostString(final SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            final InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
            final InetAddress inetAddress = inetSocketAddress.getAddress();
            return inetAddress != null ? inetAddress.getHostAddress() : inetSocketAddress.getHostString();
        }
        return address.toString(); // unknown address type, just do a toString()
    }

    /**
     * {@link String#indexOf(int)} equivalent for {@link CharSequence}s.
     *
     * @param sequence a {@link CharSequence}
     * @param ch a char to find an index
     * @return the index of the first occurrence of the character in the {@link CharSequence}, or {@code -1} if the
     * character does not occur
     */
    private static int indexOf(final CharSequence sequence, final char ch) {
        for (int i = 0; i < sequence.length(); i++) {
            if (sequence.charAt(i) == ch) {
                return i;
            }
        }
        return -1;
    }
}
