/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.api;

/**
 * Represents an algorithm that can compress and decompress SSL certificates.
 * <p>
 * This feature is defined in <a href="https://www.rfc-editor.org/rfc/rfc8879">RFC 8879</a> as an optional extension
 * to TLS 1.3 and later.
 */
public interface CertificateCompressionAlgorithm {

    /**
     * Get the unique identifier for this algorithm.
     * <p>
     * RFC 8879 defines unique identifiers for each algorithm type and the returned value must reflect those defined
     * in <a href="https://www.rfc-editor.org/rfc/rfc8879#section-7.3">the RFC</a>.
     *
     * @return the unique identifier for this algorithm.
     */
    int algorithmId();
}
