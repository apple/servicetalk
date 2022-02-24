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
package io.servicetalk.examples.http.serialization.protobuf;

import io.servicetalk.data.protobuf.ProtobufSerializerFactory;
import io.servicetalk.examples.http.serialization.protobuf.ExampleProtos.RequestMessage;
import io.servicetalk.examples.http.serialization.protobuf.ExampleProtos.ResponseMessage;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpSerializerDeserializer;
import io.servicetalk.http.api.HttpSerializers;
import io.servicetalk.http.api.HttpStreamingSerializerDeserializer;

import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.data.protobuf.ProtobufSerializerFactory.PROTOBUF;
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

    private static final CharSequence APPLICATION_PROTOBUF = newAsciiString("application/protobuf");
    private static final Consumer<HttpHeaders> CONTENT_TYPE_SETTER =
            headers -> headers.set(CONTENT_TYPE, APPLICATION_PROTOBUF);
    private static final Predicate<HttpHeaders> CONTENT_TYPE_VALIDATOR =
            headers -> hasContentType(headers, APPLICATION_PROTOBUF, null);

    public static final HttpSerializerDeserializer<RequestMessage> REQ_SERIALIZER =
            serializer(PROTOBUF.serializerDeserializer(RequestMessage.parser()),
                    CONTENT_TYPE_SETTER, CONTENT_TYPE_VALIDATOR);

    public static final HttpStreamingSerializerDeserializer<RequestMessage> REQ_STREAMING_SERIALIZER =
            streamingSerializer(PROTOBUF.streamingSerializerDeserializer(RequestMessage.parser()),
                    CONTENT_TYPE_SETTER, CONTENT_TYPE_VALIDATOR);

    public static final HttpSerializerDeserializer<ResponseMessage> RESP_SERIALIZER =
            serializer(PROTOBUF.serializerDeserializer(ResponseMessage.parser()),
                    CONTENT_TYPE_SETTER, CONTENT_TYPE_VALIDATOR);

    public static final HttpStreamingSerializerDeserializer<ResponseMessage> RESP_STREAMING_SERIALIZER =
            streamingSerializer(PROTOBUF.streamingSerializerDeserializer(ResponseMessage.parser()),
                    CONTENT_TYPE_SETTER, CONTENT_TYPE_VALIDATOR);

    private SerializerUtils() {
        // No instances.
    }
}
