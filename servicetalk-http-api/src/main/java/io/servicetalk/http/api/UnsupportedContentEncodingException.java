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

/**
 * Exception thrown when a payload was encoded with an unsupported encoder.
 */
public final class UnsupportedContentEncodingException extends RuntimeException {

    private final String encoding;

    /**
     * New instance.
     *
     * @param encoding the name of the encoding used
     */
    public UnsupportedContentEncodingException(String encoding) {
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
