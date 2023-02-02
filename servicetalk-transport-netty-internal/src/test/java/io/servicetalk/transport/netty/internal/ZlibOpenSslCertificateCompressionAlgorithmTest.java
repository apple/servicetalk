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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.test.resources.DefaultTestCerts;

import io.netty.handler.ssl.OpenSslCertificateCompressionAlgorithm;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ZlibOpenSslCertificateCompressionAlgorithmTest {

    /**
     * Simple round-trip test for the happy path as a sanity check.
     */
    @Test
    void compressAndDecompressCertificate() throws Exception {
        byte[] originalCert = inputStreamToArray(DefaultTestCerts.loadServerPem());

        OpenSslCertificateCompressionAlgorithm algorithm = ZlibOpenSslCertificateCompressionAlgorithm.INSTANCE;
        byte[] compressedCert = algorithm.compress(null, originalCert);
        byte[] uncompressedCert = algorithm.decompress(null, originalCert.length, compressedCert);

        assertArrayEquals(originalCert, uncompressedCert);
    }

    private static byte[] inputStreamToArray(final InputStream inputStream) throws Exception {
        byte[] buffer = new byte[1000];
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int temp;
        while ((temp = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, temp);
        }
        return outputStream.toByteArray();
    }
}
