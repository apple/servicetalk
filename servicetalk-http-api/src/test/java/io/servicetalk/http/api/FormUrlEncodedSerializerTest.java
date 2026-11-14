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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.serializer.api.SerializerDeserializer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.FormUrlEncodedSerializer.formUrlEncodedSerializer;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormUrlEncodedSerializerTest {

    private static final SerializerDeserializer<Map<String, List<String>>> SERIALIZER =
            formUrlEncodedSerializer(UTF_8);

    @ParameterizedTest(name = "{displayName} [{index}] direct={0}")
    @ValueSource(booleans = {false, true})
    void deserializeConsumesReadableBytes(boolean direct) {
        final byte[] form = "a=1&b=2&b=3".getBytes(UTF_8);
        final Buffer buffer = DEFAULT_ALLOCATOR.newBuffer(form.length, direct).writeBytes(form);
        final int writerIndex = buffer.writerIndex();

        final Map<String, List<String>> result = SERIALIZER.deserialize(buffer, DEFAULT_ALLOCATOR);

        assertThat(result, is(aMapWithSize(2)));
        assertThat(result.get("a"), equalTo(singletonList("1")));
        assertThat(result.get("b"), equalTo(asList("2", "3")));
        assertThat(buffer.readerIndex(), is(writerIndex));
        assertThat(buffer.readableBytes(), is(0));
    }

    @ParameterizedTest(name = "{displayName} [{index}] direct={0}")
    @ValueSource(booleans = {false, true})
    void deserializeStartsAtReaderIndex(boolean direct) {
        final byte[] prefix = "skipped".getBytes(UTF_8);
        final byte[] form = "key=value".getBytes(UTF_8);
        final Buffer buffer = DEFAULT_ALLOCATOR.newBuffer(prefix.length + form.length, direct)
                .writeBytes(prefix).writeBytes(form);
        buffer.skipBytes(prefix.length);

        final Map<String, List<String>> result = SERIALIZER.deserialize(buffer, DEFAULT_ALLOCATOR);

        assertThat(result, is(aMapWithSize(1)));
        assertThat(result.get("key"), equalTo(singletonList("value")));
        assertThat(buffer.readerIndex(), is(prefix.length + form.length));
    }

    @ParameterizedTest(name = "{displayName} [{index}] direct={0}")
    @ValueSource(booleans = {false, true})
    void serializeThenDeserializeConsumesBuffer(boolean direct) {
        final Map<String, List<String>> form = new LinkedHashMap<>();
        form.put("name", singletonList("a b&c"));
        final Buffer buffer = DEFAULT_ALLOCATOR.newBuffer(32, direct);
        SERIALIZER.serialize(form, DEFAULT_ALLOCATOR, buffer);

        final Map<String, List<String>> result = SERIALIZER.deserialize(buffer, DEFAULT_ALLOCATOR);

        assertThat(result, equalTo(form));
        assertThat(buffer.readableBytes(), is(0));
    }

    @ParameterizedTest(name = "{displayName} [{index}] direct={0}")
    @ValueSource(booleans = {false, true})
    void deserializeFailureDoesNotConsumeBytes(boolean direct) {
        final byte[] form = "a=%zz".getBytes(UTF_8);
        final Buffer buffer = DEFAULT_ALLOCATOR.newBuffer(form.length, direct).writeBytes(form);
        final int readerIndex = buffer.readerIndex();

        assertThrows(IllegalArgumentException.class, () -> SERIALIZER.deserialize(buffer, DEFAULT_ALLOCATOR));
        assertThat(buffer.readerIndex(), is(readerIndex));
        assertThat(buffer.readableBytes(), is(form.length));
    }
}
