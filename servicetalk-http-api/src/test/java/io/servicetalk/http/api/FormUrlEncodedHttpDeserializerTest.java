/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.serialization.api.SerializationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormUrlEncodedHttpDeserializerTest {

    private static final FormUrlEncodedHttpDeserializer deserializer = FormUrlEncodedHttpDeserializer.UTF8;

    private static final HttpHeaders FE_HEADERS = DefaultHttpHeadersFactory.INSTANCE.newHeaders()
            .set(CONTENT_TYPE, "application/x-www-form-urlencoded; charset=UTF-8");

    @Test
    void formParametersAreDeserialized() {
        final String formParameters = "escape%26this%3D=and%26this%25&param2=bar+&param2=foo%20&emptyParam=";
        final Map<String, List<String>> deserialized = deserializer.deserialize(FE_HEADERS, toBuffer(formParameters));

        assertEquals(singletonList("and&this%"),
                deserialized.get("escape&this="), "Unexpected parameter value.");

        assertEquals(2, deserialized.get("param2").size(), "Unexpected parameter value count.");
        assertEquals("bar ", deserialized.get("param2").get(0), "Unexpected parameter value.");
        assertEquals("foo ", deserialized.get("param2").get(1), "Unexpected parameter value.");

        assertEquals(1, deserialized.get("emptyParam").size(), "Unexpected parameter value count.");
        assertEquals("", deserialized.get("emptyParam").get(0), "Unexpected parameter value.");
    }

    @Test
    void deserializeEmptyBuffer() {
        final Map<String, List<String>> deserialized = deserializer.deserialize(FE_HEADERS, EMPTY_BUFFER);
        assertEquals(0, deserialized.size(), "Unexpected parameter count");
    }

    @Test
    void invalidContentTypeThrows() {
        final String invalidContentType = "invalid/content/type";
        final HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders()
                .set(CONTENT_TYPE, invalidContentType);

        SerializationException e = assertThrows(SerializationException.class,
                                                () -> deserializer.deserialize(headers, EMPTY_BUFFER));
        assertThat(e.getMessage(), containsString(invalidContentType));
    }

    @Test
    void invalidContentTypeThrowsAndMasksAdditionalHeadersValues() {
        final String invalidContentType = "invalid/content/type";
        final String someHost = "some/host";
        final HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders()
                .set(CONTENT_TYPE, invalidContentType)
                .set(HttpHeaderNames.HOST, someHost);

        SerializationException e = assertThrows(SerializationException.class,
                                                () -> deserializer.deserialize(headers, EMPTY_BUFFER));
        assertThat(e.getMessage(), containsString(invalidContentType));
        assertThat(e.getMessage(), containsString("<filtered>"));
        assertThat(e.getMessage(), not(containsString(someHost)));
    }

    @Test
    void missingContentTypeThrows() {
        final HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        assertThrows(SerializationException.class,
                () -> deserializer.deserialize(headers, EMPTY_BUFFER));
    }

    @Test
    void iterableCloseIsPropagated() throws Exception {
        final AtomicBoolean isClosed = new AtomicBoolean(false);

        final BlockingIterable<Buffer> formParametersIterable = () -> new BlockingIterator<Buffer>() {
            @Override
            public boolean hasNext(final long timeout, final TimeUnit unit) {
                return false;
            }

            @Override
            public Buffer next(final long timeout, final TimeUnit unit) {
                return EMPTY_BUFFER;
            }

            @Override
            public Buffer next() {
                return EMPTY_BUFFER;
            }

            @Override
            public void close() {
                isClosed.set(true);
            }

            @Override
            public boolean hasNext() {
                return false;
            }
        };

        final BlockingIterable<Map<String, List<String>>> deserialized = deserializer
                .deserialize(FE_HEADERS, formParametersIterable);
        deserialized.iterator().close();

        assertTrue(isClosed.get(), "BlockingIterable was not closed.");
    }

    @ParameterizedTest(name = "{displayName} [{index}] paramIsNull={0}")
    @ValueSource(booleans = { true, false })
    void deserializesNullOrEmptyValues(boolean paramIsNull) {
        final String separator = paramIsNull ? "" : "=";
        final String formParameters = String.format("key1%s&key2%s&key2%s", separator, separator, separator);

        final Map<String, List<String>> deserialized = deserializer.deserialize(FE_HEADERS, toBuffer(formParameters));
        assertThat(deserialized.size(), is(2));

        assertThat(deserialized.get("key1").size(), is(1));
        assertThat(deserialized.get("key2").size(), is(2));

        assertThat(deserialized.get("key1").get(0), paramIsNull ? nullValue() : emptyString());
        assertThat(deserialized.get("key2").get(0), paramIsNull ? nullValue() : emptyString());
        assertThat(deserialized.get("key2").get(1), paramIsNull ? nullValue() : emptyString());
    }

    private Buffer toBuffer(final String value) {
        return DEFAULT_ALLOCATOR.fromUtf8(value);
    }
}
