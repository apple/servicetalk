/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpHeaders;

import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.AUTHORITY;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.METHOD;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.PATH;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.SCHEME;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.STATUS;
import static java.util.Objects.requireNonNull;

final class ServiceTalkHttpHeadersAsHttp2Headers implements Http2Headers {

    private final HttpHeaders underlying;
    private final boolean lowercaseKeys;

    // HTTP/2 pseudo-headers - bounded cardinality, so use raw fields
    // Note: :authority is stored as Host header in underlying for consistency with HTTP/1.x
    @Nullable
    private CharSequence method;
    @Nullable
    private CharSequence path;
    @Nullable
    private CharSequence scheme;
    @Nullable
    private CharSequence status;

    ServiceTalkHttpHeadersAsHttp2Headers(HttpHeaders underlying, boolean lowercaseKeys) {
        this.underlying = requireNonNull(underlying);
        this.lowercaseKeys = lowercaseKeys;
    }

    @Override
    public Iterator<Map.Entry<CharSequence, CharSequence>> iterator() {
        return new Http2HeadersIterator();
    }

    // Iterator that pre-stages the next value for reliable hasNext()/next() behavior.
    // Iterates through pseudo-headers first, then regular headers (excluding Host which becomes :authority).
    private final class Http2HeadersIterator implements Iterator<Map.Entry<CharSequence, CharSequence>> {
        // States for pseudo-headers iteration (order matters for HTTP/2 spec compliance)
        private static final int STATE_METHOD = 0;
        private static final int STATE_SCHEME = 1;
        private static final int STATE_AUTHORITY = 2;
        private static final int STATE_PATH = 3;
        private static final int STATE_STATUS = 4;
        private static final int STATE_REGULAR_HEADERS = 5;

        private final Iterator<Map.Entry<CharSequence, CharSequence>> underlyingIterator = underlying.iterator();
        private int pseudoHeaderState = STATE_METHOD;

        @Nullable
        private Map.Entry<CharSequence, CharSequence> nextValue;

        Http2HeadersIterator() {
            // Pre-stage the first value on initialization
            advanceToNext();
        }

        @Override
        public boolean hasNext() {
            return nextValue != null;
        }

        @Override
        public Map.Entry<CharSequence, CharSequence> next() {
            if (nextValue == null) {
                throw new java.util.NoSuchElementException("No more elements");
            }

            Map.Entry<CharSequence, CharSequence> result = nextValue;
            advanceToNext(); // Pre-stage the next value
            return result;
        }

        private void advanceToNext() {
            // First, try to find the next pseudo-header
            while (pseudoHeaderState < STATE_REGULAR_HEADERS) {
                Map.Entry<CharSequence, CharSequence> pseudoEntry = getCurrentPseudoHeaderEntry();
                pseudoHeaderState++;
                if (pseudoEntry != null) {
                    nextValue = pseudoEntry;
                    return;
                }
            }
            // Then, look for regular headers (skipping Host since it's returned as :authority)
            while (underlyingIterator.hasNext()) {
                Map.Entry<CharSequence, CharSequence> entry = underlyingIterator.next();
                // Skip Host header since it's already returned as :authority
                if (!HOST.equals(entry.getKey())) {
                    nextValue = lowercaseKeys ? lowerCaseKey(entry) : entry;
                    return;
                }
            }
            // No more entries available
            nextValue = null;
        }

        // Gets the current pseudo-header entry if it exists.
        @Nullable
        private Map.Entry<CharSequence, CharSequence> getCurrentPseudoHeaderEntry() {
            switch (pseudoHeaderState) {
                case STATE_METHOD:
                    return method != null ? entry(METHOD.value(), method) : null;
                case STATE_SCHEME:
                    return scheme != null ? entry(SCHEME.value(), scheme) : null;
                case STATE_AUTHORITY:
                    CharSequence authority = underlying.get(HOST);
                    return authority != null ? entry(AUTHORITY.value(), authority) : null;
                case STATE_PATH:
                    return path != null ? entry(PATH.value(), path) : null;
                case STATE_STATUS:
                    return status != null ? entry(STATUS.value(), status) : null;
                default:
                    return null;
            }
        }
    }

