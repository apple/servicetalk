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
package io.servicetalk.encoding.api.internal;

import io.servicetalk.buffer.api.CharSequences;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.encoding.api.Identity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.regionMatches;
import static io.servicetalk.buffer.api.CharSequences.split;
import static io.servicetalk.encoding.api.Identity.identity;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

/**
 * Header utilities to support encoding.
 */
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
     * In all other cases, the first matching encoding (that is NOT {@link Identity#identity()}) is preferred,
     * otherwise {@code null} is returned.
     * @deprecated Use {@link #negotiateAcceptedEncodingRaw(CharSequence, List, Function)}.
     * @param acceptEncodingHeaderValue The accept encoding header value.
     * @param serverSupportedEncodings The server supported codings as configured.
     * @return The {@link ContentCodec} that satisfies both client and server needs,
     * null if none found or matched to {@link Identity#identity()}
     */
    @Deprecated
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
     * Get an encoder from {@code supportedEncoders} that is acceptable as referenced by
     * {@code acceptEncodingHeaderValue}.
     * @param acceptEncodingHeaderValue The accept encoding header value.
     * @param supportedEncoders The supported encoders.
     * @param messageEncodingFunc Accessor to get the encoder form an element of {@code supportedEncoders}.
     * @param <T> The type containing the encoder.
     * @return an encoder from {@code supportedEncoders} that is acceptable as referenced by
     * {@code acceptEncodingHeaderValue}.
     */
    @Nullable
    public static <T> T negotiateAcceptedEncodingRaw(
            @Nullable final CharSequence acceptEncodingHeaderValue,
            final List<T> supportedEncoders,
            final Function<T, CharSequence> messageEncodingFunc) {
        // Fast path, server has no encodings configured or has only identity configured as encoding
        if (acceptEncodingHeaderValue == null || supportedEncoders.isEmpty()) {
            return null;
        }

        int i = 0;
        do {
            // Find the next comma separated value.
            int j = CharSequences.indexOf(acceptEncodingHeaderValue, ',', i);
            if (j < 0) {
                j = acceptEncodingHeaderValue.length();
            }

            if (i >= j) {
                return null;
            }

            // Trim spaces from end.
            int jNonTrimmed = j;
            while (acceptEncodingHeaderValue.charAt(j - 1) == ' ') {
                if (--j == i) {
                    return null;
                }
            }

            // Trim spaces from beginning.
            char firstChar;
            while ((firstChar = acceptEncodingHeaderValue.charAt(i)) == ' ') {
                if (++i == j) {
                    return null;
                }
            }
            // Match special case '*' wild card, ignore qvalue prioritization for now.
            if (firstChar == '*') {
                return supportedEncoders.get(0);
            }

            // If the accepted encoding is supported, use it.
            int x = 0;
            do {
                T supportedEncoding = supportedEncoders.get(x);
                CharSequence serverSupported = messageEncodingFunc.apply(supportedEncoding);
                // Use serverSupported.length() as we ignore qvalue prioritization for now.
                // All content-coding values are case-insensitive [1].
                // [1] https://datatracker.ietf.org/doc/html/rfc7231#section-3.1.2.1.
                if (regionMatches(acceptEncodingHeaderValue, true, i, serverSupported, 0, serverSupported.length())) {
                    return supportedEncoding;
                }
            } while (++x < supportedEncoders.size());

            i = jNonTrimmed + 1;
        } while (i < acceptEncodingHeaderValue.length());

        return null;
    }

    /**
     * Establish a commonly accepted encoding between server and client, according to the supported-encodings
     * on the server side and the incoming header on the request.
     * <p>
     * If no supported encodings are passed then the result is always {@code null}
     * Otherwise, the first matching encoding (that is NOT {@link Identity#identity()}) is preferred,
     * or {@code null} is returned.
     * @deprecated Use {@link #negotiateAcceptedEncodingRaw(CharSequence, List, Function)}.
     * @param clientSupportedEncodings The client supported codings as found in the HTTP header.
     * @param serverSupportedEncodings The server supported codings as configured.
     * @return The {@link ContentCodec} that satisfies both client and server needs,
     * null if none found or matched to {@link Identity#identity()}
     */
    @Deprecated
    @Nullable
    public static ContentCodec negotiateAcceptedEncoding(final List<ContentCodec> clientSupportedEncodings,
                                                         final List<ContentCodec> serverSupportedEncodings) {
        // Fast path, Client has no encodings configured, or has identity as the only encoding configured
        if (clientSupportedEncodings == NONE_CONTENT_ENCODING_SINGLETON ||
                (clientSupportedEncodings.size() == 1 && clientSupportedEncodings.contains(identity()))) {
            return null;
        }

        for (ContentCodec encoding : serverSupportedEncodings) {
            if (!identity().equals(encoding) && clientSupportedEncodings.contains(encoding)) {
                return encoding;
            }
        }

        return null;
    }

    @Deprecated
    private static List<ContentCodec> parseAcceptEncoding(@Nullable final CharSequence acceptEncodingHeaderValue,
                                                          final List<ContentCodec> allowedEncodings) {

        if (acceptEncodingHeaderValue == null || acceptEncodingHeaderValue.length() == 0) {
            return NONE_CONTENT_ENCODING_SINGLETON;
        }

        List<ContentCodec> knownEncodings = new ArrayList<>();
        List<CharSequence> acceptEncodingValues = split(acceptEncodingHeaderValue, ',', true);
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
     * {@link Identity#identity()} regardless of its presence in the {@code allowedList}.
     * @deprecated Use {@link #encodingForRaw(List, Function, CharSequence)}.
     * @param allowedList the source list to find a matching codec from.
     * @param name the codec name used for the equality predicate.
     * @return a codec from the allowed-list that name matches the {@code name}.
     */
    @Deprecated
    @Nullable
    public static ContentCodec encodingFor(final Collection<ContentCodec> allowedList,
                                           @Nullable final CharSequence name) {
        requireNonNull(allowedList);
        if (name == null || name.length() == 0) {
            return null;
        }

        // Identity is always supported, regardless of its presence in the allowed-list
        if (CharSequences.contentEquals(name, identity().name())) {
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

    /**
     * Get the first encoding that matches {@code name} from {@code supportedEncoders}.
     * @param supportedEncoders The {@link List} of supported encoders.
     * @param messageEncodingFunc A means to access the supported encoding name from an element in
     * {@code supportedEncoders}.
     * @param name The encoding name.
     * @param <T> The type containing the encoder.
     * @return the first encoding that matches {@code name} from {@code supportedEncoders}.
     */
    @Nullable
    public static <T> T encodingForRaw(final List<T> supportedEncoders,
                                       final Function<T, CharSequence> messageEncodingFunc,
                                       final CharSequence name) {
        for (T allowed : supportedEncoders) {
            // Encoding values can potentially included compression configurations, we only match on the type
            if (startsWith(name, messageEncodingFunc.apply(allowed))) {
                return allowed;
            }
        }

        return null;
    }

    private static boolean startsWith(final CharSequence string, final CharSequence prefix) {
        return regionMatches(string, true, 0, prefix, 0, prefix.length());
    }
}
