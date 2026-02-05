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

import io.netty.handler.codec.CharSequenceValueConverter;
import io.netty.handler.codec.DefaultHeaders;
import io.netty.handler.codec.Headers;
import io.netty.handler.codec.ValueConverter;
import io.netty.handler.codec.http.HttpHeaderValidationUtil;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.internal.PlatformDependent;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
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
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.hasPseudoHeaderFormat;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.isPseudoHeader;
import static io.netty.util.AsciiString.isUpperCase;
import static io.servicetalk.buffer.api.CharSequences.contentEquals;
import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.http.api.HeaderUtils.DEFAULT_HEADER_FILTER;
import static java.util.Objects.requireNonNull;

final class ServiceTalkHttp2Headers implements Http2Headers {

    private static final ValueConverter<CharSequence> VALUE_CONVERTER = CharSequenceValueConverter.INSTANCE;

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

    ServiceTalkHttp2Headers(HttpHeaders underlying, boolean lowercaseKeys,
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
        maybeValidatePseudoHeaderValue(value);
        this.method = value;
        return this;
    }

    @Override
    public Http2Headers scheme(@Nullable CharSequence value) {
        maybeValidatePseudoHeaderValue(value);
        this.scheme = value;
        return this;
    }

    @Override
    public Http2Headers authority(@Nullable CharSequence value) {
        maybeValidatePseudoHeaderValue(value);
        if (value != null) {
            underlying.set(HOST, value);
        } else {
            underlying.remove(HOST);
        }
        return this;
    }

    @Override
    public Http2Headers path(@Nullable CharSequence value) {
        maybeValidatePseudoHeaderValue(value);
        this.path = value;
        return this;
    }

    @Override
    public Http2Headers status(@Nullable CharSequence value) {
        maybeValidatePseudoHeaderValue(value);
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
        if (hasPseudoHeaderFormat(name)) {
            if (contentEquals(AUTHORITY.value(), name)) {
                // Authority is stored in the underlying map as the Host header.
                return caseInsensitive ? underlying.containsIgnoreCase(HOST, value) : underlying.contains(HOST, value);
            } else {
                CharSequence pseudoHeaderValue = get(name);
                return caseInsensitive ? contentEqualsIgnoreCase(pseudoHeaderValue, value) :
                        contentEquals(pseudoHeaderValue, value);
            }
        } else {
            return caseInsensitive ? underlying.containsIgnoreCase(name, value) : underlying.contains(name, value);
        }
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
                setPseudoHeaderValue(name, null, false);
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
        List<CharSequence> result = getAll(name);
        if (!result.isEmpty()) {
            remove(name);
        }
        return result;
    }

    @Override
    @Nullable
    public Boolean getBoolean(CharSequence name) {
        return convert(name, VALUE_CONVERTER::convertToBoolean);
    }

