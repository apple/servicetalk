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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpCookie;

import io.netty.util.AsciiString;

import java.util.Objects;
import javax.annotation.Nullable;

import static io.netty.util.AsciiString.contentEqualsIgnoreCase;

final class DefaultHttpCookie implements HttpCookie {
    private final String name;
    private final String value;
    @Nullable
    private final String path;
    @Nullable
    private final String domain;
    @Nullable
    private final String expires;
    @Nullable
    private final Long maxAge;
    private final boolean isWrapped;
    private final boolean isSecure;
    private final boolean isHttpOnly;

    DefaultHttpCookie(String name, String value, @Nullable String path,
                      @Nullable String domain, @Nullable String expires,
                      @Nullable Long maxAge, boolean isWrapped, boolean isSecure, boolean isHttpOnly) {
        this.name = name;
        this.value = value;
        this.path = path;
        this.domain = domain;
        this.expires = expires;
        this.maxAge = maxAge;
        this.isWrapped = isWrapped;
        this.isSecure = isSecure;
        this.isHttpOnly = isHttpOnly;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public boolean isWrapped() {
        return isWrapped;
    }

    @Nullable
    @Override
    public String getDomain() {
        return domain;
    }

    @Nullable
    @Override
    public String getPath() {
        return path;
    }

    @Nullable
    @Override
    public Long getMaxAge() {
        return maxAge;
    }

    @Nullable
    @Override
    public String getExpires() {
        return expires;
    }

    @Override
    public boolean isSecure() {
        return isSecure;
    }

    @Override
    public boolean isHttpOnly() {
        return isHttpOnly;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpCookie)) {
            return false;
        }
        HttpCookie rhs = (HttpCookie) o;
        // It is not possible to do domain [1] and path [2] equality and preserve the equals/hashCode API because the
        // equality comparisons in the RFC are variable so we cannot guarantee the following property:
        // if equals(a) == equals(b) then a.hasCode() == b.hashCode()
        // [1] https://tools.ietf.org/html/rfc6265#section-5.1.3
        // [2] https://tools.ietf.org/html/rfc6265#section-5.1.4
        return name.equalsIgnoreCase(rhs.getName()) &&
                contentEqualsIgnoreCase(domain, rhs.getDomain()) &&
                Objects.equals(path, rhs.getPath());
    }

    @Override
    public int hashCode() {
        int hash = 31 + AsciiString.hashCode(name);
        if (domain != null) {
            hash = 31 * hash + AsciiString.hashCode(domain);
        }
        if (path != null) {
            hash = 31 * hash + path.hashCode();
        }
        return hash;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + name + "]";
    }
}
