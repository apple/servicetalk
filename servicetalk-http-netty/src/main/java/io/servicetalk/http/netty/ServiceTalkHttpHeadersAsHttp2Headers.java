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

import io.netty.handler.codec.DefaultHeaders;
import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http.HttpHeaderValidationUtil;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.internal.PlatformDependent;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.AUTHORITY;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.METHOD;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.PATH;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.PROTOCOL;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.SCHEME;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.STATUS;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.isPseudoHeader;
import static io.netty.util.AsciiString.isUpperCase;
import static java.util.Objects.requireNonNull;

final class ServiceTalkHttpHeadersAsHttp2Headers implements Http2Headers {

    // TODO: use the validators from Netty once they're made public
    private static final ByteProcessor HTTP2_NAME_VALIDATOR_PROCESSOR = (c) -> !isUpperCase(c);

    private static final DefaultHeaders.NameValidator<CharSequence> HTTP2_NAME_VALIDATOR = (CharSequence name) -> {
        if (name == null || name.length() == 0) {
            PlatformDependent.throwException(connectionError(PROTOCOL_ERROR,
                    "empty headers are not allowed [%s]", name));
        }

        if (hasPseudoHeaderFormat(name)) {
            if (!isPseudoHeader(name)) {
                PlatformDependent.throwException(connectionError(
                        PROTOCOL_ERROR, "Invalid HTTP/2 pseudo-header '%s' encountered.", name));
            }
            // no need for lower-case validation, we trust our own pseudo header constants
            return;
        }

        if (name instanceof AsciiString) {
            final int index;
            try {
                index = ((AsciiString) name).forEachByte(HTTP2_NAME_VALIDATOR_PROCESSOR);
            } catch (Http2Exception e) {
                PlatformDependent.throwException(e);
                return;
            } catch (Throwable t) {
                PlatformDependent.throwException(connectionError(PROTOCOL_ERROR, t,
                        "unexpected error. invalid header name [%s]", name));
                return;
            }

            if (index != -1) {
                PlatformDependent.throwException(connectionError(PROTOCOL_ERROR,
                        "invalid header name [%s]", name));
            }
        } else {
            for (int i = 0; i < name.length(); ++i) {
                if (isUpperCase(name.charAt(i))) {
                    PlatformDependent.throwException(connectionError(PROTOCOL_ERROR,
                            "invalid header name [%s]", name));
                }
            }
        }
    };

    private static final DefaultHeaders.ValueValidator<CharSequence> HTTP2_VALUE_VALIDATOR = (value) -> {
        int index = HttpHeaderValidationUtil.validateValidHeaderValue(value);
        if (index != -1) {
            throw new IllegalArgumentException("a header value contains prohibited character 0x" +
                    Integer.toHexString(value.charAt(index)) + " at index " + index + '.');
        }
    };

    private final HttpHeaders underlying;
    private final boolean validateNames;
    private final boolean validateValues;
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

