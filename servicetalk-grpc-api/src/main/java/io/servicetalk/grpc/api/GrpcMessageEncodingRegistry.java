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
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

public final class GrpcMessageEncodingRegistry {

    private static final ConcurrentMap<String, GrpcMessageEncoding> REGISTRY = new ConcurrentHashMap<>();

    public static final GrpcMessageEncoding NONE =
            new DefaultGrpcMessageEncoding("identity", new IdentityGrpcMessageCodec());
    public static final GrpcMessageEncoding GZIP =
            new DefaultGrpcMessageEncoding("gzip", new GzipGrpcMessageCodec());
    public static final GrpcMessageEncoding DEFLATE =
            new DefaultGrpcMessageEncoding("deflate", new DeflateGrpcMessageCodec());

    static {
        registerEncoding(NONE);
        registerEncoding(GZIP);
        registerEncoding(DEFLATE);
    }

    private GrpcMessageEncodingRegistry() {
    }

    public static void registerEncoding(final GrpcMessageEncoding encoding) {
        requireNonNull(encoding);
        requireNonNull(encoding.name());
        requireNonNull(encoding.codec());

        REGISTRY.putIfAbsent(encoding.name(), encoding);
    }

    @Nullable
    public static GrpcMessageEncoding encodingFor(@Nullable final String name) {
        return encodingFor(REGISTRY.values(), name);
    }

    /**
     * Returns a {@link GrpcMessageEncoding} that matches the input name.
     * NULL or empty names will always result in NULL {@link GrpcMessageEncoding} and "identity" will
     * always result in {@link GrpcMessageEncodingRegistry#NONE} regardless of its presence in the allowedList
     *
     * {@link GrpcMessageEncodingRegistry#NONE} is always supported
     *
     * @param allowedList the source list to find a matching encoding in
     * @param name the encoding name used for the matching predicate
     * @return an encoding from the allowed-list matching the name parameter
     */
    @Nullable
    public static GrpcMessageEncoding encodingFor(final Collection<GrpcMessageEncoding> allowedList,
                                                  @Nullable final String name) {
        requireNonNull(allowedList);
        if (name == null || name.isEmpty()) {
            return null;
        }

        // Identity is always supported, regardless of its presence in the allowed-list
        if (name.equalsIgnoreCase(NONE.name())) {
            return NONE;
        }

        for (GrpcMessageEncoding enumEnc : allowedList) {
            // Encoding values can potentially included compression configurations, we only match on the type
            if (name.startsWith(enumEnc.name())) {
                return enumEnc;
            }
        }

        return null;
    }

    public static GrpcMessageEncoding encodingForOrNone(@Nullable final String name) {
        if (name == null || name.isEmpty()) {
            return NONE;
        }

        for (GrpcMessageEncoding enumEnc : REGISTRY.values()) {
            // Encoding values can potentially included compression configurations, we only match on the type
            if (name.startsWith(enumEnc.name())) {
                return enumEnc;
            }
        }

        return NONE;
    }

    public static Collection<GrpcMessageEncoding> allEncodings() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }
}
