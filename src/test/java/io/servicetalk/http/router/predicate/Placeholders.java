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
package io.servicetalk.http.router.predicate;

import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMethod;

import static org.mockito.Mockito.mock;

/**
 * Placeholder values until real ones exist.
 */
final class Placeholders {
    static final HttpRequestMethod POST = mock(HttpRequestMethod.class, "POST");
    static final HttpRequestMethod GET = mock(HttpRequestMethod.class, "GET");
    static final HttpRequestMethod PUT = mock(HttpRequestMethod.class, "PUT");

    static final HttpProtocolVersion HTTP_1_0 = mock(HttpProtocolVersion.class, "HTTP_1_0");
    static final HttpProtocolVersion HTTP_1_1 = mock(HttpProtocolVersion.class, "HTTP_1_1");

    private Placeholders() {
        // no instances
    }
}
