/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.encoding.api.BufferDecoderGroup;
import io.servicetalk.encoding.api.BufferDecoderGroupBuilder;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.encoding.api.EmptyBufferDecoderGroup;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.encoding.api.Identity.identityEncoder;
import static io.servicetalk.encoding.netty.NettyBufferEncoders.deflateDefault;
import static io.servicetalk.encoding.netty.NettyBufferEncoders.gzipDefault;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

abstract class BaseContentEncodingTest {
    private static final int PAYLOAD_SIZE = 1024;

    private static Stream<Arguments> params() {
        return Stream.of(
                Arguments.of(HTTP_1, Encoder.DEFAULT, Decoders.DEFAULT, Encoders.DEFAULT, Decoders.DEFAULT, true),
                Arguments.of(HTTP_2, Encoder.DEFAULT, Decoders.DEFAULT, Encoders.DEFAULT, Decoders.DEFAULT, true),
                Arguments.of(HTTP_1, Encoder.GZIP, Decoders.GZIP_ID, Encoders.GZIP_ID, Decoders.GZIP_ID, true),
                Arguments.of(HTTP_2, Encoder.GZIP, Decoders.GZIP_ID, Encoders.GZIP_ID, Decoders.GZIP_ID, true),
                Arguments.of(HTTP_1, Encoder.DEFLATE, Decoders.DEFLATE_ID, Encoders.DEFLATE_ID, Decoders.DEFLATE_ID,
                        true),
                Arguments.of(HTTP_2, Encoder.DEFLATE, Decoders.DEFLATE_ID, Encoders.DEFLATE_ID, Decoders.DEFLATE_ID,
                        true),
                Arguments.of(HTTP_1, Encoder.GZIP, Decoders.GZIP_DEFLATE_ID, Encoders.GZIP_DEFLATE_ID,
                        Decoders.GZIP_DEFLATE_ID, true),
                Arguments.of(HTTP_2, Encoder.GZIP, Decoders.GZIP_DEFLATE_ID, Encoders.GZIP_DEFLATE_ID,
                        Decoders.GZIP_DEFLATE_ID, true),
                Arguments.of(HTTP_1, Encoder.DEFLATE, Decoders.GZIP_DEFLATE_ID, Encoders.GZIP_DEFLATE_ID,
                        Decoders.GZIP_DEFLATE_ID, true),
                Arguments.of(HTTP_2, Encoder.DEFLATE, Decoders.GZIP_DEFLATE_ID, Encoders.GZIP_DEFLATE_ID,
                        Decoders.GZIP_DEFLATE_ID, true),
                Arguments.of(HTTP_1, Encoder.GZIP, Decoders.ID_DEFLATE_GZIP, Encoders.ID_DEFLATE_GZIP,
                        Decoders.ID_DEFLATE_GZIP, true),
                Arguments.of(HTTP_2, Encoder.GZIP, Decoders.ID_DEFLATE_GZIP, Encoders.ID_DEFLATE_GZIP,
                        Decoders.ID_DEFLATE_GZIP, true),
                Arguments.of(HTTP_1, Encoder.DEFLATE, Decoders.ID_DEFLATE_GZIP, Encoders.ID_DEFLATE_GZIP,
                        Decoders.ID_DEFLATE_GZIP, true),
                Arguments.of(HTTP_2, Encoder.DEFLATE, Decoders.ID_DEFLATE_GZIP, Encoders.ID_DEFLATE_GZIP,
                        Decoders.ID_DEFLATE_GZIP, true),
                Arguments.of(HTTP_1, Encoder.ID, Decoders.GZIP_DEFLATE_ID, Encoders.DEFAULT, Decoders.ID_ONLY,
                        true),
                Arguments.of(HTTP_2, Encoder.ID, Decoders.GZIP_DEFLATE_ID, Encoders.DEFAULT, Decoders.ID_ONLY,
                        true),

                // identity is currently always supported
                Arguments.of(HTTP_1, Encoder.DEFAULT, Decoders.DEFAULT, Encoders.GZIP_ONLY, Decoders.DEFAULT, true),
                Arguments.of(HTTP_2, Encoder.DEFAULT, Decoders.DEFAULT, Encoders.GZIP_ONLY, Decoders.DEFAULT, true),
                Arguments.of(HTTP_1, Encoder.DEFAULT, Decoders.DEFAULT, Encoders.DEFLATE_ONLY, Decoders.DEFAULT, true),
                Arguments.of(HTTP_2, Encoder.DEFAULT, Decoders.DEFAULT, Encoders.DEFLATE_ONLY, Decoders.DEFAULT, true),

                Arguments.of(HTTP_1, Encoder.GZIP, Decoders.DEFAULT, Encoders.DEFAULT, Decoders.DEFAULT, false),
                Arguments.of(HTTP_2, Encoder.GZIP, Decoders.DEFAULT, Encoders.DEFAULT, Decoders.DEFAULT, false),
                Arguments.of(HTTP_1, Encoder.DEFLATE, Decoders.DEFAULT, Encoders.DEFAULT, Decoders.DEFAULT, false),
                Arguments.of(HTTP_2, Encoder.DEFLATE, Decoders.DEFAULT, Encoders.DEFAULT, Decoders.DEFAULT, false),
                Arguments.of(HTTP_1, Encoder.GZIP, Decoders.GZIP_DEFLATE_ID, Encoders.DEFAULT, Decoders.DEFAULT, false),
                Arguments.of(HTTP_2, Encoder.GZIP, Decoders.GZIP_DEFLATE_ID, Encoders.DEFAULT, Decoders.DEFAULT, false),
                Arguments.of(HTTP_1, Encoder.DEFLATE, Decoders.GZIP_DEFLATE_ID, Encoders.DEFAULT, Decoders.GZIP_ONLY,
                        false),
                Arguments.of(HTTP_2, Encoder.DEFLATE, Decoders.GZIP_DEFLATE_ID, Encoders.DEFAULT, Decoders.GZIP_ONLY,
                        false),
                Arguments.of(HTTP_1, Encoder.ID, Decoders.GZIP_DEFLATE_ID, Encoders.DEFAULT, Decoders.GZIP_ONLY,
                        true),
                Arguments.of(HTTP_2, Encoder.ID, Decoders.GZIP_DEFLATE_ID, Encoders.DEFAULT, Decoders.GZIP_ONLY,
                        true));
    }

