/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.serializer.api.SerializationException;

/**
 * Exception thrown when a payload was encoded with an unsupported encoder.
 */
final class UnsupportedContentEncodingException extends SerializationException {

    private static final long serialVersionUID = 5645078707423180235L;

    private final String encoding;

    /**
     * New instance.
     *
     * @param encoding the name of the encoding used
     */
    UnsupportedContentEncodingException(String encoding) {
        super("Compression " + encoding + " not supported");
        this.encoding = encoding;
    }

    /**
     * The name of the encoding used when the Exception was thrown.
     * @return the name of the encoding used when the Exception was thrown
     */
    public String encoding() {
        return encoding;
    }
}
