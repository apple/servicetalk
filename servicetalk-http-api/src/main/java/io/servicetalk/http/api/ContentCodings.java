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

import io.servicetalk.http.api.StreamingContentCodingBuilder.DeflateStreamingContentCodingBuilder;
import io.servicetalk.http.api.StreamingContentCodingBuilder.GzipStreamingContentCodingBuilder;

import java.util.Collection;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.CharSequences.contentEquals;
import static io.servicetalk.http.api.CharSequences.isEmpty;
import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static java.util.Objects.requireNonNull;

/**
 * Common available encoding implementations.
 * Encoding {@link #identity()} is always supported regardless of the client or server settings.
 */
public final class ContentCodings {

    static final CharSequence IDENTITY_NAME = newAsciiString("identity");

    private static final StreamingContentCoding IDENTITY = new IdentityContentCoding();

    private static final StreamingContentCoding DEFAULT_GZIP = gzip().build();

    private static final StreamingContentCoding DEFAULT_DEFLATE = deflate().build();

    private static final StreamingContentCoding[] ALL = {IDENTITY, DEFAULT_GZIP, DEFAULT_DEFLATE};

    private ContentCodings() {
    }

    /**
     * Returns the default, always supported 'identity' {@link StreamingContentCoding}.
     * @return the default, always supported 'identity' {@link StreamingContentCoding}
     */
    public static StreamingContentCoding identity() {
        return IDENTITY;
    }

    /**
     * Returns a GZIP based {@link StreamingContentCoding} backed by {@link java.util.zip.Inflater}.
     * @return a GZIP based {@link StreamingContentCoding} backed by {@link java.util.zip.Inflater}
     */
    public static StreamingContentCoding gzipDefault() {
        return DEFAULT_GZIP;
    }

    /**
     * Returns a GZIP based {@link StreamingContentCodingBuilder} that allows building
     * a customizable {@link StreamingContentCoding}.
     * @return a GZIP based {@link StreamingContentCodingBuilder} that allows building
     *          a customizable GZIP {@link StreamingContentCoding}
     */
    public static StreamingContentCodingBuilder gzip() {
        return new GzipStreamingContentCodingBuilder();
    }

    /**
     * Returns a DEFLATE based {@link StreamingContentCoding} backed by {@link java.util.zip.Inflater}.
     * @return a DEFLATE based {@link StreamingContentCoding} backed by {@link java.util.zip.Inflater}
     */
    public static StreamingContentCoding deflateDefault() {
        return DEFAULT_DEFLATE;
    }

    /**
     * Returns a DEFLATE based {@link StreamingContentCodingBuilder} that allows building
     * a customizable {@link StreamingContentCoding}.
     * @return a DEFLATE based {@link StreamingContentCodingBuilder} that allows building
     *          a customizable DEFLATE {@link StreamingContentCoding}
     */
    public static StreamingContentCodingBuilder deflate() {
        return new DeflateStreamingContentCodingBuilder();
    }

    /**
     * Returns a {@link StreamingContentCoding} that matches the {@code name}.
     * Returns {@code null} if {@code name} is {@code null} or empty.
     * If {@code name} is {@code 'identity'} this will always result in
     * {@link ContentCodings#IDENTITY} regardless of its presence in the {@code allowedList}.
     *
     * @param allowedList the source list to find a matching encoding in
     * @param name the encoding name used for the matching predicate
     * @return an encoding from the allowed-list matching the {@code name},
     *          otherwise {@code null} if {@code name} is {@code null} or empty
     */
    @Nullable
    static StreamingContentCoding encodingFor(final Collection<StreamingContentCoding> allowedList,
                                                     @Nullable final CharSequence name) {
        requireNonNull(allowedList);
        if (name == null || isEmpty(name)) {
            return null;
        }

        // Identity is always supported, regardless of its presence in the allowed-list
        if (contentEquals(name, IDENTITY_NAME)) {
            return IDENTITY;
        }

        for (StreamingContentCoding enumEnc : allowedList) {
            // Encoding values can potentially included compression configurations, we only match on the type
            if (name.toString().startsWith(enumEnc.name().toString())) {
                return enumEnc;
            }
        }

        return null;
    }
}
