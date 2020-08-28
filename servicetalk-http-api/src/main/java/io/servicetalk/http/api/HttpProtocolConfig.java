/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import java.util.Set;

/**
 * Defines configuration options for HTTP protocol versions.
 */
public interface HttpProtocolConfig {

    /**
     * TLS Application-Layer Protocol Negotiation (ALPN) Protocol ID of the protocol this configuration is for.
     *
     * @return string representation of ALPN Identification Sequence
     * @see <a href=
     * "https://www.iana.org/assignments/tls-extensiontype-values/tls-extensiontype-values.xhtml#alpn-protocol-ids">
     * TLS Application-Layer Protocol Negotiation (ALPN) Protocol IDs</a>
     */
    String alpnId();

    /**
     * {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding HTTP messages.
     *
     * @return {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding HTTP messages
     */
    HttpHeadersFactory headersFactory();

    /**
     * A collection of all {@link ContentCoding}s the endpoint supports.
     * The list will be advertised as part of the Accept-Encoding header.
     *
     * @return The list of supported {@link ContentCoding}s for this endpoint.
     * @see <a href="https://tools.ietf.org/html/rfc7231#page-41">Accept-Encodings</a>
     */
    Set<ContentCoding> supportedEncodings();
}
