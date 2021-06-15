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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.BlockingIterables;
import io.servicetalk.serialization.api.SerializationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.StreamSupport;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.EMPTY_MAP;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormUrlEncodedHttpSerializerTest {

    private static final FormUrlEncodedHttpSerializer SERIALIZER = FormUrlEncodedHttpSerializer.UTF8;

    private static final Map<String, List<String>> MAP_A = new HashMap<>();
    private static final Map<String, List<String>> MAP_B = new HashMap<>();
    static {
        // Populate maps
        MAP_A.put("key1", singletonList("val1"));
        MAP_A.put("key2", singletonList("val2"));

        MAP_B.put("key3", singletonList("val3"));
        MAP_B.put("key5", singletonList(null));
        MAP_B.put("key6", null);
        MAP_B.put("key7", emptyList());
        MAP_B.put("key4", singletonList("val4"));
    }

    private static final Map<String, List<String>> MAP_NULL_KEY = new HashMap<>();
    static {
        MAP_NULL_KEY.put(null, singletonList("val1"));
    }

    private HttpHeaders headers;

    @BeforeEach
    void setup() {
        headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
    }

    @Test
    void formParametersAreEncoded() {
        final Map<String, List<String>> formParameters = new HashMap<>();
        formParameters.put("emptyParam", singletonList(""));
        formParameters.put("escape&this=", singletonList("and&this%"));
        formParameters.put("param2", asList("foo", "bar"));

        final Buffer serialized = SERIALIZER.serialize(headers, formParameters, DEFAULT_ALLOCATOR);

        assertEquals("emptyParam=&escape%26this%3D=and%26this%25&param2=foo&param2=bar",
                serialized.toString(UTF_8), "Unexpected serialized content.");
        assertTrue(headers.contains(CONTENT_TYPE, "application/x-www-form-urlencoded; charset=UTF-8"),
                "Unexpected content type.");
    }

    @Test
    void serializeEmptyMap() {
        final Buffer serialized = SERIALIZER.serialize(headers, EMPTY_MAP, DEFAULT_ALLOCATOR);
        assertEquals(0, serialized.readableBytes(), "Unexpected buffer length.");
        assertTrue(headers.contains(CONTENT_TYPE, "application/x-www-form-urlencoded; charset=UTF-8"),
                "Unexpected content type.");
    }

    @Test
    void serializeMapWithNullKey() {
        assertThrows(SerializationException.class,
                () -> SERIALIZER.serialize(headers, MAP_NULL_KEY, DEFAULT_ALLOCATOR));
    }

    @Test
    void serializeStreamingMultipleParts() throws Exception {
        final Publisher<Buffer> serialized = SERIALIZER.serialize(headers,
                Publisher.from(MAP_A, MAP_B), DEFAULT_ALLOCATOR);
        String queryStr = queryStringFromPublisher(serialized);

        assertEquals("key1=val1&key2=val2&key3=val3&key4=val4", queryStr, "Unexpected serialized content.");
    }

    @Test
    void serializeStreamingMultiplePartsWithEmptySecondMap() throws Exception {
        String queryStr = queryStringFromPublisher(SERIALIZER.serialize(headers,
                Publisher.from(MAP_A, EMPTY_MAP), DEFAULT_ALLOCATOR));

        assertEquals("key1=val1&key2=val2", queryStr, "Unexpected serialized content.");
    }

    @Test
    void serializeStreamingMultiplePartsWithEmptyFirstMap() throws Exception {
        String queryStr = queryStringFromPublisher(SERIALIZER.serialize(headers,
                Publisher.from(EMPTY_MAP, MAP_A), DEFAULT_ALLOCATOR));

        assertEquals("key1=val1&key2=val2", queryStr, "Unexpected serialized content.");
    }

    @Test
    void serializeStreamingMultiplePartsWithEmptyAllMaps() throws Exception {
        String queryStr = queryStringFromPublisher(SERIALIZER.serialize(headers,
                Publisher.from(EMPTY_MAP, EMPTY_MAP), DEFAULT_ALLOCATOR));

        assertEquals("", queryStr, "Unexpected serialized content.");
    }

    @Test
    void serializeStreamingMultiplePartsWithMixOfEmptyAndNotEmptyMaps() throws Exception {
        String queryStr = queryStringFromPublisher(SERIALIZER.serialize(headers,
                Publisher.from(MAP_A, EMPTY_MAP, EMPTY_MAP, MAP_B), DEFAULT_ALLOCATOR));

        assertEquals("key1=val1&key2=val2&key3=val3&key4=val4", queryStr, "Unexpected serialized content.");
    }

    @Test
    void serializeStreamingMultiplePartsWithMixOfEmptyAndNotEmptyMapsAndResubscribe() throws Exception {
        Publisher<Buffer> pub = SERIALIZER.serialize(headers,
                Publisher.from(MAP_A, EMPTY_MAP, EMPTY_MAP, MAP_B), DEFAULT_ALLOCATOR);

        String queryStr = queryStringFromPublisher(pub);
        assertEquals("key1=val1&key2=val2&key3=val3&key4=val4", queryStr, "Unexpected serialized content.");

        queryStr = queryStringFromPublisher(pub);
        assertEquals("key1=val1&key2=val2&key3=val3&key4=val4", queryStr, "Unexpected serialized content.");
    }

    @Test
    void serializeBlockingItMultipleParts() {
        String queryStr = queryStringFromBlockingIterable(SERIALIZER.serialize(headers,
                BlockingIterables.from(asList(MAP_A, MAP_B)), DEFAULT_ALLOCATOR));

        assertEquals("key1=val1&key2=val2&key3=val3&key4=val4", queryStr, "Unexpected serialized content.");
    }

    @Test
    void serializeBlockingItMultiplePartsWithEmptySecondMap() {
        String queryStr = queryStringFromBlockingIterable(SERIALIZER.serialize(headers,
                BlockingIterables.from(asList(MAP_A, EMPTY_MAP)), DEFAULT_ALLOCATOR));

        assertEquals("key1=val1&key2=val2", queryStr, "Unexpected serialized content.");
    }

    @Test
    void serializeBlockingItMultiplePartsWithEmptyFirstMap() {
        String queryStr = queryStringFromBlockingIterable(SERIALIZER.serialize(headers,
                BlockingIterables.from(asList(EMPTY_MAP, MAP_A)), DEFAULT_ALLOCATOR));

        assertEquals("key1=val1&key2=val2", queryStr, "Unexpected serialized content.");
    }

    @Test
    void serializeBlockingItMultiplePartsWithEmptyAllMaps() {
        String queryStr = queryStringFromBlockingIterable(SERIALIZER.serialize(headers,
                BlockingIterables.from(asList(EMPTY_MAP, EMPTY_MAP)), DEFAULT_ALLOCATOR));

        assertEquals("", queryStr, "Unexpected serialized content.");
    }

    @Test
    void serializeBlockingItMultiplePartsWithMixOfEmptyAndNotEmptyMaps() {
        String queryStr = queryStringFromBlockingIterable(SERIALIZER.serialize(headers,
                BlockingIterables.from(asList(MAP_A, EMPTY_MAP, EMPTY_MAP, MAP_B)), DEFAULT_ALLOCATOR));

        assertEquals("key1=val1&key2=val2&key3=val3&key4=val4", queryStr, "Unexpected serialized content.");
    }

    @Test
    void iterableCloseIsPropagated() throws Exception {
        final AtomicBoolean isClosed = new AtomicBoolean(false);

        final BlockingIterable<Map<String, List<String>>> formParametersIterable =
                () -> new BlockingIterator<Map<String, List<String>>>() {
                    @Override
                    public boolean hasNext(final long timeout, final TimeUnit unit) {
                        return false;
                    }

                    @Override
                    public Map<String, List<String>> next(final long timeout, final TimeUnit unit) {
                        return emptyMap();
                    }

                    @Override
                    public Map<String, List<String>> next() {
                        return emptyMap();
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

        final BlockingIterable<Buffer> serialized = SERIALIZER.serialize(headers,
                formParametersIterable, DEFAULT_ALLOCATOR);
        serialized.iterator().close();

        assertTrue(isClosed.get());
        assertTrue(headers.contains(CONTENT_TYPE,
                "application/x-www-form-urlencoded; charset=UTF-8"), "Unexpected content type.");
    }

    private String queryStringFromPublisher(Publisher<Buffer> serialized) throws Exception {
        return serialized.collect(StringBuilder::new, (builder, buffer) -> {
            builder.append(buffer.toString(UTF_8));
            return builder;
        }).toFuture().get().toString();
    }

    private String queryStringFromBlockingIterable(BlockingIterable<Buffer> serialized) {
        return StreamSupport.stream(serialized.spliterator(), false)
                .map((b) -> b.toString(UTF_8))
                .reduce("", (partial, value) -> partial + value);
    }
}
