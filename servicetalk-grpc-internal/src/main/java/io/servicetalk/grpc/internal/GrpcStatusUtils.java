/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
/*
 * Copyright 2024 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.grpc.internal;

import io.servicetalk.http.api.HttpHeaders;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;

/**
 * Provides utilities around percent-encoding and decoding the GRPC status message.
 * <p>
 * Note that much of the actual encoding and decoding logic is borrowed from the {@code io.grpc.Status} class,
 * specifically the {@code io.grpc.Status#StatusMessageMarshaller}.
 */
public final class GrpcStatusUtils {

    public static final CharSequence GRPC_STATUS_MESSAGE = newAsciiString("grpc-message");

    private static final byte[] HEX =
            {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private GrpcStatusUtils() {
        // singleton
    }

    /**
     * Sets and potentially encodes the given status message.
     *
     * @param headers the headers on where to set the message.
     * @param message the message to set.
     */
    public static void setStatusMessage(final HttpHeaders headers, final CharSequence message) {
        final byte[] messageBytes = message.toString().getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < messageBytes.length; i++) {
            final byte b = messageBytes[i];
            // If there are only non escaping characters, skip the slow path.
            if (isEscapingChar(b)) {
                headers.set(GRPC_STATUS_MESSAGE, encodeMessage(messageBytes, i));
                return;
            }
        }
        headers.set(GRPC_STATUS_MESSAGE, message);
    }

    /**
     * Tries to read the status message from the {@link HttpHeaders} and percent-decode if necessary.
     *
     * @param headers the headers to load and decode the status message from.
     * @return the decoded status message, or null of no message found in the header provided.
     */
    @Nullable
    public static CharSequence getStatusMessage(final HttpHeaders headers) {
        final CharSequence message = headers.get(GRPC_STATUS_MESSAGE);
        if (message == null) {
            return null;
        }

        final byte[] messageBytes = message.toString().getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < messageBytes.length; i++) {
            byte b = messageBytes[i];
            if (b < ' ' || b >= '~' || (b == '%' && i + 2 < messageBytes.length)) {
                return decodeMessage(messageBytes);
            }
        }
        return message;
    }

    /**
     * Decodes the {@link CharSequence} removing the percent-encoding where needed.
     *
     * @param messageBytes the message to decode.
     * @return the deocded message.
     */
    private static CharSequence decodeMessage(final byte[] messageBytes) {
        ByteBuffer buf = ByteBuffer.allocate(messageBytes.length);
        for (int i = 0; i < messageBytes.length;) {
            if (messageBytes[i] == '%' && i + 2 < messageBytes.length) {
                try {
                    buf.put((byte) Integer.parseInt(new String(messageBytes, i + 1, 2, StandardCharsets.US_ASCII), 16));
                    i += 3;
                    continue;
                } catch (NumberFormatException e) {
                    // ignore, fall through, just push the bytes.
                }
            }
            buf.put(messageBytes[i]);
            i += 1;
        }
        return new String(buf.array(), 0, buf.position(), StandardCharsets.UTF_8);
    }

    /**
     * Describes the character ranges which need escaping (essentially non-printable characters).
     *
     * @param b the character to check.
     * @return true if it needs escaping, false otherwise.
     */
    private static boolean isEscapingChar(byte b) {
        return b < ' ' || b >= '~' || b == '%';
    }

    /**
     * Performs encoding of the message by using a percent-encoding scheme.
     *
     * @param msgBytes the encoded message.
     * @param ri the reader index previously iterated to.
     * @return the encoded message.
     */
    private static CharSequence encodeMessage(byte[] msgBytes, int ri) {
        byte[] escapedBytes = new byte[ri + (msgBytes.length - ri) * 3];
        // copy over the good bytes
        if (ri != 0) {
            System.arraycopy(msgBytes, 0, escapedBytes, 0, ri);
        }
        int wi = ri;
        for (; ri < msgBytes.length; ri++) {
            byte b = msgBytes[ri];
            // Manually implement URL encoding, per the gRPC spec.
            if (isEscapingChar(b)) {
                escapedBytes[wi] = '%';
                escapedBytes[wi + 1] = HEX[(b >> 4) & 0xF];
                escapedBytes[wi + 2] = HEX[b & 0xF];
                wi += 3;
                continue;
            }
            escapedBytes[wi++] = b;
        }
        return new String(escapedBytes, 0, wi, StandardCharsets.UTF_8);
    }
}
