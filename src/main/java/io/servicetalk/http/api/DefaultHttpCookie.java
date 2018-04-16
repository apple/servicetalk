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

import java.util.Objects;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.CharSequences.contentEqualsIgnoreCase;

final class DefaultHttpCookie implements HttpCookie {
    private final CharSequence name;
    private final CharSequence value;
    @Nullable
    private final CharSequence path;
    @Nullable
    private final CharSequence domain;
    @Nullable
    private final CharSequence expires;
    @Nullable
    private final Long maxAge;
    private final boolean wrapped;
    private final boolean secure;
    private final boolean httpOnly;

    DefaultHttpCookie(final CharSequence name, final CharSequence value, @Nullable final CharSequence path,
                      @Nullable final CharSequence domain, @Nullable final CharSequence expires,
                      @Nullable final Long maxAge, final boolean wrapped, final boolean secure, final boolean httpOnly) {
        this.name = name;
        this.value = value;
        this.path = path;
        this.domain = domain;
        this.expires = expires;
        this.maxAge = maxAge;
        this.wrapped = wrapped;
        this.secure = secure;
        this.httpOnly = httpOnly;
    }

    @Override
    public CharSequence getName() {
        return name;
    }

    @Override
    public CharSequence getValue() {
        return value;
    }

    @Override
    public boolean isWrapped() {
        return wrapped;
    }

    @Nullable
    @Override
    public CharSequence getDomain() {
        return domain;
    }

    @Nullable
    @Override
    public CharSequence getPath() {
        return path;
    }

    @Nullable
    @Override
    public Long getMaxAge() {
        return maxAge;
    }

    @Nullable
    @Override
    public CharSequence getExpires() {
        return expires;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public boolean isHttpOnly() {
        return httpOnly;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof HttpCookie)) {
            return false;
        }
        final HttpCookie rhs = (HttpCookie) o;
        // It is not possible to do domain [1] and path [2] equality and preserve the equals/hashCode API because the
        // equality comparisons in the RFC are variable so we cannot guarantee the following property:
        // if equals(a) == equals(b) then a.hasCode() == b.hashCode()
        // [1] https://tools.ietf.org/html/rfc6265#section-5.1.3
        // [2] https://tools.ietf.org/html/rfc6265#section-5.1.4
        return contentEqualsIgnoreCase(name, rhs.getName()) &&
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