    @Override
    public Iterator<CharSequence> valueIterator(CharSequence name) {
        CharSequence pseudoValue = getPseudoHeaderValue(name);
        if (pseudoValue != null) {
            return Collections.singletonList(pseudoValue).iterator();
        }
        // Cast to handle wildcard issues
        @SuppressWarnings("unchecked")
        Iterator<CharSequence> result = (Iterator<CharSequence>) underlying.valuesIterator(name);
        return result;
    }

    @Override
    public Http2Headers method(@Nullable CharSequence value) {
        this.method = value;
        return this;
    }

    @Override
    public Http2Headers scheme(@Nullable CharSequence value) {
        this.scheme = value;
        return this;
    }

    @Override
    public Http2Headers authority(@Nullable CharSequence value) {
        if (value != null) {
            underlying.set(HOST, value);
        } else {
            underlying.remove(HOST);
        }
        return this;
    }

    @Override
    public Http2Headers path(@Nullable CharSequence value) {
        this.path = value;
        return this;
    }

    @Override
    public Http2Headers status(@Nullable CharSequence value) {
        this.status = value;
        return this;
    }

    @Override
    @Nullable
    public CharSequence method() {
        return method;
    }

    @Override
    @Nullable
    public CharSequence scheme() {
        return scheme;
    }

    @Override
    @Nullable
    public CharSequence authority() {
        return underlying.get(HOST);
    }

    @Override
    @Nullable
    public CharSequence path() {
        return path;
    }

    @Override
    @Nullable
    public CharSequence status() {
        return status;
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean caseInsensitive) {
        CharSequence pseudoValue = getPseudoHeaderValue(name);
        if (pseudoValue != null) {
            if (caseInsensitive) {
                return pseudoValue.toString().equalsIgnoreCase(value.toString());
            } else {
                return pseudoValue.equals(value);
            }
        }
        return underlying.contains(name, value) || underlying.containsIgnoreCase(name, value);
    }

    @Override
    public CharSequence get(CharSequence name) {
        CharSequence pseudoValue = getPseudoHeaderValue(name);
        return pseudoValue != null ? pseudoValue : underlying.get(name);
    }

    @Override
    public CharSequence get(CharSequence name, CharSequence defaultValue) {
        CharSequence value = get(name);
        return value != null ? value : defaultValue;
    }

    @Override
    public CharSequence getAndRemove(CharSequence name) {
        CharSequence pseudoValue = getPseudoHeaderValue(name);
        if (pseudoValue != null) {
            setPseudoHeaderValue(name, null);
            return pseudoValue;
        }
        return underlying.getAndRemove(name);
    }

    @Override
    public CharSequence getAndRemove(CharSequence name, CharSequence defaultValue) {
        CharSequence value = getAndRemove(name);
        return value != null ? value : defaultValue;
    }

    @Override
    public List<CharSequence> getAll(CharSequence name) {
        if (isPseudoHeader(name)) {
            CharSequence pseudoValue = getPseudoHeaderValue(name);
            return pseudoValue == null ? Collections.emptyList() : Collections.singletonList(pseudoValue);
        } else {
            List<CharSequence> result = new ArrayList<>();
            for (CharSequence value : underlying.values(name)) {
                result.add(value);
            }
            return result;
        }
    }

    @Override
    public List<CharSequence> getAllAndRemove(CharSequence name) {
        CharSequence pseudoValue = getPseudoHeaderValue(name);
        if (pseudoValue != null) {
            setPseudoHeaderValue(name, null);
            return Collections.singletonList(pseudoValue);
        }
        List<CharSequence> values = new ArrayList<>();
        for (CharSequence value : underlying.values(name)) {
            values.add(value);
        }
        underlying.remove(name);
        return values;
    }

    @Override
    public Boolean getBoolean(CharSequence name) {
        return unsupportedOperation("getBoolean");
    }

