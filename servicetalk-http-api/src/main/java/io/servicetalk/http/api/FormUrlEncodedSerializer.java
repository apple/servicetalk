/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.serializer.api.SerializationException;
import io.servicetalk.serializer.api.SerializerDeserializer;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.DefaultHttpRequestMetaData.DEFAULT_MAX_QUERY_PARAMS;
import static io.servicetalk.http.api.UriUtils.decodeQueryParams;
import static io.servicetalk.utils.internal.CharsetUtils.standardCharsets;
import static java.util.Collections.emptyMap;

final class FormUrlEncodedSerializer implements SerializerDeserializer<Map<String, List<String>>> {
    private static final HashMap<Charset, byte[]> CONTINUATIONS_SEPARATORS;
    private static final HashMap<Charset, byte[]> KEYVALUE_SEPARATORS;
    static {
        Collection<Charset> charsets = standardCharsets();
        final int size = charsets.size();
        CONTINUATIONS_SEPARATORS = new HashMap<>(size);
        KEYVALUE_SEPARATORS = new HashMap<>(size);
        for (Charset charset : charsets) {
            final byte[] continuation;
            final byte[] keyvalue;
            try {
                continuation = "&".getBytes(charset);
                keyvalue = "=".getBytes(charset);
            } catch (Throwable cause) {
                continue;
            }
            CONTINUATIONS_SEPARATORS.put(charset, continuation);
            KEYVALUE_SEPARATORS.put(charset, keyvalue);
        }
    }

    private final Charset charset;
    private final byte[] continuationSeparator;
    private final byte[] keyValueSeparator;

    FormUrlEncodedSerializer(final Charset charset) {
        this.charset = charset;
        byte[] continuationSeparator = CONTINUATIONS_SEPARATORS.get(charset);
        if (continuationSeparator == null) {
            this.continuationSeparator = "&".getBytes(charset);
            this.keyValueSeparator = "=".getBytes(charset);
        } else {
            this.continuationSeparator = continuationSeparator;
            this.keyValueSeparator = KEYVALUE_SEPARATORS.get(charset);
        }
    }

    @Override
    public Map<String, List<String>> deserialize(final Buffer serializedData, final BufferAllocator allocator) {
        return deserialize(serializedData, charset);
    }

    @Override
    public Buffer serialize(Map<String, List<String>> toSerialize, BufferAllocator allocator) {
        Buffer buffer = allocator.newBuffer(toSerialize.size() * 10);
        serialize(toSerialize, allocator, buffer);
        return buffer;
    }

    @Override
    public void serialize(final Map<String, List<String>> toSerialize, final BufferAllocator allocator,
                          final Buffer buffer) {
        // Null values may be omitted
        // https://tools.ietf.org/html/rfc1866#section-8.2
        boolean needContinuation = false;
        for (Entry<String, List<String>> entry : toSerialize.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) {
                throw new SerializationException("Null or empty keys are not supported " +
                        "for x-www-form-urlencoded params");
            }

            List<String> values = entry.getValue();
            if (values != null) {
                for (String value : values) {
                    if (value != null) {
                        if (needContinuation) {
                            buffer.writeBytes(continuationSeparator);
                        } else {
                            needContinuation = true;
                        }
                        buffer.writeBytes(urlEncode(key, charset));
                        buffer.writeBytes(keyValueSeparator);
                        buffer.writeBytes(urlEncode(value, charset));
                    }
                }
            }
        }
    }

    static Map<String, List<String>> deserialize(@Nullable final Buffer buffer, Charset charset) {
        if (buffer == null || buffer.readableBytes() == 0) {
            return emptyMap();
        }
        return decodeQueryParams(buffer.toString(charset), charset, DEFAULT_MAX_QUERY_PARAMS, (value, charset2) -> {
            try {
                return URLDecoder.decode(value, charset2.name());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException("URLDecoder failed to find Charset: " + charset2, e);
            }
        });
    }

    private static byte[] urlEncode(final String value, Charset charset) {
        try {
            return URLEncoder.encode(value, charset.name()).getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
