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
package io.servicetalk.dns.discovery.netty;

import io.netty.util.internal.PlatformDependent;

import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.setAll;

final class DnsTestUtils {

    private static final int[] NUMBERS = new int[254];
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private static final String[] IPV6_ADDRESSES = {
            "0:0:0:0:0:0:1:1",
            "0:0:0:0:0:1:1:1",
            "0:0:0:0:1:1:1:1",
            "0:0:0:1:1:1:1:1",
            "0:0:1:1:1:1:1:1",
            "0:1:1:1:1:1:1:1",
            "1:1:1:1:1:1:1:1",
    };

    private static final AtomicInteger ipv6Idx = new AtomicInteger();

    static {
        setAll(NUMBERS, i -> i + 1);
    }

    private DnsTestUtils() {
        // No instances
    }

    static String nextIp() {
        return ipPart() + "." + ipPart() + '.' + ipPart() + '.' + ipPart();
    }

    static String nextIp6() {
        return IPV6_ADDRESSES[ipv6Idx.getAndIncrement() % IPV6_ADDRESSES.length];
    }

    private static int index(int arrayLength) {
        return PlatformDependent.threadLocalRandom().nextInt(arrayLength);
    }

    private static int ipPart() {
        return NUMBERS[index(NUMBERS.length)];
    }
}
