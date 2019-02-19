/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

/**
 * HTTP URI Scheme as defined by <a href="https://tools.ietf.org/html/rfc7230#section-2.7">RFC 7230, section 2.7</a>.
 */
public interface HttpScheme {
    /**
     * Returns the name of this {@link HttpScheme}, which is case-insensitive and normally provided in lowercase.
     *
     * @return the name of this {@link HttpScheme}.
     */
    String name();

    /**
     * Returns a default port number for this {@link HttpScheme}.
     *
     * @return a default port number for this {@link HttpScheme}
     */
    int defaultPort();

    /**
     * Compares the specified object with this {@link HttpScheme} for equality.
     * <p>
     * Returns {@code true} if and only if the specified object is also an {@link HttpScheme}, both schemes have the
     * same name (ignoring case) and the same default port number. This definition ensures that the {@code equals}
     * method works properly across different implementations of the {@link HttpScheme} interface.
     *
     * @param o the object to be compared for equality with this {@link HttpScheme}
     * @return {@code true} if the specified object is equal to this {@link HttpScheme}
     */
    @Override
    boolean equals(Object o);
}
