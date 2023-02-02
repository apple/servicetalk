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
 * A factory to create {@link CertificateCompressionAlgorithm Certificate Compression Algorithms}.
 */
public final class CertificateCompressionAlgorithms {

    /**
     * Unique ZLIB algorithm ID as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc8879#name-compression-algorithms">RFC8879</a>.
     */
    public static final int ZLIB_ALGORITHM_ID = 0x01;

    /**
     * This just satisfies the marker interface which allows to extend the API in the future.
     * <p>
     * Currently, the actual ZLIB implementation can be found in transport-netty-internal.
     */
    private static final CertificateCompressionAlgorithm ZLIB = () -> ZLIB_ALGORITHM_ID;

    private CertificateCompressionAlgorithms() {
    }

    /**
     * Get the default ZLIB {@link CertificateCompressionAlgorithm}.
     *
     * @return the default ZLIB {@link CertificateCompressionAlgorithm}.
     */
    public static CertificateCompressionAlgorithm zlibDefault() {
        return ZLIB;
    }
}