    @Override
    public boolean getBoolean(CharSequence name, boolean defaultValue) {
        Boolean result = getBoolean(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Byte getByte(CharSequence name) {
        return convert(name, VALUE_CONVERTER::convertToByte);
    }

    @Override
    public byte getByte(CharSequence name, byte defaultValue) {
        Byte result = getByte(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Character getChar(CharSequence name) {
        return convert(name, VALUE_CONVERTER::convertToChar);
    }

    @Override
    public char getChar(CharSequence name, char defaultValue) {
        Character result = getChar(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Short getShort(CharSequence name) {
        return convert(name, VALUE_CONVERTER::convertToShort);
    }

    @Override
    public short getShort(CharSequence name, short defaultValue) {
        Short result = getShort(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Integer getInt(CharSequence name) {
        return convert(name, VALUE_CONVERTER::convertToInt);
    }

    @Override
    public int getInt(CharSequence name, int defaultValue) {
        Integer result = getInt(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Long getLong(CharSequence name) {
        return convert(name, VALUE_CONVERTER::convertToLong);
    }

    @Override
    public long getLong(CharSequence name, long defaultValue) {
        Long result = getLong(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Float getFloat(CharSequence name) {
        return convert(name, VALUE_CONVERTER::convertToFloat);
    }

    @Override
    public float getFloat(CharSequence name, float defaultValue) {
        Float result = getFloat(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Double getDouble(CharSequence name) {
        return convert(name, VALUE_CONVERTER::convertToDouble);
    }

    @Override
    public double getDouble(CharSequence name, double defaultValue) {
        Double result = getDouble(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Long getTimeMillis(CharSequence name) {
        return convert(name, VALUE_CONVERTER::convertToTimeMillis);
    }

    @Override
    public long getTimeMillis(CharSequence name, long defaultValue) {
        Long result = getTimeMillis(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Boolean getBooleanAndRemove(CharSequence name) {
        return convertValue(getAndRemove(name), VALUE_CONVERTER::convertToBoolean);
    }

    @Override
    public boolean getBooleanAndRemove(CharSequence name, boolean defaultValue) {
        Boolean result = getBooleanAndRemove(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Byte getByteAndRemove(CharSequence name) {
        return convertValue(getAndRemove(name), VALUE_CONVERTER::convertToByte);
    }

    @Override
    public byte getByteAndRemove(CharSequence name, byte defaultValue) {
        Byte result = getByteAndRemove(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Character getCharAndRemove(CharSequence name) {
        return convertValue(getAndRemove(name), VALUE_CONVERTER::convertToChar);
    }

    @Override
    public char getCharAndRemove(CharSequence name, char defaultValue) {
        Character result = getCharAndRemove(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Short getShortAndRemove(CharSequence name) {
        return convertValue(getAndRemove(name), VALUE_CONVERTER::convertToShort);
    }

    @Override
    public short getShortAndRemove(CharSequence name, short defaultValue) {
        Short result = getShortAndRemove(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Integer getIntAndRemove(CharSequence name) {
        return convertValue(getAndRemove(name), VALUE_CONVERTER::convertToInt);
    }

    @Override
    public int getIntAndRemove(CharSequence name, int defaultValue) {
        Integer result = getIntAndRemove(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Long getLongAndRemove(CharSequence name) {
        return convertValue(getAndRemove(name), VALUE_CONVERTER::convertToLong);
    }

    @Override
    public long getLongAndRemove(CharSequence name, long defaultValue) {
        Long result = getLongAndRemove(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Float getFloatAndRemove(CharSequence name) {
        return convertValue(getAndRemove(name), VALUE_CONVERTER::convertToFloat);
    }

    @Override
    public float getFloatAndRemove(CharSequence name, float defaultValue) {
        Float result = getFloatAndRemove(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Double getDoubleAndRemove(CharSequence name) {
        return convertValue(getAndRemove(name), VALUE_CONVERTER::convertToDouble);
    }

    @Override
    public double getDoubleAndRemove(CharSequence name, double defaultValue) {
        Double result = getDoubleAndRemove(name);
        return result == null ? defaultValue : result;
    }

    @Override
    @Nullable
    public Long getTimeMillisAndRemove(CharSequence name) {
        return convertValue(getAndRemove(name), VALUE_CONVERTER::convertToTimeMillis);
    }

    @Override
    public long getTimeMillisAndRemove(CharSequence name, long defaultValue) {
        Long result = getTimeMillisAndRemove(name);
        return result == null ? defaultValue : result;
    }

    @Override
    public boolean contains(CharSequence name) {
        return hasPseudoHeaderFormat(name) ? getPseudoHeaderValue(name) != null : underlying.contains(name);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value) {
        return contains(name, value, false);
    }

    @Override
    public boolean containsObject(CharSequence name, Object value) {
        return contains(name, VALUE_CONVERTER.convertObject(value));
    }

    @Override
    public boolean containsBoolean(CharSequence name, boolean value) {
        return contains(name, VALUE_CONVERTER.convertBoolean(value));
    }

    @Override
    public boolean containsByte(CharSequence name, byte value) {
        return contains(name, VALUE_CONVERTER.convertByte(value));
    }

    @Override
    public boolean containsChar(CharSequence name, char value) {
        return contains(name, VALUE_CONVERTER.convertChar(value));
    }

    @Override
    public boolean containsShort(CharSequence name, short value) {
        return contains(name, VALUE_CONVERTER.convertShort(value));
    }

    @Override
    public boolean containsInt(CharSequence name, int value) {
        return contains(name, VALUE_CONVERTER.convertInt(value));
    }

    @Override
    public boolean containsLong(CharSequence name, long value) {
        return contains(name, VALUE_CONVERTER.convertLong(value));
    }

    @Override
    public boolean containsFloat(CharSequence name, float value) {
        return contains(name, VALUE_CONVERTER.convertFloat(value));
    }

    @Override
    public boolean containsDouble(CharSequence name, double value) {
        return contains(name, VALUE_CONVERTER.convertDouble(value));
    }

    @Override
    public boolean containsTimeMillis(CharSequence name, long value) {
        return contains(name, VALUE_CONVERTER.convertTimeMillis(value));
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
        Set<CharSequence> result = new HashSet<>();
        if (method != null) {
            result.add(METHOD.value());
        }
        if (path != null) {
            result.add(PATH.value());
        }
        if (scheme != null) {
            result.add(SCHEME.value());
        }
        if (status != null) {
            result.add(STATUS.value());
        }
        for (Map.Entry<CharSequence, ?> entry : underlying) {
            // We need to convert the 'Host' header to the ':authority' pseudo-header, if it exists.
            if (contentEquals(HOST, entry.getKey())) {
                result.add(AUTHORITY.value());
            } else {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    @Override
    public Http2Headers add(CharSequence name, CharSequence value) {
        if (validateNames) {
            HTTP2_NAME_VALIDATOR.validateName(name);
        }
        if (hasPseudoHeaderFormat(name)) {
            // For pseudo-headers, we set (replace) rather than add, as they should be unique
            setPseudoHeaderValue(name, value, true);
        } else {
            underlying.add(name, value);
        }
        return this;
    }

    @Override
    public Http2Headers add(CharSequence name, Iterable<? extends CharSequence> values) {
        for (CharSequence value : values) {
            add(name, value);
        }
        return this;
    }

    @Override
    public Http2Headers add(CharSequence name, CharSequence... values) {
        for (CharSequence value : values) {
            add(name, value);
        }
        return this;
    }

    @Override
    public Http2Headers addObject(CharSequence name, Object value) {
        return add(name, VALUE_CONVERTER.convertObject(value));
    }

    @Override
    public Http2Headers addObject(CharSequence name, Iterable<?> values) {
        for (Object value : values) {
            addObject(name, value);
        }
        return this;
    }

    @Override
    public Http2Headers addObject(CharSequence name, Object... values) {
        for (Object value : values) {
            addObject(name, value);
        }
        return this;
    }

    @Override
    public Http2Headers addBoolean(CharSequence name, boolean value) {
        return add(name, VALUE_CONVERTER.convertBoolean(value));
    }

    @Override
    public Http2Headers addByte(CharSequence name, byte value) {
        return add(name, VALUE_CONVERTER.convertByte(value));
    }

    @Override
    public Http2Headers addChar(CharSequence name, char value) {
        return add(name, VALUE_CONVERTER.convertChar(value));
    }

    @Override
    public Http2Headers addShort(CharSequence name, short value) {
        return add(name, VALUE_CONVERTER.convertShort(value));
    }

    @Override
    public Http2Headers addInt(CharSequence name, int value) {
        return add(name, VALUE_CONVERTER.convertInt(value));
    }

    @Override
    public Http2Headers addLong(CharSequence name, long value) {
        return add(name, VALUE_CONVERTER.convertLong(value));
    }

    @Override
    public Http2Headers addFloat(CharSequence name, float value) {
        return add(name, VALUE_CONVERTER.convertFloat(value));
    }

    @Override
    public Http2Headers addDouble(CharSequence name, double value) {
        return add(name, VALUE_CONVERTER.convertDouble(value));
    }

    @Override
    public Http2Headers addTimeMillis(CharSequence name, long value) {
        return add(name, VALUE_CONVERTER.convertTimeMillis(value));
    }

    @Override
    public Http2Headers add(Headers<? extends CharSequence, ? extends CharSequence, ?> headers) {
        if (headers == this) {
            throw new IllegalArgumentException("can't add to itself.");
        }
        for (Map.Entry<? extends CharSequence, ? extends CharSequence> entry : headers) {
            add(entry.getKey(), entry.getValue());
        }
        return this;
    }

    @Override
    public Http2Headers set(CharSequence name, CharSequence value) {
        if (validateNames) {
            HTTP2_NAME_VALIDATOR.validateName(name);
        }
        if (hasPseudoHeaderFormat(name)) {
            setPseudoHeaderValue(name, value, false);
        } else {
            underlying.set(name, value);
        }
        return this;
    }

    @Override
    public Http2Headers set(CharSequence name, Iterable<? extends CharSequence> values) {
        remove(name);
        return add(name, values);
    }

    @Override
    public Http2Headers set(CharSequence name, CharSequence... values) {
        remove(name);
        return add(name, values);
    }

    @Override
    public Http2Headers setObject(CharSequence name, Object value) {
        return set(name, VALUE_CONVERTER.convertObject(value));
    }

    @Override
    public Http2Headers setObject(CharSequence name, Iterable<?> values) {
        remove(name);
        for (Object o : values) {
            addObject(name, o);
        }
        return this;
    }

    @Override
    public Http2Headers setObject(CharSequence name, Object... values) {
        remove(name);
        for (Object o : values) {
            addObject(name, o);
        }
        return this;
    }

    @Override
    public Http2Headers setBoolean(CharSequence name, boolean value) {
        return set(name, VALUE_CONVERTER.convertBoolean(value));
    }

    @Override
    public Http2Headers setByte(CharSequence name, byte value) {
        return set(name, VALUE_CONVERTER.convertByte(value));
    }

    @Override
    public Http2Headers setChar(CharSequence name, char value) {
        return set(name, VALUE_CONVERTER.convertChar(value));
    }

    @Override
    public Http2Headers setShort(CharSequence name, short value) {
        return set(name, VALUE_CONVERTER.convertShort(value));
    }

    @Override
    public Http2Headers setInt(CharSequence name, int value) {
        return set(name, VALUE_CONVERTER.convertInt(value));
    }

    @Override
    public Http2Headers setLong(CharSequence name, long value) {
        return set(name, VALUE_CONVERTER.convertLong(value));
    }

    @Override
    public Http2Headers setFloat(CharSequence name, float value) {
        return set(name, VALUE_CONVERTER.convertFloat(value));
    }

    @Override
    public Http2Headers setDouble(CharSequence name, double value) {
        return set(name, VALUE_CONVERTER.convertDouble(value));
    }

    @Override
    public Http2Headers setTimeMillis(CharSequence name, long value) {
        return set(name, VALUE_CONVERTER.convertTimeMillis(value));
    }

    @Override
    public Http2Headers set(Headers<? extends CharSequence, ? extends CharSequence, ?> headers) {
        if (this != headers) {
            clear();
            add(headers);
        }
        return this;
    }

    @Override
    public Http2Headers setAll(Headers<? extends CharSequence, ? extends CharSequence, ?> headers) {
        if (headers != this) {
            for (Map.Entry<? extends CharSequence, ? extends CharSequence> entry : headers) {
                remove(entry.getKey());
            }
            add(headers);
        }
        return this;
    }

    @Override
    public boolean remove(CharSequence name) {
        if (hasPseudoHeaderFormat(name)) {
            boolean hadValue = getPseudoHeaderValue(name) != null;
            setPseudoHeaderValue(name, null, false);
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName())
                .append("{ pseudo-headers: [");

        boolean firstPseudo = true;

        // Add pseudo-headers in HTTP/2 order: method, scheme, authority, path, status
        firstPseudo = appendPseudoHeader(sb, METHOD.value(), method, firstPseudo);
        firstPseudo = appendPseudoHeader(sb, SCHEME.value(), scheme, firstPseudo);
        firstPseudo = appendPseudoHeader(sb, AUTHORITY.value(), underlying.get(HOST), firstPseudo);
        firstPseudo = appendPseudoHeader(sb, PATH.value(), path, firstPseudo);
        appendPseudoHeader(sb, STATUS.value(), status, firstPseudo);

        sb.append("], standard-headers: ")
                .append(underlying)
                .append('}');
        return sb.toString();
    }

    private static boolean appendPseudoHeader(StringBuilder sb, CharSequence key,
                                              @Nullable CharSequence value, boolean firstPseudoHeader) {
        if (value != null) {
            if (!firstPseudoHeader) {
                sb.append(", ");
            }
            sb.append(key).append(": ")
                    .append(DEFAULT_HEADER_FILTER.apply(key, value));
            return false;
        }
        return firstPseudoHeader;
    }

    /**
     * Get the underlying HttpHeaders without pseudo-headers, except the ':authority' header, which
     * is represented as the 'Host' header.
     */
    HttpHeaders httpHeaders() {
        return underlying;
    }

    private void maybeValidatePseudoHeaderValue(@Nullable CharSequence value) {
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

    private void setPseudoHeaderValue(CharSequence name, @Nullable CharSequence value, boolean forAdd) {
        if (METHOD.value().contentEquals(name)) {
            checkNotDuplicate(name, value, this.method, forAdd);
            method(value);
        } else if (PATH.value().contentEquals(name)) {
            checkNotDuplicate(name, value, this.path, forAdd);
            path(value);
        } else if (SCHEME.value().contentEquals(name)) {
            checkNotDuplicate(name, value, this.scheme, forAdd);
            scheme(value);
        } else if (AUTHORITY.value().contentEquals(name)) {
            if (value != null) {
                if (forAdd && validateNames && underlying.contains(HOST)) {
                    throwDuplicateHeader(name);
                }
                underlying.set(HOST, value);
            } else {
                underlying.remove(HOST);
            }
        } else if (STATUS.value().contentEquals(name)) {
            checkNotDuplicate(name, value, this.status, forAdd);
            status(value);
        } else if (PROTOCOL.value().contentEquals(name)) {
            // Defined in https://httpwg.org/specs/rfc8441.html but we don't use it, so we ignore it.
        } else {
            // Reproduce netty behavior for unknown headers.
            PlatformDependent.throwException(
                connectionError(PROTOCOL_ERROR, "invalid header name [%s]", name));
        }
    }

    private void checkNotDuplicate(CharSequence name, @Nullable CharSequence value,
                                   @Nullable CharSequence existingValue, boolean forAdd) {
        if (forAdd && value != null && existingValue != null && validateNames) {
            throwDuplicateHeader(name);
        }
    }

    private static void throwDuplicateHeader(CharSequence name) {
        PlatformDependent.throwException(connectionError(
                PROTOCOL_ERROR, "Duplicate HTTP/2 pseudo-header '%s' encountered.", name));
    }

    @Nullable
    private <T> T convert(CharSequence name, Function<CharSequence, T> converter) {
        return convertValue(get(name), converter);
    }

    @Nullable
    private static <T> T convertValue(@Nullable CharSequence value, Function<? super CharSequence, T> converter) {
        if (value == null) {
            return null;
        }
        try {
            return converter.apply(value);
        } catch (RuntimeException ex) {
            return null;
        }
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
            if (isUpperCase(value.charAt(i))) {
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