    ServiceTalkHttpHeadersAsHttp2Headers(HttpHeaders underlying, boolean lowercaseKeys,
                                         boolean validateNames, boolean validateValues) {
        this.underlying = requireNonNull(underlying);
        this.lowercaseKeys = lowercaseKeys;
        this.validateNames = validateNames;
        this.validateValues = validateValues;
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
                throw new NoSuchElementException("No more elements");
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
                if (!HOST.contentEquals(entry.getKey())) {
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
        if (hasPseudoHeaderFormat(name)) {
            CharSequence pseudoValue = getPseudoHeaderValue(name);
            return pseudoValue != null ? Collections.singletonList(pseudoValue).iterator() :
                    Collections.emptyIterator();
        } else {
            // Cast to handle wildcard issues
            return (Iterator<CharSequence>) underlying.valuesIterator(name);
        }
    }

    @Override
    public Http2Headers method(@Nullable CharSequence value) {
        maybeValidateValue(value);
        this.method = value;
        return this;
    }

    @Override
    public Http2Headers scheme(@Nullable CharSequence value) {
        maybeValidateValue(value);
        this.scheme = value;
        return this;
    }

    @Override
    public Http2Headers authority(@Nullable CharSequence value) {
        maybeValidateValue(value);
        if (value != null) {
            underlying.set(HOST, value);
        } else {
            underlying.remove(HOST);
        }
        return this;
    }

    @Override
    public Http2Headers path(@Nullable CharSequence value) {
        maybeValidateValue(value);
        this.path = value;
        return this;
    }

    @Override
    public Http2Headers status(@Nullable CharSequence value) {
        maybeValidateValue(value);
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
        return unsupportedOperation("contains");
    }

    @Override
    @Nullable
    public CharSequence get(CharSequence name) {
        return hasPseudoHeaderFormat(name) ? getPseudoHeaderValue(name) : underlying.get(name);
    }

    @Override
    public CharSequence get(CharSequence name, CharSequence defaultValue) {
        CharSequence value = get(name);
        return value != null ? value : defaultValue;
    }

    @Override
    @Nullable
    public CharSequence getAndRemove(CharSequence name) {
        if (hasPseudoHeaderFormat(name)) {
            CharSequence pseudoValue = getPseudoHeaderValue(name);
            if (pseudoValue != null) {
                setPseudoHeaderValue(name, null);
            }
            return pseudoValue;
        } else {
            return underlying.getAndRemove(name);
        }
    }

    @Override
    public CharSequence getAndRemove(CharSequence name, CharSequence defaultValue) {
        CharSequence value = getAndRemove(name);
        return value != null ? value : defaultValue;
    }

    @Override
    public List<CharSequence> getAll(CharSequence name) {
        if (hasPseudoHeaderFormat(name)) {
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
        return unsupportedOperation("getAllAndRemove");
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
        return hasPseudoHeaderFormat(name) ? getPseudoHeaderValue(name) != null : underlying.contains(name);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value) {
        return unsupportedOperation("contains(name, value)");
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
        return unsupportedOperation("names");
    }

    @Override
    public Http2Headers add(CharSequence name, CharSequence value) {
        if (validateNames) {
            HTTP2_NAME_VALIDATOR.validateName(name);
        }
        if (validateValues) {
            HTTP2_VALUE_VALIDATOR.validate(value);
        }
        if (hasPseudoHeaderFormat(name)) {
            // For pseudo-headers, we set (replace) rather than add, as they should be unique
            setPseudoHeaderValue(name, value);
        } else {
            underlying.add(name, value);
        }
        return this;
    }

    @Override
    public Http2Headers add(CharSequence name, Iterable<? extends CharSequence> values) {
        return unsupportedOperation("add(name, Iterable values)");
    }

    @Override
    public Http2Headers add(CharSequence name, CharSequence... values) {
        return unsupportedOperation("add(name, values...)");
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
        return unsupportedOperation("addInt");
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
        if (validateNames) {
            HTTP2_NAME_VALIDATOR.validateName(name);
        }
        if (validateValues) {
            HTTP2_VALUE_VALIDATOR.validate(value);
        }
        if (hasPseudoHeaderFormat(name)) {
            setPseudoHeaderValue(name, value);
        } else {
            underlying.set(name, value);
        }
        return this;
    }

    @Override
    public Http2Headers set(CharSequence name, Iterable<? extends CharSequence> values) {
        return unsupportedOperation("set(name, Iterable values)");
    }

    @Override
    public Http2Headers set(CharSequence name, CharSequence... values) {
        return unsupportedOperation("set(name, values...)");
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
        return unsupportedOperation("setInt");
    }

    @Override
    public Http2Headers setLong(CharSequence name, long value) {
        // This method is used in `DefaultHttp2ConnectionDecoder.onHeadersRead`
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
        if (hasPseudoHeaderFormat(name)) {
            boolean hadValue = getPseudoHeaderValue(name) != null;
            setPseudoHeaderValue(name, null);
            return hadValue;
        } else {
            return underlying.remove(name);
        }
    }

    @Override
    public Http2Headers clear() {
        return unsupportedOperation("clear");
    }

    private void maybeValidateValue(@Nullable CharSequence value) {
        if (validateValues && value != null) {
            HTTP2_VALUE_VALIDATOR.validate(value);
        }
    }

    // Helper methods for pseudo-header handling
    @Nullable
    private CharSequence getPseudoHeaderValue(CharSequence name) {
        if (METHOD.value().contentEquals(name)) {
            return method;
        } else if (PATH.value().contentEquals(name)) {
            return path;
        } else if (SCHEME.value().contentEquals(name)) {
            return scheme;
        } else if (AUTHORITY.value().contentEquals(name)) {
            return underlying.get(HOST);
        } else if (STATUS.value().contentEquals(name)) {
            return status;
        }
        return null;
    }

    private void setPseudoHeaderValue(CharSequence name, @Nullable CharSequence value) {
        if (METHOD.value().contentEquals(name)) {
            this.method = value;
        } else if (PATH.value().contentEquals(name)) {
            this.path = value;
        } else if (SCHEME.value().contentEquals(name)) {
            this.scheme = value;
        } else if (AUTHORITY.value().contentEquals(name)) {
            if (value != null) {
                underlying.set(HOST, value);
            } else {
                underlying.remove(HOST);
            }
        } else if (STATUS.value().contentEquals(name)) {
            this.status = value;
        } else if (PROTOCOL.value().contentEquals(name)) {
            // Defined in https://httpwg.org/specs/rfc8441.html but we don't use it, so we ignore it.
        } else {
            // Reproduce netty behavior for unknown headers.
            PlatformDependent.throwException(
                connectionError(PROTOCOL_ERROR, "invalid header name [%s]", name));
        }
    }

    /**
     * Get the underlying HttpHeaders without pseudo-headers, except the ':authority' header, which
     * is represented as the 'Host' header.
     */
    HttpHeaders httpHeaders() {
        return underlying;
    }

    private <T> T unsupportedOperation(String methodName) {
        throw new UnsupportedOperationException(methodName + " is not supported by " + getClass().getSimpleName());
    }

    private static boolean hasPseudoHeaderFormat(CharSequence name) {
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