    @Override
    public boolean getBoolean(CharSequence name, boolean defaultValue) {
        return unsupportedOperation("getBoolean");
    }

    @Override
    public Byte getByte(CharSequence name) {
        return unsupportedOperation("getByte");
    }

    @Override
    public byte getByte(CharSequence name, byte defaultValue) {
        return unsupportedOperation("getByte");
    }

    @Override
    public Character getChar(CharSequence name) {
        return unsupportedOperation("getChar");
    }

    @Override
    public char getChar(CharSequence name, char defaultValue) {
        return unsupportedOperation("getChar");
    }

    @Override
    public Short getShort(CharSequence name) {
        return unsupportedOperation("getShort");
    }

    @Override
    public short getShort(CharSequence name, short defaultValue) {
        return unsupportedOperation("getShort");
    }

    @Override
    public Integer getInt(CharSequence name) {
        return unsupportedOperation("getInt");
    }

    @Override
    public int getInt(CharSequence name, int defaultValue) {
        return unsupportedOperation("getInt");
    }

    @Override
    public Long getLong(CharSequence name) {
        return unsupportedOperation("getLong");
    }

    @Override
    public long getLong(CharSequence name, long defaultValue) {
        return unsupportedOperation("getLong");
    }

    @Override
    public Float getFloat(CharSequence name) {
        return unsupportedOperation("getFloat");
    }

    @Override
    public float getFloat(CharSequence name, float defaultValue) {
        return unsupportedOperation("getFloat");
    }

    @Override
    public Double getDouble(CharSequence name) {
        return unsupportedOperation("getDouble");
    }

    @Override
    public double getDouble(CharSequence name, double defaultValue) {
        return unsupportedOperation("getDouble");
    }

    @Override
    public Long getTimeMillis(CharSequence name) {
        return unsupportedOperation("getTimeMillis");
    }

    @Override
    public long getTimeMillis(CharSequence name, long defaultValue) {
        return unsupportedOperation("getTimeMillis");
    }

    @Override
    public Boolean getBooleanAndRemove(CharSequence name) {
        return unsupportedOperation("getBooleanAndRemove");
    }

    @Override
    public boolean getBooleanAndRemove(CharSequence name, boolean defaultValue) {
        return unsupportedOperation("getBooleanAndRemove");
    }

    @Override
    public Byte getByteAndRemove(CharSequence name) {
        return unsupportedOperation("getByteAndRemove");
    }

    @Override
    public byte getByteAndRemove(CharSequence name, byte defaultValue) {
        return unsupportedOperation("getByteAndRemove");
    }

    @Override
    public Character getCharAndRemove(CharSequence name) {
        return unsupportedOperation("getCharAndRemove");
    }

    @Override
    public char getCharAndRemove(CharSequence name, char defaultValue) {
        return unsupportedOperation("getCharAndRemove");
    }

    @Override
    public Short getShortAndRemove(CharSequence name) {
        return unsupportedOperation("getShortAndRemove");
    }

    @Override
    public short getShortAndRemove(CharSequence name, short defaultValue) {
        return unsupportedOperation("getShortAndRemove");
    }

    @Override
    public Integer getIntAndRemove(CharSequence name) {
        return unsupportedOperation("getIntAndRemove");
    }

    @Override
    public int getIntAndRemove(CharSequence name, int defaultValue) {
        return unsupportedOperation("getIntAndRemove");
    }

    @Override
    public Long getLongAndRemove(CharSequence name) {
        return unsupportedOperation("getLongAndRemove");
    }

    @Override
    public long getLongAndRemove(CharSequence name, long defaultValue) {
        return unsupportedOperation("getLongAndRemove");
    }

    @Override
    public Float getFloatAndRemove(CharSequence name) {
        return unsupportedOperation("getFloatAndRemove");
    }

    @Override
    public float getFloatAndRemove(CharSequence name, float defaultValue) {
        return unsupportedOperation("getFloatAndRemove");
    }

    @Override
    public Double getDoubleAndRemove(CharSequence name) {
        return unsupportedOperation("getDoubleAndRemove");
    }

