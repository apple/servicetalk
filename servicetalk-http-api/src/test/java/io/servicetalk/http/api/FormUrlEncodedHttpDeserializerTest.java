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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.serialization.api.SerializationException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.rules.ExpectedException.none;

public class FormUrlEncodedHttpDeserializerTest {

    @Rule
    public final ExpectedException expectedException = none();

    @Test
    public void formParametersAreDeserialized() {
        final FormUrlEncodedHttpDeserializer deserializer = FormUrlEncodedHttpDeserializer.UTF8;

        final HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        headers.set(CONTENT_TYPE, "application/x-www-form-urlencoded; charset=UTF-8");
        final String formParameters = "escape%26this%3D=and%26this%25&param2=bar+&param2=foo%20&emptyParam=";

        final Map<String, List<String>> deserialized = deserializer.deserialize(headers, toBuffer(formParameters));

        assertEquals("Unexpected parameter value.",
                singletonList("and&this%"),
                deserialized.get("escape&this="));

        assertEquals("Unexpected parameter value count.", 2, deserialized.get("param2").size());
        assertEquals("Unexpected parameter value.", "bar ", deserialized.get("param2").get(0));
        assertEquals("Unexpected parameter value.", "foo ", deserialized.get("param2").get(1));

        assertEquals("Unexpected parameter value count.", 1, deserialized.get("emptyParam").size());
        assertEquals("Unexpected parameter value.", "", deserialized.get("emptyParam").get(0));
    }

    @Test
    public void deserializeEmptyBuffer() {
        final FormUrlEncodedHttpDeserializer deserializer = FormUrlEncodedHttpDeserializer.UTF8;

        final HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        headers.set(CONTENT_TYPE, "application/x-www-form-urlencoded; charset=UTF-8");

        final Map<String, List<String>> deserialized = deserializer.deserialize(headers, EMPTY_BUFFER);

        assertEquals("Unexpected parameter count", 0, deserialized.size());
    }

    @Test
    public void invalidContentTypeThrows() {
        final FormUrlEncodedHttpDeserializer deserializer = FormUrlEncodedHttpDeserializer.UTF8;

        final HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        final String invalidContentType = "invalid/content/type";
        headers.set(CONTENT_TYPE, invalidContentType);

        expectedException.expect(instanceOf(SerializationException.class));
        expectedException.expectMessage(containsString(invalidContentType));

        deserializer.deserialize(headers, EMPTY_BUFFER);
    }

    @Test
    public void invalidContentTypeThrowsAndMasksAdditionalHeadersValues() {
        final FormUrlEncodedHttpDeserializer deserializer = FormUrlEncodedHttpDeserializer.UTF8;

        final HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        final String invalidContentType = "invalid/content/type";
        final String someHost = "some/host";
        headers.set(CONTENT_TYPE, invalidContentType);
        headers.set(HttpHeaderNames.HOST, someHost);

        expectedException.expect(instanceOf(SerializationException.class));
        expectedException.expectMessage(containsString(invalidContentType));
        expectedException.expectMessage(containsString("<filtered>"));
        expectedException.expectMessage(not(containsString(someHost)));

        deserializer.deserialize(headers, EMPTY_BUFFER);
    }

    @Test
    public void missingContentTypeThrows() {
        final FormUrlEncodedHttpDeserializer deserializer = FormUrlEncodedHttpDeserializer.UTF8;

        final HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();

        expectedException.expect(instanceOf(SerializationException.class));

        deserializer.deserialize(headers, EMPTY_BUFFER);
    }

    @Test
    public void iterableCloseIsPropagated() throws Exception {
        final FormUrlEncodedHttpDeserializer deserializer = FormUrlEncodedHttpDeserializer.UTF8;
        final HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        headers.set(CONTENT_TYPE, "application/x-www-form-urlencoded; charset=UTF-8");

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
                .deserialize(headers, formParametersIterable);
        deserialized.iterator().close();

        assertTrue("BlockingIterable was not closed.", isClosed.get());
    }

    private Buffer toBuffer(final String value) {
        return DEFAULT_ALLOCATOR.fromUtf8(value);
    }
}
