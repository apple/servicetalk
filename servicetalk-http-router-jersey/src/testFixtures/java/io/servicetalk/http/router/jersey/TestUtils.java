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
package io.servicetalk.http.router.jersey;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.StreamingHttpResponse;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class TestUtils {
    public static final class ContentReadException extends RuntimeException {
        private static final long serialVersionUID = -1340168051096097707L;

        public ContentReadException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    private TestUtils() {
        // no instances
    }

    public static String getContentAsString(final StreamingHttpResponse res) {
        return getContentAsString(res.payloadBody());
    }

    public static String getContentAsString(final Publisher<Buffer> content) {
        try {
            return awaitIndefinitelyNonNull(
                    content.reduce(StringBuilder::new, (sb, b) -> sb.append(b.toString(UTF_8)))).toString();
        } catch (final Throwable t) {
            throw new ContentReadException("Failed to extract content from: " + content, t);
        }
    }
}