    @ParameterizedTest(name = "{index}, protocol={0}, client-encode=[{1}], client-decode=[{2}], server-encode={3}, " +
            "server-decode={4}, valid={5}")
    @MethodSource("params")
    final void testCompatibility(
            final HttpProtocol protocol, final Encoder clientEncoding, final Decoders clientDecoder,
            final Encoders serverEncoder, final Decoders serverDecoder, final boolean valid) throws Throwable {
        runTest(protocol, clientEncoding, clientDecoder, serverEncoder, serverDecoder, valid);
    }

    protected abstract void runTest(
            HttpProtocol protocol, Encoder clientEncoding, Decoders clientDecoder,
            Encoders serverEncoder, Decoders serverDecoder, boolean isValid) throws Throwable;

    static byte[] payload(byte b) {
        byte[] payload = new byte[PAYLOAD_SIZE];
        Arrays.fill(payload, b);
        return payload;
    }

    static String payloadAsString(byte b) {
        return new String(payload(b), StandardCharsets.US_ASCII);
    }

    protected enum Decoders {
        DEFAULT(EmptyBufferDecoderGroup.INSTANCE),
        GZIP_ONLY(new BufferDecoderGroupBuilder().add(gzipDefault(), true).build()),
        GZIP_ID(new BufferDecoderGroupBuilder().add(gzipDefault(), true).add(identityEncoder(), false).build()),
        GZIP_DEFLATE_ID(new BufferDecoderGroupBuilder().add(gzipDefault(), true).add(deflateDefault(), true)
                .add(identityEncoder(), false).build()),
        ID_ONLY(new BufferDecoderGroupBuilder().add(identityEncoder(), true).build()),
        ID_GZIP(new BufferDecoderGroupBuilder().add(identityEncoder(), false).add(gzipDefault(), true).build()),
        ID_DEFLATE(new BufferDecoderGroupBuilder().add(identityEncoder(), false).add(deflateDefault(), true).build()),
        ID_DEFLATE_GZIP(new BufferDecoderGroupBuilder().add(identityEncoder(), false).add(deflateDefault(), true)
                .add(gzipDefault(), true).build()),
        DEFLATE_ONLY(new BufferDecoderGroupBuilder().add(deflateDefault(), true).build()),
        DEFLATE_ID(new BufferDecoderGroupBuilder().add(deflateDefault(), true).add(identityEncoder(), false).build());

        final BufferDecoderGroup group;

        Decoders(BufferDecoderGroup group) {
            this.group = group;
        }

        @Override
        public String toString() {
            return Objects.toString(group.advertisedMessageEncoding());
        }
    }

    protected enum Encoders {
        DEFAULT(emptyList()),
        GZIP_ONLY(singletonList(gzipDefault())),
        GZIP_ID(asList(gzipDefault(), identityEncoder())),
        GZIP_DEFLATE_ID(asList(gzipDefault(), deflateDefault(), identityEncoder())),
        ID_ONLY(singletonList(identityEncoder())),
        ID_GZIP(asList(identityEncoder(), gzipDefault())),
        ID_DEFLATE(asList(identityEncoder(), deflateDefault())),
        ID_DEFLATE_GZIP(asList(identityEncoder(), deflateDefault(), gzipDefault())),
        DEFLATE_ONLY(singletonList(deflateDefault())),
        DEFLATE_ID(asList(deflateDefault(), identityEncoder()));

        final List<BufferEncoder> list;

        Encoders(List<BufferEncoder> list) {
            this.list = list;
        }

        @Override
        public String toString() {
            if (list.isEmpty()) {
                return identityEncoder().encoder().toString();
            }

            StringBuilder b = new StringBuilder();
            for (BufferEncoder c : list) {
                if (b.length() > 1) {
                    b.append(", ");
                }

                b.append(c.encodingName());
            }
            return b.toString();
        }
    }

    protected enum Encoder {
        DEFAULT(null),
        ID(identityEncoder()),
        GZIP(gzipDefault()),
        DEFLATE(deflateDefault());

        @Nullable
        final BufferEncoder encoder;

        Encoder(@Nullable BufferEncoder encoder) {
            this.encoder = encoder;
        }

        @Override
        public String toString() {
            return encoder == null ? "null" : encoder.encodingName().toString();
        }
    }
}