    @Override
    public double getDoubleAndRemove(CharSequence name, double defaultValue) {
        return unsupportedOperation("getDoubleAndRemove");
    }

    @Override
    public Long getTimeMillisAndRemove(CharSequence name) {
        return unsupportedOperation("getTimeMillisAndRemove");
    }

    @Override
    public long getTimeMillisAndRemove(CharSequence name, long defaultValue) {
        return unsupportedOperation("getTimeMillisAndRemove");
    }

    @Override
    public boolean contains(CharSequence name) {
        return getPseudoHeaderValue(name) != null || underlying.contains(name);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value) {
        return contains(name, value, false);
    }

    @Override
    public boolean containsObject(CharSequence name, Object value) {
        return unsupportedOperation("containsObject");
    }

    @Override
    public boolean containsBoolean(CharSequence name, boolean value) {
        return unsupportedOperation("containsBoolean");
    }

    @Override
    public boolean containsByte(CharSequence name, byte value) {
        return unsupportedOperation("containsByte");
    }

    @Override
    public boolean containsChar(CharSequence name, char value) {
        return unsupportedOperation("containsChar");
    }

    @Override
    public boolean containsShort(CharSequence name, short value) {
        return unsupportedOperation("containsShort");
    }

    @Override
    public boolean containsInt(CharSequence name, int value) {
        return unsupportedOperation("containsInt");
    }

    @Override
    public boolean containsLong(CharSequence name, long value) {
        return unsupportedOperation("containsLong");
    }

    @Override
    public boolean containsFloat(CharSequence name, float value) {
        return unsupportedOperation("containsFloat");
    }

    @Override
    public boolean containsDouble(CharSequence name, double value) {
        return unsupportedOperation("containsDouble");
    }

    @Override
    public boolean containsTimeMillis(CharSequence name, long value) {
        return unsupportedOperation("containsTimeMillis");
    }

    @Override
    public int size() {
        int pseudoHeaderCount = 0;
        if (method != null) {
            pseudoHeaderCount++;
        }
        if (path != null) {
            pseudoHeaderCount++;
        }
        if (scheme != null) {
            pseudoHeaderCount++;
        }
        if (underlying.contains(HOST)) {
            pseudoHeaderCount++;
        }
        if (status != null) {
            pseudoHeaderCount++;
        }
        return pseudoHeaderCount + underlying.size();
    }

    @Override
    public boolean isEmpty() {
        return method == null && path == null && scheme == null && status == null && underlying.isEmpty();
    }

    @Override
    public Set<CharSequence> names() {
        Set<CharSequence> allNames = new HashSet<>(underlying.names());
        if (method != null) {
            allNames.add(METHOD.value());
        }
        if (path != null) {
            allNames.add(PATH.value());
        }
        if (scheme != null) {
            allNames.add(SCHEME.value());
        }
        if (underlying.contains(HOST)) {
            allNames.add(AUTHORITY.value());
        }
        if (status != null) {
            allNames.add(STATUS.value());
        }
        return allNames;
    }

    @Override
    public Http2Headers add(CharSequence name, CharSequence value) {
        if (isPseudoHeader(name)) {
            // For pseudo-headers, we set (replace) rather than add, as they should be unique
            setPseudoHeaderValue(name, value);
        } else {
            underlying.add(name, value);
        }
        return this;
    }

    @Override
    public Http2Headers add(CharSequence name, Iterable<? extends CharSequence> values) {
        if (isPseudoHeader(name)) {
            // Take the last value for pseudo-headers as they should be unique
            CharSequence lastValue = null;
            for (CharSequence value : values) {
                lastValue = value;
            }
            if (lastValue != null) {
                setPseudoHeaderValue(name, lastValue);
            }
        } else {
            underlying.add(name, values);
        }
        return this;
    }

    @Override
    public Http2Headers add(CharSequence name, CharSequence... values) {
        if (isPseudoHeader(name) && values.length > 0) {
            // Take the last value for pseudo-headers as they should be unique
            setPseudoHeaderValue(name, values[values.length - 1]);
        } else {
            underlying.add(name, values);
        }
        return this;
    }

