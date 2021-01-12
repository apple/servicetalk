/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.internal;

import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.encoding.api.ContentCodings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.encoding.api.ContentCodings.identity;
import static io.servicetalk.utils.internal.CharSequenceUtils.contentEqualsIgnoreCaseUnknownTypes;
import static io.servicetalk.utils.internal.CharSequenceUtils.regionMatches;
import static io.servicetalk.utils.internal.CharSequenceUtils.split;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

public final class HeaderUtils {

    private static final List<ContentCodec> NONE_CONTENT_ENCODING_SINGLETON = singletonList(identity());

    private HeaderUtils() {
        // no instances
    }

    /**
     * Establish a commonly accepted encoding between server and client, according to the supported-encodings
     * on the server side and the {@code 'Accepted-Encoding'} incoming header on the request.
     * <p>
     * If no supported encodings are configured then the result is always {@code null}
     * If no accepted encodings are present in the request then the result is always {@code null}
     * In all other cases, the first matching encoding (that is NOT {@link ContentCodings#identity()}) is preferred,
     * otherwise {@code null} is returned.
     *
     * @param acceptEncodingHeaderValue The accept encoding header value.
     * @param serverSupportedEncodings The server supported codings as configured.
     * @return The {@link ContentCodec} that satisfies both client and server needs,
     * null if none found or matched to {@link ContentCodings#identity()}
     */
    @Nullable
    public static ContentCodec negotiateAcceptedEncoding(@Nullable final CharSequence acceptEncodingHeaderValue,
                                                  final List<ContentCodec> serverSupportedEncodings) {

        // Fast path, server has no encodings configured or has only identity configured as encoding
        if (serverSupportedEncodings.isEmpty() ||
                (serverSupportedEncodings.size() == 1 && serverSupportedEncodings.contains(identity()))) {
            return null;
        }

        List<ContentCodec> clientSupportedEncodings = parseAcceptEncoding(acceptEncodingHeaderValue,
                serverSupportedEncodings);
        return negotiateAcceptedEncoding(clientSupportedEncodings, serverSupportedEncodings);
    }

    /**
     * Establish a commonly accepted encoding between server and client, according to the supported-encodings
     * on the server side and the incoming header on the request.
     * <p>
     * If no supported encodings are passed then the result is always {@code null}
     * Otherwise, the first matching encoding (that is NOT {@link ContentCodings#identity()}) is preferred,
     * or {@code null} is returned.
     *
     * @param clientSupportedEncodings The client supported codings as found in the HTTP header.
     * @param serverSupportedEncodings The server supported codings as configured.
     * @return The {@link ContentCodec} that satisfies both client and server needs,
     * null if none found or matched to {@link ContentCodings#identity()}
     */
    @Nullable
    public static ContentCodec negotiateAcceptedEncoding(final List<ContentCodec> clientSupportedEncodings,
                                                  final List<ContentCodec> serverSupportedEncodings) {
        // Fast path, Client has no encodings configured, or has identity as the only encoding configured
        if (clientSupportedEncodings == NONE_CONTENT_ENCODING_SINGLETON ||
                (clientSupportedEncodings.size() == 1 && clientSupportedEncodings.contains(identity()))) {
            return null;
        }

        for (ContentCodec encoding : serverSupportedEncodings) {
            if (encoding != identity() && clientSupportedEncodings.contains(encoding)) {
                return encoding;
            }
        }

        return null;
    }

    private static List<ContentCodec> parseAcceptEncoding(@Nullable final CharSequence acceptEncodingHeaderValue,
                                                         final List<ContentCodec> allowedEncodings) {

        if (acceptEncodingHeaderValue == null || acceptEncodingHeaderValue.length() == 0) {
            return NONE_CONTENT_ENCODING_SINGLETON;
        }

        List<ContentCodec> knownEncodings = new ArrayList<>();
        List<CharSequence> acceptEncodingValues = split(acceptEncodingHeaderValue, ',');
        for (CharSequence val : acceptEncodingValues) {
            ContentCodec enc = encodingFor(allowedEncodings, val);
            if (enc != null) {
                knownEncodings.add(enc);
            }
        }

        return knownEncodings;
    }

    /**
     * Returns the {@link ContentCodec} that matches the {@code name} within the {@code allowedList}.
     * if {@code name} is {@code null} or empty it results in {@code null} .
     * If {@code name} is {@code 'identity'} this will always result in
     * {@link ContentCodings#identity()} regardless of its presence in the {@code allowedList}.
     *
     * @param allowedList the source list to find a matching codec from.
     * @param name the codec name used for the equality predicate.
     * @return a codec from the allowed-list that name matches the {@code name}.
     */
    @Nullable
    public static ContentCodec encodingFor(final Collection<ContentCodec> allowedList,
                                    @Nullable final CharSequence name) {
        requireNonNull(allowedList);
        if (name == null || name.length() == 0) {
            return null;
        }

        // Identity is always supported, regardless of its presence in the allowed-list
        if (contentEqualsIgnoreCaseUnknownTypes(name, identity().name())) {
            return identity();
        }

        for (ContentCodec enumEnc : allowedList) {
            // Encoding values can potentially included compression configurations, we only match on the type
            if (startsWith(name, enumEnc.name())) {
                return enumEnc;
            }
        }

        return null;
    }

    private static boolean startsWith(final CharSequence string, final CharSequence prefix) {
        return regionMatches(string, true, 0, prefix, 0, prefix.length());
    }
}
