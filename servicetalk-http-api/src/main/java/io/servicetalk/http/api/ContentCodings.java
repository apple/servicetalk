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

import io.servicetalk.http.api.DefaultContentCodecBuilder.DeflateContentCodecBuilder;
import io.servicetalk.http.api.DefaultContentCodecBuilder.GzipContentCodecBuilder;

import java.util.Collection;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.http.api.CharSequences.regionMatches;
import static java.util.Objects.requireNonNull;

/**
 * Common available encoding implementations.
 * Encoding {@link #identity()} is always supported regardless of the client or server settings.
 */
public final class ContentCodings {

    private static final ContentCodec IDENTITY = IdentityContentCodec.INSTANCE;

    private static final ContentCodec DEFAULT_GZIP = gzip().build();

    private static final ContentCodec DEFAULT_DEFLATE = deflate().build();

    private ContentCodings() {
    }

    /**
     * Returns the default, always supported 'identity' {@link ContentCodec}.
     * @return the default, always supported 'identity' {@link ContentCodec}
     */
    public static ContentCodec identity() {
        return IDENTITY;
    }

    /**
     * Returns the default GZIP {@link ContentCodec} backed by {@link java.util.zip.Inflater}.
     * @return default GZIP based {@link ContentCodec} backed by {@link java.util.zip.Inflater}
     */
    public static ContentCodec gzipDefault() {
        return DEFAULT_GZIP;
    }

    /**
     * Returns a GZIP based {@link DefaultContentCodecBuilder} that allows building
     * a customizable {@link ContentCodec}.
     * @return a GZIP based {@link DefaultContentCodecBuilder} that allows building
     *          a customizable GZIP {@link ContentCodec}
     */
    public static ContentCodecBuilder gzip() {
        return new GzipContentCodecBuilder();
    }

    /**
     * Returns the default DEFLATE based {@link ContentCodec} backed by {@link java.util.zip.Inflater}.
     * @return default DEFLATE based {@link ContentCodec} backed by {@link java.util.zip.Inflater}
     */
    public static ContentCodec deflateDefault() {
        return DEFAULT_DEFLATE;
    }

    /**
     * Returns a DEFLATE based {@link DefaultContentCodecBuilder} that allows building
     * a customizable {@link ContentCodec}.
     * @return a DEFLATE based {@link DefaultContentCodecBuilder} that allows building
     *          a customizable DEFLATE {@link ContentCodec}
     */
    public static ContentCodecBuilder deflate() {
        return new DeflateContentCodecBuilder();
    }

    /**
     * Returns a {@link ContentCodec} that matches the {@code name}.
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
    static ContentCodec encodingFor(final Collection<ContentCodec> allowedList,
                                             @Nullable final CharSequence name) {
        requireNonNull(allowedList);
        if (name == null || name.length() == 0) {
            return null;
        }

        // Identity is always supported, regardless of its presence in the allowed-list
        if (contentEqualsIgnoreCase(name, IDENTITY.name())) {
            return IDENTITY;
        }

        for (ContentCodec enumEnc : allowedList) {
            // Encoding values can potentially included compression configurations, we only match on the type
            if (startsWith(name, enumEnc.name())) {
                return enumEnc;
            }
        }

        return null;
    }

    static boolean startsWith(final CharSequence string, final CharSequence prefix) {
        return regionMatches(string, true, 0, prefix, 0, prefix.length());
    }
}
