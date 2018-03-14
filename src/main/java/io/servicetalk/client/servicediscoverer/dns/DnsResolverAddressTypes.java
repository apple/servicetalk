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

/**
 * Defined resolved address types.
 */
public enum DnsResolverAddressTypes {
    /**
     * Only resolve IPv4 addresses.
     */
    IPV4_ONLY,
    /**
     * Only resolve IPv6 addresses.
     */
    IPV6_ONLY,
    /**
     * Prefer IPv4 addresses over IPv6 ones.
     */
    IPV4_PREFERRED,
    /**
     * Prefer IPv6 addresses over IPv4 ones.
     */
    IPV6_PREFERRED
}
