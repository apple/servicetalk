/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.jaxrs.client;

import io.servicetalk.data.protobuf.ProtobufSerializerFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpSerializerDeserializer;
import io.servicetalk.http.api.HttpSerializers;
import io.servicetalk.http.api.HttpStreamingSerializerDeserializer;
import io.servicetalk.tests.helloworld.HelloReply;
import io.servicetalk.tests.helloworld.HelloRequest;

import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.servicetalk.data.protobuf.ProtobufSerializerFactory.PROTOBUF;
import static io.servicetalk.data.protobuf.jersey.ProtobufMediaTypes.APPLICATION_PROTOBUF;
import static io.servicetalk.data.protobuf.jersey.ProtobufMediaTypes.APPLICATION_PROTOBUF_VAR_INT;
import static io.servicetalk.http.api.HeaderUtils.hasContentType;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpSerializers.serializer;
import static io.servicetalk.http.api.HttpSerializers.streamingSerializer;

/**
 * Utilities to cache serializer instances for request/response protos.
 * <p>
 * {@link ProtobufSerializerFactory} produces protocol-agnostic serializer/deserializer. We use {@link HttpSerializers}
 * utilities to concert it to HTTP-aware variants.
 */
public final class SerializerUtils {
    private static final Consumer<HttpHeaders> CONTENT_TYPE_SETTER =
            headers -> headers.set(CONTENT_TYPE, APPLICATION_PROTOBUF);
    private static final Predicate<HttpHeaders> CONTENT_TYPE_VALIDATOR =
            headers -> hasContentType(headers, APPLICATION_PROTOBUF, null);
    private static final Consumer<HttpHeaders> VAR_INT_CONTENT_TYPE_SETTER =
            headers -> headers.set(CONTENT_TYPE, APPLICATION_PROTOBUF_VAR_INT);
    private static final Predicate<HttpHeaders> VAR_INT_CONTENT_TYPE_VALIDATOR =
            headers -> hasContentType(headers, APPLICATION_PROTOBUF_VAR_INT, null);

    public static final HttpSerializerDeserializer<HelloRequest> REQ_SERIALIZER =
            serializer(PROTOBUF.serializerDeserializer(HelloRequest.parser()),
                    CONTENT_TYPE_SETTER, CONTENT_TYPE_VALIDATOR);

    public static final HttpStreamingSerializerDeserializer<HelloRequest> REQ_STREAMING_SERIALIZER =
            streamingSerializer(PROTOBUF.streamingSerializerDeserializer(HelloRequest.parser()),
                    VAR_INT_CONTENT_TYPE_SETTER, VAR_INT_CONTENT_TYPE_VALIDATOR);

    public static final HttpSerializerDeserializer<HelloReply> RESP_SERIALIZER =
            serializer(PROTOBUF.serializerDeserializer(HelloReply.parser()),
                    CONTENT_TYPE_SETTER, CONTENT_TYPE_VALIDATOR);

    public static final HttpStreamingSerializerDeserializer<HelloReply> RESP_STREAMING_SERIALIZER =
            streamingSerializer(PROTOBUF.streamingSerializerDeserializer(HelloReply.parser()),
                    VAR_INT_CONTENT_TYPE_SETTER, VAR_INT_CONTENT_TYPE_VALIDATOR);

    private SerializerUtils() {
        // No instances.
    }
}
