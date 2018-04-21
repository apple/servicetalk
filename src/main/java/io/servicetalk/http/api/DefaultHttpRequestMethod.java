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
package io.servicetalk.http.api;

import io.servicetalk.buffer.Buffer;

import static io.servicetalk.http.api.HttpRequestMethods.HttpRequestMethodProperties.NONE;
import static java.nio.charset.StandardCharsets.US_ASCII;

final class DefaultHttpRequestMethod implements HttpRequestMethod {

    private final Buffer name;
    private final Properties properties;

    DefaultHttpRequestMethod(final Buffer name) {
        this(name, NONE);
    }

    DefaultHttpRequestMethod(final Buffer name, final Properties properties) {
        this.name = name;
        this.properties = properties;
    }

    @Override
    public Buffer getName() {
        return name.duplicate();
    }

    @Override
    public Properties getMethodProperties() {
        return properties;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultHttpRequestMethod that = (DefaultHttpRequestMethod) o;

        return name.equals(that.name) && properties.equals(that.properties);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + properties.hashCode();
    }

    @Override
    public String toString() {
        return name.toString(US_ASCII);
    }

    static final class DefaultHttpRequestMethodProperties implements Properties {
        private final boolean safe;
        private final boolean idempotent;
        private final boolean cacheable;

        DefaultHttpRequestMethodProperties(final boolean safe, final boolean idempotent, final boolean cacheable) {
            this.safe = safe;
            this.idempotent = idempotent;
            this.cacheable = cacheable;
        }

        @Override
        public boolean isSafe() {
            return safe;
        }

        @Override
        public boolean isIdempotent() {
            return idempotent;
        }

        @Override
        public boolean isCacheable() {
            return cacheable;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final DefaultHttpRequestMethodProperties that = (DefaultHttpRequestMethodProperties) o;

            return safe == that.safe && idempotent == that.idempotent && cacheable == that.cacheable;
        }

        @Override
        public int hashCode() {
            int result = (safe ? 1 : 0);
            result = 31 * result + (idempotent ? 1 : 0);
            result = 31 * result + (cacheable ? 1 : 0);
            return result;
        }
    }
}
