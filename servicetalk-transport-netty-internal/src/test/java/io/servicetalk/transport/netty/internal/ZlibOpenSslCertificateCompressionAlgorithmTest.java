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
import io.servicetalk.transport.api.CertificateCompressionException;

import io.netty.handler.ssl.OpenSslCertificateCompressionAlgorithm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// SEPARATE_THREAD is required so the timeout can preempt a hang inside the non-interruptible
// JNI Inflater.inflate() call — the project-wide default uses SAME_THREAD which cannot.
@Timeout(value = 5, threadMode = ThreadMode.SEPARATE_THREAD)
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

    @Test
    void decompressTruncatedStreamDoesNotHang() throws Exception {
        byte[] originalCert = inputStreamToArray(DefaultTestCerts.loadServerPem());
        byte[] compressed = ZlibOpenSslCertificateCompressionAlgorithm.INSTANCE.compress(null, originalCert);
        byte[] truncated = Arrays.copyOf(compressed, compressed.length - 4);

        assertThrows(CertificateCompressionException.class, () ->
                ZlibOpenSslCertificateCompressionAlgorithm.INSTANCE.decompress(
                        null, originalCert.length, truncated));
    }

    @Test
    void decompressFdictStreamDoesNotHang() {
        byte[] payload = "hello-certificate-payload".getBytes(StandardCharsets.UTF_8);
        byte[] dictionary = "dict".getBytes(StandardCharsets.UTF_8);

        Deflater deflater = new Deflater();
        deflater.setDictionary(dictionary);
        deflater.setInput(payload);
        deflater.finish();
        byte[] buffer = new byte[payload.length + dictionary.length + 64];
        int written = 0;
        while (!deflater.finished()) {
            written += deflater.deflate(buffer, written, buffer.length - written);
        }
        deflater.end();
        byte[] compressed = Arrays.copyOf(buffer, written);

        assertThrows(CertificateCompressionException.class, () ->
                ZlibOpenSslCertificateCompressionAlgorithm.INSTANCE.decompress(
                        null, payload.length, compressed));
    }

    @Test
    void decompressUnderdeclaredUncompressedLenDoesNotHang() throws Exception {
        byte[] originalCert = inputStreamToArray(DefaultTestCerts.loadServerPem());
        byte[] compressed = ZlibOpenSslCertificateCompressionAlgorithm.INSTANCE.compress(null, originalCert);

        assertThrows(CertificateCompressionException.class, () ->
                ZlibOpenSslCertificateCompressionAlgorithm.INSTANCE.decompress(
                        null, originalCert.length - 1, compressed));
    }
}
