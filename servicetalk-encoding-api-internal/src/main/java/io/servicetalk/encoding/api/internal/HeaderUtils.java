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
     * @param acceptEncodingHeaderValue The accept encoding header value.
     * @param serverSupportedEncodings The server supported codings as configured.
     * @return The {@link ContentCodec} that satisfies both client and server needs,
     * null if none found or matched to {@link Identity#identity()}
     * @deprecated Use {@link #negotiateAcceptedEncodingRaw(CharSequence, List, Function)}.
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
     * <p>
     * The {@code qvalue} of each entry is taken into account as described in
     * <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-5.3.4">RFC 7231, section 5.3.4</a>: the entry
     * with the highest {@code qvalue} that matches a supported encoder wins, entries with equal {@code qvalue}s are
     * prioritized by the order in which they appear in the header, entries without a {@code qvalue} default to
     * {@code 1}, and entries with {@code q=0} are "not acceptable" and never selected. The {@code '*'} wild card
     * matches the first supported encoder that is not explicitly listed in the header.
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

        T best = null;
        int bestQValue = 0;
        int wildcardQValue = 0;
        final int length = acceptEncodingHeaderValue.length();
        int i = 0;
        while (i < length) {
            // Find the next comma separated value.
            int j = CharSequences.indexOf(acceptEncodingHeaderValue, ',', i);
            if (j < 0) {
                j = length;
            }
            final int start = skipSpaces(acceptEncodingHeaderValue, i, j);
            final int end = trimTrailingSpaces(acceptEncodingHeaderValue, start, j);
            i = j + 1;
            if (start == end) {
                // Empty list elements are ignored.
                continue;
            }

            final int qValue = qValue(acceptEncodingHeaderValue, start, end);
            if (acceptEncodingHeaderValue.charAt(start) == '*') {
                // Match special case '*' wild card, resolved below once all explicitly listed values are known.
                if (wildcardQValue == 0) {
                    wildcardQValue = qValue;
                }
                continue;
            }
            if (qValue <= bestQValue) {
                continue;
            }
            // If the accepted encoding is supported, use it.
            final T supportedEncoding = findEncoder(supportedEncoders, messageEncodingFunc,
                    acceptEncodingHeaderValue, start);
            if (supportedEncoding != null) {
                best = supportedEncoding;
                bestQValue = qValue;
            }
        }

        if (wildcardQValue > bestQValue) {
            // The wild card matches any encoding that is not explicitly listed, which includes the ones with q=0.
            for (int x = 0; x < supportedEncoders.size(); ++x) {
                final T supportedEncoding = supportedEncoders.get(x);
                if (!isListed(acceptEncodingHeaderValue, messageEncodingFunc.apply(supportedEncoding))) {
                    return supportedEncoding;
                }
            }
        }
        return best;
    }

    @Nullable
    private static <T> T findEncoder(final List<T> supportedEncoders,
                                     final Function<T, CharSequence> messageEncodingFunc,
                                     final CharSequence acceptEncodingHeaderValue, final int start) {
        for (int x = 0; x < supportedEncoders.size(); ++x) {
            final T supportedEncoding = supportedEncoders.get(x);
            final CharSequence serverSupported = messageEncodingFunc.apply(supportedEncoding);
            // Use serverSupported.length() as the value may be followed by parameters.
            // All content-coding values are case-insensitive [1].
            // [1] https://datatracker.ietf.org/doc/html/rfc7231#section-3.1.2.1.
            if (regionMatches(acceptEncodingHeaderValue, true, start, serverSupported, 0, serverSupported.length())) {
                return supportedEncoding;
            }
        }
        return null;
    }

    private static boolean isListed(final CharSequence acceptEncodingHeaderValue, final CharSequence encoding) {
        final int length = acceptEncodingHeaderValue.length();
        int i = 0;
        while (i < length) {
            int j = CharSequences.indexOf(acceptEncodingHeaderValue, ',', i);
            if (j < 0) {
                j = length;
            }
            final int start = skipSpaces(acceptEncodingHeaderValue, i, j);
            i = j + 1;
            if (start < j && regionMatches(acceptEncodingHeaderValue, true, start, encoding, 0, encoding.length())) {
                return true;
            }
        }
        return false;
    }

    private static int skipSpaces(final CharSequence value, int start, final int end) {
        while (start < end && value.charAt(start) == ' ') {
            ++start;
        }
        return start;
    }

    private static int trimTrailingSpaces(final CharSequence value, final int start, int end) {
        while (end > start && value.charAt(end - 1) == ' ') {
            --end;
        }
        return end;
    }

    /**
     * Parses the {@code qvalue} of an {@code Accept-Encoding} entry.
     * <pre>
     *     weight = OWS ";" OWS "q=" qvalue
     *     qvalue = ( "0" [ "." 0*3DIGIT ] ) / ( "1" [ "." 0*3("0") ] )
     * </pre>
     * @param value The header value.
     * @param start The start index (inclusive) of the entry.
     * @param end The end index (exclusive) of the entry.
     * @return the {@code qvalue} in thousandths ({@code 0} - {@code 1000}), or {@code 1000} if the entry doesn't
     * have a (valid) {@code qvalue}.
     */
    private static int qValue(final CharSequence value, final int start, final int end) {
        int i = CharSequences.indexOf(value, ';', start);
        if (i < 0 || i >= end) {
            return 1000;
        }
        i = skipSpaces(value, i + 1, end);
        if (end - i < 3 || (value.charAt(i) != 'q' && value.charAt(i) != 'Q') || value.charAt(i + 1) != '=') {
            return 1000;
        }
        i += 2;
        final char first = value.charAt(i++);
        if (first != '0' && first != '1') {
            return 1000;
        }
        int result = (first - '0') * 1000;
        if (i < end && value.charAt(i) == '.') {
            int multiplier = 100;
            for (++i; i < end && multiplier > 0; ++i, multiplier /= 10) {
                final char c = value.charAt(i);
                if (c < '0' || c > '9') {
                    break;
                }
                result += (c - '0') * multiplier;
            }
        }
        return Math.min(result, 1000);
    }

    /**
     * Establish a commonly accepted encoding between server and client, according to the supported-encodings
     * on the server side and the incoming header on the request.
     * <p>
     * If no supported encodings are passed then the result is always {@code null}
     * Otherwise, the first matching encoding (that is NOT {@link Identity#identity()}) is preferred,
     * or {@code null} is returned.
     * @param clientSupportedEncodings The client supported codings as found in the HTTP header.
     * @param serverSupportedEncodings The server supported codings as configured.
     * @return The {@link ContentCodec} that satisfies both client and server needs,
     * null if none found or matched to {@link Identity#identity()}
     * @deprecated Use {@link #negotiateAcceptedEncodingRaw(CharSequence, List, Function)}.
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
     * @param allowedList the source list to find a matching codec from.
     * @param name the codec name used for the equality predicate.
     * @return a codec from the allowed-list that name matches the {@code name}.
     * @deprecated Use {@link #encodingForRaw(List, Function, CharSequence)}.
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
