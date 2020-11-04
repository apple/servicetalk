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

import io.servicetalk.http.api.DefaultStreamingContentCodingBuilder.DeflateStreamingContentCodingBuilder;
import io.servicetalk.http.api.DefaultStreamingContentCodingBuilder.GzipStreamingContentCodingBuilder;

import java.util.Collection;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.CharSequences.contentEquals;
import static io.servicetalk.http.api.CharSequences.isEmpty;
import static io.servicetalk.http.api.CharSequences.startsWith;
import static java.util.Objects.requireNonNull;

/**
 * Common available encoding implementations.
 * Encoding {@link #identity()} is always supported regardless of the client or server settings.
 */
public final class ContentCodings {

    private static final StreamingContentCodec IDENTITY = new IdentityContentCodec();

    private static final StreamingContentCodec DEFAULT_GZIP = gzip().build();

    private static final StreamingContentCodec DEFAULT_DEFLATE = deflate().build();

    private ContentCodings() {
    }

    /**
     * Returns the default, always supported 'identity' {@link StreamingContentCodec}.
     * @return the default, always supported 'identity' {@link StreamingContentCodec}
     */
    public static StreamingContentCodec identity() {
        return IDENTITY;
    }

    /**
     * Returns a GZIP based {@link StreamingContentCodec} backed by {@link java.util.zip.Inflater}.
     * The max allowed payload size for this codec is 2Mib.
     *
     * @return a GZIP based {@link StreamingContentCodec} backed by {@link java.util.zip.Inflater}
     */
    public static StreamingContentCodec gzipDefault() {
        return DEFAULT_GZIP;
    }

    /**
     * Returns a GZIP based {@link DefaultStreamingContentCodingBuilder} that allows building
     * a customizable {@link StreamingContentCodec}.
     * @return a GZIP based {@link DefaultStreamingContentCodingBuilder} that allows building
     *          a customizable GZIP {@link StreamingContentCodec}
     */
    public static DefaultStreamingContentCodingBuilder gzip() {
        return new GzipStreamingContentCodingBuilder();
    }

    /**
     * Returns a DEFLATE based {@link StreamingContentCodec} backed by {@link java.util.zip.Inflater}.
     * The max allowed payload size for this codec is 2Mib.
     *
     * @return a DEFLATE based {@link StreamingContentCodec} backed by {@link java.util.zip.Inflater}
     */
    public static StreamingContentCodec deflateDefault() {
        return DEFAULT_DEFLATE;
    }

    /**
     * Returns a DEFLATE based {@link DefaultStreamingContentCodingBuilder} that allows building
     * a customizable {@link StreamingContentCodec}.
     * @return a DEFLATE based {@link DefaultStreamingContentCodingBuilder} that allows building
     *          a customizable DEFLATE {@link StreamingContentCodec}
     */
    public static DefaultStreamingContentCodingBuilder deflate() {
        return new DeflateStreamingContentCodingBuilder();
    }

    /**
     * Returns a {@link StreamingContentCodec} that matches the {@code name}.
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
    static StreamingContentCodec encodingFor(final Collection<StreamingContentCodec> allowedList,
                                             @Nullable final CharSequence name) {
        requireNonNull(allowedList);
        if (name == null || isEmpty(name)) {
            return null;
        }

        // Identity is always supported, regardless of its presence in the allowed-list
        if (contentEquals(name, IDENTITY.name())) {
            return IDENTITY;
        }

        for (StreamingContentCodec enumEnc : allowedList) {
            // Encoding values can potentially included compression configurations, we only match on the type
            if (startsWith(name, enumEnc.name())) {
                return enumEnc;
            }
        }

        return null;
    }
}