    @Override
    public Http2Headers addObject(CharSequence name, Object value) {
        return unsupportedOperation("addObject");
    }

    @Override
    public Http2Headers addObject(CharSequence name, Iterable<?> values) {
        return unsupportedOperation("addObject");
    }

    @Override
    public Http2Headers addObject(CharSequence name, Object... values) {
        return unsupportedOperation("addObject");
    }

    @Override
    public Http2Headers addBoolean(CharSequence name, boolean value) {
        return unsupportedOperation("addBoolean");
    }

    @Override
    public Http2Headers addByte(CharSequence name, byte value) {
        return unsupportedOperation("addByte");
    }

    @Override
    public Http2Headers addChar(CharSequence name, char value) {
        return unsupportedOperation("addChar");
    }

    @Override
    public Http2Headers addShort(CharSequence name, short value) {
        return unsupportedOperation("addShort");
    }

    @Override
    public Http2Headers addInt(CharSequence name, int value) {
        underlying.add(name, String.valueOf(value));
        return this;
    }

    @Override
    public Http2Headers addLong(CharSequence name, long value) {
        return unsupportedOperation("addLong");
    }

    @Override
    public Http2Headers addFloat(CharSequence name, float value) {
        return unsupportedOperation("addFloat");
    }

    @Override
    public Http2Headers addDouble(CharSequence name, double value) {
        return unsupportedOperation("addDouble");
    }

    @Override
    public Http2Headers addTimeMillis(CharSequence name, long value) {
        return unsupportedOperation("addTimeMillis");
    }

    @Override
    public Http2Headers add(Headers<? extends CharSequence, ? extends CharSequence, ?> headers) {
        return unsupportedOperation("add(Headers)");
    }

    @Override
    public Http2Headers set(CharSequence name, CharSequence value) {
        if (isPseudoHeader(name)) {
            setPseudoHeaderValue(name, value);
        } else {
            underlying.set(name, value);
        }
        return this;
    }

    @Override
    public Http2Headers set(CharSequence name, Iterable<? extends CharSequence> values) {
        if (isPseudoHeader(name)) {
            // Take the last value for pseudo-headers as they should be unique
            CharSequence lastValue = null;
            for (CharSequence value : values) {
                lastValue = value;
            }
            setPseudoHeaderValue(name, lastValue);
        } else {
            underlying.set(name, values);
        }
        return this;
    }

    @Override
    public Http2Headers set(CharSequence name, CharSequence... values) {
        if (isPseudoHeader(name)) {
            CharSequence valueToSet = values.length > 0 ? values[values.length - 1] : null;
            setPseudoHeaderValue(name, valueToSet);
        } else {
            underlying.set(name, values);
        }
        return this;
    }

    @Override
    public Http2Headers setObject(CharSequence name, Object value) {
        return unsupportedOperation("setObject");
    }

    @Override
    public Http2Headers setObject(CharSequence name, Iterable<?> values) {
        return unsupportedOperation("setObject");
    }

    @Override
    public Http2Headers setObject(CharSequence name, Object... values) {
        return unsupportedOperation("setObject");
    }

    @Override
    public Http2Headers setBoolean(CharSequence name, boolean value) {
        return unsupportedOperation("setBoolean");
    }

    @Override
    public Http2Headers setByte(CharSequence name, byte value) {
        return unsupportedOperation("setByte");
    }

    @Override
    public Http2Headers setChar(CharSequence name, char value) {
        return unsupportedOperation("setChar");
    }

    @Override
    public Http2Headers setShort(CharSequence name, short value) {
        return unsupportedOperation("setShort");
    }

    @Override
    public Http2Headers setInt(CharSequence name, int value) {
        underlying.set(name, String.valueOf(value));
        return this;
    }

    @Override
    public Http2Headers setLong(CharSequence name, long value) {
        return set(name, String.valueOf(value));
    }

