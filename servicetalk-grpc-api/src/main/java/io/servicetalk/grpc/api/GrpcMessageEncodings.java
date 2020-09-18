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

public final class GrpcMessageEncodings {

    public static final GrpcMessageEncoding NONE = new DefaultGrpcMessageEncoding("identity",
            new IdentityGrpcMessageCodec());

    public static final GrpcMessageEncoding GZIP = new DefaultGrpcMessageEncoding("gzip",
            new GzipGrpcMessageCodec());

    public static final GrpcMessageEncoding DEFLATE = new DefaultGrpcMessageEncoding("deflate",
            new DeflateGrpcMessageCodec());

    public static final Set<GrpcMessageEncoding> ALL =
            unmodifiableSet(new HashSet<>(asList(NONE, GZIP, DEFLATE)));

    private GrpcMessageEncodings() {
    }



    /**
     * Returns a {@link GrpcMessageEncoding} that matches the input name.
     * NULL or empty names will always result in NULL {@link GrpcMessageEncoding} and "identity" will
     * always result in {@link GrpcMessageEncodings#NONE} regardless of its presence in the allowedList
     *
     * {@link GrpcMessageEncodings#NONE} is always supported
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
}
