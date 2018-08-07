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
/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.servicetalk.http.api;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

import static java.util.Objects.requireNonNull;

/**
 * Creates an URL-encoded URI from a path string and key-value parameter pairs.
 * This encoder is for one time use only.  Create a new instance for each URI.
 *
 * <pre>
 * {@link QueryStringEncoder} encoder = new {@link QueryStringEncoder}("/hello");
 * encoder.addParam("recipient", "world");
 * assert encoder.toString().equals("/hello?recipient=world");
 * </pre>
 *
 * @see QueryStringDecoder
 */
final class QueryStringEncoder {

    /**
     * Default character set (UTF-8)
     */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private final StringBuilder uriBuilder;
    private boolean hasParams;

    /**
     * Creates a new encoder that encodes a URI that starts with the specified
     * path string.  The encoder will encode the URI in UTF-8.
     */
    QueryStringEncoder(final String uri) {
        uriBuilder = new StringBuilder(uri);
    }

    /**
     * Adds a parameter with the specified name and value to this encoder.
     */
    public void addParam(final String name, final String value) {
        requireNonNull(name, "name");
        if (hasParams) {
            uriBuilder.append('&');
        } else {
            uriBuilder.append('?');
            hasParams = true;
        }
        appendComponent(name, uriBuilder);
        if (value != null) {
            uriBuilder.append('=');
            appendComponent(value, uriBuilder);
        }
    }

    /**
     * Returns the URL-encoded URI object which was created from the path string
     * specified in the constructor and the parameters added by
     * {@link #addParam(String, String)} method.
     */
    public URI toUri() throws URISyntaxException {
        return new URI(toString());
    }

    /**
     * Returns the URL-encoded URI which was created from the path string
     * specified in the constructor and the parameters added by
     * {@link #addParam(String, String)} method.
     */
    @Override
    public String toString() {
        return uriBuilder.toString();
    }

    private static void appendComponent(String s, final StringBuilder sb) {
        try {
            s = URLEncoder.encode(s, DEFAULT_CHARSET.name());
        } catch (final UnsupportedEncodingException ignored) {
            throw new UnsupportedCharsetException(DEFAULT_CHARSET.name());
        }
        // replace all '+' with "%20"
        int idx = s.indexOf('+');
        if (idx == -1) {
            sb.append(s);
            return;
        }
        sb.append(s, 0, idx).append("%20");
        final int size = s.length();
        idx++;
        for (; idx < size; idx++) {
            final char c = s.charAt(idx);
            if (c != '+') {
                sb.append(c);
            } else {
                sb.append("%20");
            }
        }
    }
}
