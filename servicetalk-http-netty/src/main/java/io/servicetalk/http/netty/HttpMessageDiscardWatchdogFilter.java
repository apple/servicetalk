/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

final class HttpMessageDiscardWatchdogFilter {

    static final String PROPERTY_KEY = "io.servicetalk.http.netty.watchdog.usegc";

    static final boolean USE_PHANTOM_REFERENCE = Boolean.getBoolean(PROPERTY_KEY);

    static final String WARN_MESSAGE = "Discovered un-drained HTTP response message body which has " +
            "been dropped by user code - this is a strong indication of a bug " +
            "in a user-defined filter. Response payload (message) body must " +
            "be fully consumed before retrying.";

    private HttpMessageDiscardWatchdogFilter() {
        // no instances.
    }
}
