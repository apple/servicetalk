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

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.http.api.CharSequences.contentEquals;
import static io.servicetalk.http.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.http.api.HeaderUtils.DEFAULT_HEADER_FILTER;
import static io.servicetalk.http.api.HeaderUtils.validateCookieTokenAndHeaderName;
import static io.servicetalk.http.api.HttpHeaderNames.COOKIE;
import static io.servicetalk.http.api.HttpHeaderNames.SET_COOKIE;

/**
 * Default implementation of {@link HttpHeaders}.
 */
final class DefaultHttpHeaders extends MultiMap<CharSequence, CharSequence> implements HttpHeaders {
    private final boolean validateNames;

    /**
     * Create a new instance.
     *
     * @param arraySizeHint A hint as to how large the hash data structure should be.
     *                      The next positive power of two will be used. An upper bound may be enforced.
     * @param validateNames {@code true} to validate header names.
     */
    DefaultHttpHeaders(final int arraySizeHint, final boolean validateNames) {
        super(arraySizeHint);
        this.validateNames = validateNames;
    }

    private DefaultHttpHeaders(final DefaultHttpHeaders rhs) {
        super(rhs);
        this.validateNames = rhs.validateNames;
    }

    @Override
    protected MultiMapEntry<CharSequence, CharSequence> newEntry(final CharSequence key, final CharSequence value, final int keyHash) {
        return new MultiMapEntry<CharSequence, CharSequence>(value, keyHash) {
            @Override
            public CharSequence getKey() {
                return key;
            }
        };
    }

    @Override
    public boolean contains(final CharSequence name, final CharSequence value, final boolean caseInsensitive) {
        return caseInsensitive ? contains(name, value, CharSequences::contentEqualsIgnoreCase) : contains(name, value);
    }

    @Override
    public HttpCookies parseCookies(final boolean validateContent) {
        return new DefaultHttpCookies(this, COOKIE, validateContent);
    }

    @Override
    public HttpCookies parseSetCookies(final boolean validateContent) {
        return new DefaultHttpCookies(this, SET_COOKIE, validateContent);
    }

    @Override
    protected void validateKey(@Nullable final CharSequence name) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("empty header names are not allowed");
        }
        if (validateNames) {
            validateHeaderName(name);
        }
    }

    /**
     * Validate a <a href="https://tools.ietf.org/html/rfc7230#section-3.2.6">field-name</a> of a header-field.
     *
     * @param name The filed-name to validate.
     */
    private static void validateHeaderName(final CharSequence name) {
        validateCookieTokenAndHeaderName(name);
    }

    @Nullable
    @Override
    public CharSequence get(final CharSequence name) {
        return getValue(name);
    }

    @Nullable
    @Override
    public CharSequence getAndRemove(final CharSequence name) {
        return removeAllAndGetFirst(name);
    }

    @Override
    public Iterator<? extends CharSequence> getAll(final CharSequence name) {
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
    public HttpHeaders add(final CharSequence name, final CharSequence value) {
        put(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(final CharSequence name, final Iterable<? extends CharSequence> values) {
        putAll(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(final CharSequence name, final CharSequence... values) {
        putAll(name, values);
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public HttpHeaders add(final HttpHeaders headers) {
        if (headers == this) {
            throw new IllegalArgumentException("can't add to itself");
        }
        if (headers instanceof MultiMap) {
            putAll((MultiMap<? extends CharSequence, ? extends CharSequence>) headers);
        } else { // Slow copy
            for (final Map.Entry<? extends CharSequence, ? extends CharSequence> header : headers) {
                add(header.getKey(), header.getValue());
            }
        }
        return this;
    }

    @Override
    public HttpHeaders set(final CharSequence name, final CharSequence value) {
        putExclusive(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(final CharSequence name, final Iterable<? extends CharSequence> values) {
        putExclusive(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(final CharSequence name, final CharSequence... values) {
        putExclusive(name, values);
        return this;
    }

    @Override
    public boolean remove(final CharSequence name) {
        return removeAll(name);
    }

    @Override
    public boolean remove(final CharSequence name, final CharSequence value) {
        return remove(name, value, false);
    }

    @Override
    public boolean remove(final CharSequence name, final CharSequence value, final boolean caseInsensitive) {
        final int nameHash = hashCode(name);
        final int bucketIndex = index(nameHash);
        final BucketHead<CharSequence, CharSequence> bucketHead = entries[bucketIndex];
        if (bucketHead == null) {
            return false;
        }
        final int sizeBefore = size();
        MultiMapEntry<CharSequence, CharSequence> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == nameHash && equals(name, e.getKey()) &&
                    (caseInsensitive ? contentEqualsIgnoreCase(value, e.value) : contentEquals(value, e.value))) {
                final MultiMapEntry<CharSequence, CharSequence> tmpEntry = e;
                e = e.bucketNext;
                removeEntry(bucketHead, tmpEntry, bucketIndex);
            } else {
                e = e.bucketNext;
            }
        } while (e != null);
        return sizeBefore != size();
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
    public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> filter) {
        return HeaderUtils.toString(this, filter);
    }

    @Override
    protected int hashCode(final CharSequence name) {
        return caseInsensitiveHashCode(name);
    }

    @Override
    protected boolean equals(final CharSequence name1, final CharSequence name2) {
        return contentEqualsIgnoreCase(name1, name2);
    }

    @Override
    protected boolean isKeyEqualityCompatible(final MultiMap<? extends CharSequence, ? extends CharSequence> multiMap) {
        return multiMap.getClass().equals(getClass());
    }

    @Override
    protected int hashCodeForValue(final CharSequence value) {
        return value.hashCode();
    }

    @Override
    protected boolean equalsForValue(final CharSequence value1, final CharSequence value2) {
        return contentEquals(value1, value2);
    }
}
