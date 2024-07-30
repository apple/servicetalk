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
public final class StatusMessageUtils {

    public static final CharSequence GRPC_STATUS_MESSAGE = newAsciiString("grpc-message");

    private static final byte[] HEX =
            {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private StatusMessageUtils() {
        // singleton
    }

    /**
     * Sets and potentially encodes the given status message.
     *
     * @param headers the headers on where to set the message.
     * @param message the message to set.
     */
    public static void setStatusMessage(final HttpHeaders headers, final CharSequence message) {
        final boolean needsEncoding = message.chars().anyMatch(c -> isEscapingChar((char) c));
        headers.set(GRPC_STATUS_MESSAGE, needsEncoding ? encodeMessage(message) : message);
    }

    /**
     * Tries to read the status message from the {@link HttpHeaders} and percent-decode if necessary.
     *
     * @param headers the headers to load and decode the status message from.
     * @return the decoded status message, or null of no message found in the header provided.
     */
    @Nullable
    public static CharSequence getStatusMessage(final HttpHeaders headers) {
        final CharSequence msg = headers.get(GRPC_STATUS_MESSAGE);
        if (msg == null) {
            return null;
        }

        final int msgLength = msg.length();
        for (int i = 0; i < msgLength; i++) {
            char b = msg.charAt(i);
            if (b < ' ' || b >= '~' || (b == '%' && i + 2 < msgLength)) {
                // fast-pathing did not work, perform actual decoding
                return decodeMessage(msg);
            }
        }
        return msg;
    }

    /**
     * Decodes the {@link CharSequence} removing the percent-encoding where needed.
     *
     * @param msg the message to decode.
     * @return the deocded message.
     */
    private static CharSequence decodeMessage(final CharSequence msg) {
        final int msgLength =  msg.length();
        final ByteBuffer buf = ByteBuffer.allocate(msgLength);
        for (int i = 0; i < msgLength;) {
            if (msg.charAt(i) == '%' && i + 2 < msgLength) {
                try {
                    buf.put((byte) Integer.parseInt(msg.subSequence(i + 1, i + 3).toString(), 16));
                    i += 3;
                    continue;
                } catch (NumberFormatException e) {
                    // ignore, fall through, just push the bytes.
                }
            }
            buf.put((byte) msg.charAt(i));
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
    private static boolean isEscapingChar(char b) {
        return b < ' ' || b >= '~' || b == '%';
    }

    /**
     * Performs encoding of the message by using a percent-encoding scheme.
     *
     * @param msg the message to percent encode.
     * @return the encoded message.
     */
    private static CharSequence encodeMessage(final CharSequence msg) {
        byte[] escapedBytes = new byte[msg.length() * 3];
        int ri = 0;
        int wi = ri;
        for (; ri < msg.length(); ri++) {
            char b = msg.charAt(ri);
            // Manually implement URL encoding, per the gRPC spec.
            if (isEscapingChar(b)) {
                escapedBytes[wi] = '%';
                escapedBytes[wi + 1] = HEX[(b >> 4) & 0xF];
                escapedBytes[wi + 2] = HEX[b & 0xF];
                wi += 3;
                continue;
            }
            escapedBytes[wi++] = (byte) b;
        }
        return new String(escapedBytes, 0, wi, StandardCharsets.UTF_8);
    }
}
