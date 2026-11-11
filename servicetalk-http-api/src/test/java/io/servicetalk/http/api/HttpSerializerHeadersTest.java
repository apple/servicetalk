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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.serializer.utils.FixedLengthStreamingSerializer;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpApiConversions.mayHaveTrailers;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.serializer.utils.StringSerializer.stringSerializer;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * A serializer may update the headers, which can be sent or checked before the payload body is subscribed, so every
 * method that takes a serializer must apply it before returning.
 */
class HttpSerializerHeadersTest {

    private static final String MARKER = "x-serialized";
    private static final Consumer<HttpHeaders> MARK = headers -> headers.set(MARKER, "true");

    private static final HttpStreamingSerializerDeserializer<String> STREAMING = HttpSerializers.streamingSerializer(
            new FixedLengthStreamingSerializer<>(stringSerializer(UTF_8), String::length), MARK, headers -> true);
    private static final HttpSerializerDeserializer<String> AGGREGATED =
            HttpSerializers.serializer(stringSerializer(UTF_8), MARK, headers -> true);
    @SuppressWarnings("deprecation")
    private static final HttpSerializer<String> DEPRECATED_SERIALIZER =
            HttpSerializationProviders.textSerializer(UTF_8, MARK);
    @SuppressWarnings("deprecation")
    private static final HttpDeserializer<String> DEPRECATED_DESERIALIZER =
            HttpSerializationProviders.textDeserializer(UTF_8, headers -> true);

    private static final HttpMessageBodyIterable<String> NOT_ITERATED = () -> {
        throw new AssertionError("message body is not expected to be iterated");
    };
    private static final Function<Publisher<Buffer>, Publisher<String>> TO_STRINGS = buffers -> from("a");
    private static final Function<BlockingIterable<Buffer>, BlockingIterable<String>> TO_STRINGS_BLOCKING =
            buffers -> from("a").toIterable();

    private static final StreamingHttpRequestResponseFactory FACTORY = new DefaultStreamingHttpRequestResponseFactory(
            DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);

    @FunctionalInterface
    private interface SerializerCall<M> {
        HttpHeaders apply(M message) throws Exception;
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0} mayHaveTrailers={1}")
    @MethodSource("requestCalls")
    void requestSerializerUpdatesHeadersBeforeReturning(SerializerCall<StreamingHttpRequest> call,
                                                        boolean mayHaveTrailers) throws Exception {
        StreamingHttpRequest request = FACTORY.post("/");
        if (mayHaveTrailers) {
            request = request.transform(new StatelessTrailersTransformer<>());
        }
        assertThat(mayHaveTrailers(request), is(mayHaveTrailers));
        assertThat(call.apply(request).get(MARKER), is(notNullValue()));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0} mayHaveTrailers={1}")
    @MethodSource("responseCalls")
    void responseSerializerUpdatesHeadersBeforeReturning(SerializerCall<StreamingHttpResponse> call,
                                                         boolean mayHaveTrailers) throws Exception {
        StreamingHttpResponse response = FACTORY.ok();
        if (mayHaveTrailers) {
            response = response.transform(new StatelessTrailersTransformer<>());
        }
        assertThat(mayHaveTrailers(response), is(mayHaveTrailers));
        assertThat(call.apply(response).get(MARKER), is(notNullValue()));
    }

    @SuppressWarnings({"unused", "deprecation"})
    private static Stream<Arguments> requestCalls() {
        return Stream.concat(withAndWithoutTrailers(Arrays.<Named<SerializerCall<StreamingHttpRequest>>>asList(
                Named.of("streaming payloadBody(Publisher, HttpStreamingSerializer)",
                        r -> r.payloadBody(from("a"), STREAMING).headers()),
                Named.of("streaming transformPayloadBody(Function, HttpStreamingSerializer)",
                        r -> r.transformPayloadBody(TO_STRINGS, STREAMING).headers()),
                Named.of("streaming transformPayloadBody(Function, HttpStreamingDeserializer, HttpStreamingSerializer)",
                        r -> r.transformPayloadBody(strings -> strings, STREAMING, STREAMING).headers()),
                Named.of("streaming payloadBody(Publisher, HttpSerializer)",
                        r -> r.payloadBody(from("a"), DEPRECATED_SERIALIZER).headers()),
                Named.of("streaming transformPayloadBody(Function, HttpSerializer)",
                        r -> r.transformPayloadBody(TO_STRINGS, DEPRECATED_SERIALIZER).headers()),
                Named.of("streaming transformPayloadBody(Function, HttpDeserializer, HttpSerializer)",
                        r -> r.transformPayloadBody(strings -> strings, DEPRECATED_DESERIALIZER,
                                DEPRECATED_SERIALIZER).headers()),
                Named.of("blocking payloadBody(Iterable, HttpStreamingSerializer)",
                        r -> r.toBlockingStreamingRequest().payloadBody(singletonList("a"), STREAMING).headers()),
                Named.of("blocking messageBody(HttpMessageBodyIterable, HttpStreamingSerializer)",
                        r -> r.toBlockingStreamingRequest().messageBody(NOT_ITERATED, STREAMING).headers()),
                Named.of("blocking payloadBody(Iterable, HttpSerializer)",
                        r -> r.toBlockingStreamingRequest().payloadBody(singletonList("a"), DEPRECATED_SERIALIZER)
                                .headers()),
                Named.of("blocking transformPayloadBody(Function, HttpSerializer)",
                        r -> r.toBlockingStreamingRequest()
                                .transformPayloadBody(TO_STRINGS_BLOCKING, DEPRECATED_SERIALIZER).headers()),
                Named.of("blocking transformPayloadBody(Function, HttpDeserializer, HttpSerializer)",
                        r -> r.toBlockingStreamingRequest().transformPayloadBody(
                                (BlockingIterable<String> strings) -> strings, DEPRECATED_DESERIALIZER,
                                DEPRECATED_SERIALIZER).headers()))),
                // Aggregated payload bodies are serialized eagerly whether or not the message may have trailers.
                withoutTrailers(Arrays.<Named<SerializerCall<StreamingHttpRequest>>>asList(
                Named.of("aggregated payloadBody(Object, HttpSerializer2)",
                        r -> r.toRequest().toFuture().get().payloadBody("a", AGGREGATED).headers()),
                Named.of("aggregated payloadBody(Object, HttpSerializer)",
                        r -> r.toRequest().toFuture().get().payloadBody("a", DEPRECATED_SERIALIZER).headers()))));
    }