    @Override
    public Http2Headers setFloat(CharSequence name, float value) {
        return unsupportedOperation("setFloat");
    }

    @Override
    public Http2Headers setDouble(CharSequence name, double value) {
        return unsupportedOperation("setDouble");
    }

    @Override
    public Http2Headers setTimeMillis(CharSequence name, long value) {
        return unsupportedOperation("setTimeMillis");
    }

    @Override
    public Http2Headers set(Headers<? extends CharSequence, ? extends CharSequence, ?> headers) {
        return unsupportedOperation("set(Headers)");
    }

    @Override
    public Http2Headers setAll(Headers<? extends CharSequence, ? extends CharSequence, ?> headers) {
        return unsupportedOperation("setAll");
    }

    @Override
    public boolean remove(CharSequence name) {
        if (isPseudoHeader(name)) {
            boolean hadValue = getPseudoHeaderValue(name) != null;
            setPseudoHeaderValue(name, null);
            return hadValue;
        } else {
            return underlying.remove(name);
        }
    }

    @Override
    public Http2Headers clear() {
        method = null;
        path = null;
        scheme = null;
        status = null;
        underlying.clear();
        return this;
    }

    // Helper methods for pseudo-header handling
    @Nullable
    private CharSequence getPseudoHeaderValue(CharSequence name) {
        if (METHOD.value().equals(name)) {
            return method;
        } else if (PATH.value().equals(name)) {
            return path;
        } else if (SCHEME.value().equals(name)) {
            return scheme;
        } else if (AUTHORITY.value().equals(name)) {
            return underlying.get(HOST);
        } else if (STATUS.value().equals(name)) {
            return status;
        }
        return null;
    }

    private void setPseudoHeaderValue(CharSequence name, @Nullable CharSequence value) {
        if (METHOD.value().equals(name)) {
            this.method = value;
        } else if (PATH.value().equals(name)) {
            this.path = value;
        } else if (SCHEME.value().equals(name)) {
            this.scheme = value;
        } else if (AUTHORITY.value().equals(name)) {
            if (value != null) {
                underlying.set(HOST, value);
            } else {
                underlying.remove(HOST);
            }
        } else if (STATUS.value().equals(name)) {
            this.status = value;
        }
    }

    /**
     * Get the underlying HttpHeaders without pseudo-headers.
     */
    HttpHeaders httpHeaders() {
        return underlying;
    }

    private <T> T unsupportedOperation(String methodName) {
        throw new UnsupportedOperationException(methodName + " is not supported by " + getClass().getSimpleName());
    }

    private static boolean isPseudoHeader(CharSequence name) {
        return name.length() > 0 && name.charAt(0) == ':';
    }

    private static Map.Entry<CharSequence, CharSequence> entry(CharSequence key, CharSequence value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    private static Map.Entry<CharSequence, CharSequence> lowerCaseKey(Map.Entry<CharSequence, CharSequence> entry) {
        CharSequence lowerCaseKey = toLowerCase(entry.getKey());
        if (lowerCaseKey == entry.getKey()) {
            return entry;
        } else {
            return entry(lowerCaseKey, entry.getValue());
        }
    }

    private static CharSequence toLowerCase(CharSequence value) {
        int len = value.length();
        for (int i = 0; i < len; i++) {
            if (AsciiString.isUpperCase(value.charAt(i))) {
                return new LowerCaseCharSequence(value);
            }
        }
        // already lower case
        return value;
    }

    private static final class LowerCaseCharSequence implements CharSequence {

        private final CharSequence delegate;

        LowerCaseCharSequence(CharSequence delegate) {
            this.delegate = delegate;
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public char charAt(int index) {
            return AsciiString.toLowerCase(delegate.charAt(index));
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return toLowerCase(delegate.subSequence(start, end));
        }

        @Override
        public String toString() {
            // Since we should only use this class when feeding entries to netty we shouldn't
            // actually ever hit this code path so it's okay if it's a bit slow.
            int len = length();
            StringBuilder builder = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                builder.append(charAt(i));
            }
            return builder.toString();
        }
    }
}
