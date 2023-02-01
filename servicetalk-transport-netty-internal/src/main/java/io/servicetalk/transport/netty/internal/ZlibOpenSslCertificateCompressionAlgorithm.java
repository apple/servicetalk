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

import io.servicetalk.transport.api.CertificateCompressionAlgorithms;
import io.servicetalk.transport.api.CertificateCompressionException;

import io.netty.handler.ssl.OpenSslCertificateCompressionAlgorithm;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import javax.net.ssl.SSLEngine;

/**
 * Implements ZLIB compression and decompression of OpenSSL certificates.
 *
 * @see <a href="https://www.zlib.net">ZLIB Website</a>
 */
final class ZlibOpenSslCertificateCompressionAlgorithm implements OpenSslCertificateCompressionAlgorithm {

    static final ZlibOpenSslCertificateCompressionAlgorithm INSTANCE = new ZlibOpenSslCertificateCompressionAlgorithm();

    private ZlibOpenSslCertificateCompressionAlgorithm() {
    }

    @Override
    public byte[] compress(final SSLEngine engine, final byte[] uncompressedCertificate) throws Exception {
        int uncompressedLength = uncompressedCertificate.length;
        if (uncompressedLength == 0) {
            return uncompressedCertificate;
        }

        final Deflater deflater = new Deflater();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            deflater.setInput(uncompressedCertificate);
            deflater.finish();

            // This calculation (which is also used inside Netty) comes from the C library which describes it as:
            // "...must be at least 0.1% larger than the uncompressed length plus 12 bytes..."
            int bufferSizeEstimate = (int) Math.ceil(uncompressedLength * 1.001) + 12;

            byte[] compressionBuffer = new byte[bufferSizeEstimate];
            while (!deflater.finished()) {
                int bytesCompressed = deflater.deflate(compressionBuffer);
                if (bytesCompressed > 0) {
                    outputStream.write(compressionBuffer, 0, bytesCompressed);
                }
            }
            return outputStream.toByteArray();
        } catch (Exception cause) {
            throw new CertificateCompressionException("Failed to compress certificate with ZLIB", cause);
        } finally {
            deflater.end();
        }
    }

    @Override
    public byte[] decompress(final SSLEngine engine, final int uncompressedLen, final byte[] compressedCertificate)
            throws Exception {
        if (compressedCertificate.length == 0) {
            return compressedCertificate;
        }

        final Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressedCertificate);

            // We do not need to create a ByteArrayOutputStream like we do on compression, since we know the maximum
            // size on decompress is provided as an argument and anything larger would be a violation of the RFC so
            // it will be rejected with an Exception.
            byte[] output = new byte[uncompressedLen];
            int bytesWritten = 0;
            while (!inflater.finished()) {
                int decompressedBytes = inflater.inflate(output, bytesWritten, uncompressedLen - bytesWritten);
                bytesWritten += decompressedBytes;
                if (bytesWritten > uncompressedLen) {
                    throw new CertificateCompressionException("Number of bytes written (" + bytesWritten + ") " +
                            "exceeds the uncompressed certificate length (" + uncompressedLen + ")");
                }
            }
            return output;
        } catch (Exception cause) {
            throw new CertificateCompressionException("Failed to decompress certificate with ZLIB", cause);
        } finally {
            inflater.end();
        }
    }

    @Override
    public int algorithmId() {
        return CertificateCompressionAlgorithms.ZLIB_ALGORITHM_ID;
    }
}
