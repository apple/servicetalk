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
package io.servicetalk.grpc.api;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;

/**
 * Default available encoding implementations.
 * Encoding {@link #identity()} is always supported regardless of the client or server settings.
 *
 * {@link #all()} is a set that includes all default encodings {@link #deflate()} and {@link #gzip()}.
 */
public final class GrpcMessageEncodings {

    private static final GrpcMessageEncoding IDENTITY =
            new DefaultGrpcMessageEncoding("identity", new IdentityMessageCodec());

    private static final GrpcMessageEncoding GZIP =
            new DefaultGrpcMessageEncoding("gzip", new GzipMessageCodec());

    private static final GrpcMessageEncoding DEFLATE =
            new DefaultGrpcMessageEncoding("deflate", new DeflateMessageCodec());

    private static final Set<GrpcMessageEncoding> ALL =
            unmodifiableSet(new HashSet<>(asList(IDENTITY, GZIP, DEFLATE)));

    private GrpcMessageEncodings() {
    }

    /**
     * Returns the default, always supported 'identity' {@link GrpcMessageEncoding}.
     * @return the default, always supported 'identity' {@link GrpcMessageEncoding}
     */
    public static GrpcMessageEncoding identity() {
        return IDENTITY;
    }

    /**
     * Returns a GZIP based {@link GrpcMessageEncoding} backed by {@link java.util.zip.Inflater}.
     * @return a GZIP based {@link GrpcMessageEncoding} backed by {@link java.util.zip.Inflater}
     */
    public static GrpcMessageEncoding gzip() {
        return GZIP;
    }

    /**
     * Returns a DEFLATE based {@link GrpcMessageEncoding} backed by {@link java.util.zip.Inflater}.
     * @return a DEFLATE based {@link GrpcMessageEncoding} backed by {@link java.util.zip.Inflater}
     */
    public static GrpcMessageEncoding deflate() {
        return DEFLATE;
    }

    /**
     * Returns a list of all {@link GrpcMessageEncoding}s included by default.
     * @return a list of all {@link GrpcMessageEncoding}s included by default
     */
    public static Set<GrpcMessageEncoding> all() {
        return ALL;
    }

    /**
     * Returns a {@link GrpcMessageEncoding} that matches the {@code name}.
     * Returns {@code null} if {@code name} is {@code null} or empty.
     * If {@code name} is {@code 'identity'} this will always result in
     * {@link GrpcMessageEncodings#IDENTITY} regardless of its presence in the {@code allowedList}.
     *
     * @param allowedList the source list to find a matching encoding in
     * @param name the encoding name used for the matching predicate
     * @return an encoding from the allowed-list matching the {@code name},
     *          otherwise {@code null} if {@code name} is {@code null} or empty
     */
    @Nullable
    public static GrpcMessageEncoding encodingFor(final Collection<GrpcMessageEncoding> allowedList,
                                                  @Nullable final String name) {
        requireNonNull(allowedList);
        if (name == null || name.isEmpty()) {
            return null;
        }

        // Identity is always supported, regardless of its presence in the allowed-list
        if (name.equalsIgnoreCase(IDENTITY.name())) {
            return IDENTITY;
        }

        for (GrpcMessageEncoding enumEnc : allowedList) {
            // Encoding values can potentially included compression configurations, we only match on the type
            if (name.startsWith(enumEnc.name())) {
                return enumEnc;
            }
        }

        return null;
    }
}
