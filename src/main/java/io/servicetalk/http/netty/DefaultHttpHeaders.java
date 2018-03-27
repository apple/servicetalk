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

import io.servicetalk.http.api.HttpCookies;
import io.servicetalk.http.api.HttpHeaders;

import io.netty.util.AsciiString;
import io.netty.util.internal.PlatformDependent;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.netty.util.AsciiString.contentEquals;
import static io.netty.util.AsciiString.contentEqualsIgnoreCase;
import static io.servicetalk.http.netty.HeaderUtils.DEFAULT_HEADER_FILTER;
import static io.servicetalk.http.netty.HeaderUtils.HEADER_NAME_VALIDATOR;
import static io.servicetalk.http.netty.HeaderUtils.validateCookieTokenAndHeaderName;
import static io.servicetalk.http.netty.HttpHeaderNames.COOKIE;
import static io.servicetalk.http.netty.HttpHeaderNames.SET_COOKIE;

/**
 * Default implementation of {@link HttpHeaders}.
 */
public final class DefaultHttpHeaders extends MultiMap<CharSequence, CharSequence> implements HttpHeaders {
    private final boolean validateNames;

    /**
     * Create a new instance.
     */
    public DefaultHttpHeaders() {
        this(16, true);
    }

    /**
     * Create a new instance.
     * @param arraySizeHint A hint as to how large the hash data structure should be.
     * The next positive power of two will be used. An upper bound may be enforced.
     * @param validateNames {@code true} to validate header names.
     */
    public DefaultHttpHeaders(int arraySizeHint, boolean validateNames) {
        super(arraySizeHint);
        this.validateNames = validateNames;
    }

    private DefaultHttpHeaders(DefaultHttpHeaders rhs) {
        super(rhs);
        this.validateNames = rhs.validateNames;
    }

    @Override
    protected MultiMapEntry<CharSequence, CharSequence> newEntry(CharSequence key, CharSequence value, int keyHash) {
        return new MultiMapEntry<CharSequence, CharSequence>(value, keyHash) {
            @Override
            public CharSequence getKey() {
                return key;
            }
        };
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean caseInsensitive) {
        return caseInsensitive ? contains(name, value, AsciiString::contentEqualsIgnoreCase) : contains(name, value);
    }

    @Override
    public HttpCookies parseCookies(boolean validateContent) {
        return new DefaultHttpCookies(this, COOKIE, validateContent);
    }

    @Override
    public HttpCookies parseSetCookies(boolean validateContent) {
        return new DefaultHttpCookies(this, SET_COOKIE, validateContent);
    }

    @Override
    protected void validateKey(CharSequence name) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("empty header names are not allowed");
        }
        if (validateNames) {
            validateHeaderName(name);
        }
    }

    /**
     * Validate a <a href="https://tools.ietf.org/html/rfc7230#section-3.2.6">field-name</a> of a header-field.
     * @param name The filed-name to validate.
     */
    private static void validateHeaderName(CharSequence name) {
        if (name instanceof AsciiString) {
            try {
                ((AsciiString) name).forEachByte(HEADER_NAME_VALIDATOR);
            } catch (Exception e) {
                PlatformDependent.throwException(e);
            }
        } else {
            validateCookieTokenAndHeaderName(name);
        }
    }

    @Nullable
    @Override
    public CharSequence get(CharSequence name) {
        return getValue(name);
    }

    @Nullable
    @Override
    public CharSequence getAndRemove(CharSequence name) {
        return removeAllAndGetFirst(name);
    }

    @Override
    public Iterator<? extends CharSequence> getAll(CharSequence name) {
        return getValues(name);
    }

    @Override
    public Set<? extends CharSequence> getNames() {
        return getKeys();
    }

    @Override
    public HttpHeaders clear() {
        clearAll();
        return this;
    }

    @Override
    public Iterator<Map.Entry<CharSequence, CharSequence>> iterator() {
        return entryIterator();
    }

    @Override
    public HttpHeaders add(CharSequence name, CharSequence value) {
        put(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, Iterable<? extends CharSequence> values) {
        putAll(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, CharSequence... values) {
        putAll(name, values);
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public HttpHeaders add(HttpHeaders headers) {
        if (headers == this) {
            throw new IllegalArgumentException("can't add to itself");
        }
        if (headers instanceof MultiMap) {
            putAll((MultiMap<? extends CharSequence, ? extends CharSequence>) headers);
        } else { // Slow copy
            for (Map.Entry<? extends CharSequence, ? extends CharSequence> header : headers) {
                add(header.getKey(), header.getValue());
            }
        }
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, CharSequence value) {
        putExclusive(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Iterable<? extends CharSequence> values) {
        putExclusive(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, CharSequence... values) {
        putExclusive(name, values);
        return this;
    }

    @Override
    public boolean remove(CharSequence name) {
        return removeAll(name);
    }

    @Override
    public HttpHeaders copy() {
        return new DefaultHttpHeaders(this);
    }

    @Override
    public String toString() {
        return toString(DEFAULT_HEADER_FILTER);
    }

    @Override
    public String toString(BiFunction<? super CharSequence, ? super CharSequence, CharSequence> filter) {
        return HeaderUtils.toString(this, filter);
    }

    @Override
    protected int hashCode(CharSequence name) {
        return AsciiString.hashCode(name);
    }

    @Override
    protected boolean equals(CharSequence name1, CharSequence name2) {
        return contentEqualsIgnoreCase(name1, name2);
    }

    @Override
    protected boolean isKeyEqualityCompatible(MultiMap<? extends CharSequence, ? extends CharSequence> multiMap) {
        return multiMap.getClass().equals(getClass());
    }

    @Override
    protected int hashCodeForValue(CharSequence value) {
        return AsciiString.hashCode(value);
    }

    @Override
    protected boolean equalsForValue(CharSequence value1, CharSequence value2) {
        return contentEquals(value1, value2);
    }
}