    @SuppressWarnings({"unused", "deprecation"})
    private static Stream<Arguments> responseCalls() {
        return Stream.concat(withAndWithoutTrailers(Arrays.<Named<SerializerCall<StreamingHttpResponse>>>asList(
                Named.of("streaming payloadBody(Publisher, HttpStreamingSerializer)",
                        r -> r.payloadBody(from("a"), STREAMING).headers()),
                Named.of("streaming transformPayloadBody(Function, HttpStreamingSerializer)",
                        r -> r.transformPayloadBody(TO_STRINGS, STREAMING).headers()),
                Named.of("streaming transformPayloadBody(Function, HttpStreamingDeserializer, HttpStreamingSerializer)",
                        r -> r.transformPayloadBody(strings -> strings, STREAMING, STREAMING).headers()),
                Named.of("streaming payloadBody(Publisher, HttpSerializer)",
                        r -> r.payloadBody(from("a"), DEPRECATED_SERIALIZER).headers()),
                Named.of("streaming transformPayloadBody(Function, HttpSerializer)",
                        r -> r.transformPayloadBody(TO_STRINGS, DEPRECATED_SERIALIZER).headers()),
                Named.of("streaming transformPayloadBody(Function, HttpDeserializer, HttpSerializer)",
                        r -> r.transformPayloadBody(strings -> strings, DEPRECATED_DESERIALIZER,
                                DEPRECATED_SERIALIZER).headers()),
                Named.of("blocking payloadBody(Iterable, HttpStreamingSerializer)",
                        r -> r.toBlockingStreamingResponse().payloadBody(singletonList("a"), STREAMING).headers()),
                Named.of("blocking messageBody(HttpMessageBodyIterable, HttpStreamingSerializer)",
                        r -> r.toBlockingStreamingResponse().messageBody(NOT_ITERATED, STREAMING).headers()),
                Named.of("blocking payloadBody(Iterable, HttpSerializer)",
                        r -> r.toBlockingStreamingResponse().payloadBody(singletonList("a"), DEPRECATED_SERIALIZER)
                                .headers()),
                Named.of("blocking transformPayloadBody(Function, HttpSerializer)",
                        r -> r.toBlockingStreamingResponse()
                                .transformPayloadBody(TO_STRINGS_BLOCKING, DEPRECATED_SERIALIZER).headers()),
                Named.of("blocking transformPayloadBody(Function, HttpDeserializer, HttpSerializer)",
                        r -> r.toBlockingStreamingResponse().transformPayloadBody(
                                (BlockingIterable<String> strings) -> strings, DEPRECATED_DESERIALIZER,
                                DEPRECATED_SERIALIZER).headers()))),
                // Aggregated payload bodies are serialized eagerly whether or not the message may have trailers.
                withoutTrailers(Arrays.<Named<SerializerCall<StreamingHttpResponse>>>asList(
                Named.of("aggregated payloadBody(Object, HttpSerializer2)",
                        r -> r.toResponse().toFuture().get().payloadBody("a", AGGREGATED).headers()),
                Named.of("aggregated payloadBody(Object, HttpSerializer)",
                        r -> r.toResponse().toFuture().get().payloadBody("a", DEPRECATED_SERIALIZER).headers()))));
    }

    private static <M> Stream<Arguments> withAndWithoutTrailers(List<Named<SerializerCall<M>>> calls) {
        return calls.stream().flatMap(call -> Stream.of(Arguments.of(call, false), Arguments.of(call, true)));
    }

    private static <M> Stream<Arguments> withoutTrailers(List<Named<SerializerCall<M>>> calls) {
        return calls.stream().map(call -> Arguments.of(call, false));
    }
}
