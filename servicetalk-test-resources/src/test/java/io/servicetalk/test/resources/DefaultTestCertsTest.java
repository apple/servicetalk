/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.test.resources;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.US_ASCII;

class DefaultTestCertsTest {
    private static final char[] PASSWORD = "changeit".toCharArray();

    @Test
    void loadServerKey() throws Exception {
        loadPrivateKey(DefaultTestCerts.loadServerKey());
    }

    @Test
    void loadServerPem() throws Exception {
        loadCertificate(DefaultTestCerts.loadServerPem());
    }

    @Test
    void loadServerCAPem() throws Exception {
        loadCertificate(DefaultTestCerts.loadServerCAPem());
    }

    @Test
    void loadServerP12() throws Exception {
        loadKeystore(DefaultTestCerts.loadServerP12());
    }

    @Test
    void loadClientKey() throws Exception {
        loadPrivateKey(DefaultTestCerts.loadClientKey());
    }

    @Test
    void loadClientPem() throws Exception {
        loadCertificate(DefaultTestCerts.loadClientPem());
    }

    @Test
    void loadClientCAPem() throws Exception {
        loadCertificate(DefaultTestCerts.loadClientCAPem());
    }

    @Test
    void loadClientP12() throws Exception {
        loadKeystore(DefaultTestCerts.loadClientP12());
    }

    @Test
    void loadTrustP12() throws Exception {
        loadKeystore(DefaultTestCerts.loadTruststoreP12());
    }

    private Certificate loadCertificate(final InputStream inputStream) throws CertificateException {
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return factory.generateCertificate(inputStream);
    }

    private KeyStore loadKeystore(final InputStream inputStream) throws CertificateException, IOException,
            NoSuchAlgorithmException, KeyStoreException {
        final KeyStore keyStore = KeyStore.getInstance("pkcs12");
        keyStore.load(inputStream, PASSWORD);
        return keyStore;
    }

    private PrivateKey loadPrivateKey(final InputStream inputStream) throws IOException, NoSuchAlgorithmException,
            InvalidKeySpecException {
        final byte[] rawKey = readAllBytes(inputStream);
        final String key = new String(rawKey, US_ASCII);
        /*
         * Acknowledgement: Uses algorithm by Catalin Burcea. https://www.baeldung.com/java-read-pem-file-keys
         * Bouncy Castle Library handles removing header and decoding, use instead?
         */
        final String privateKeyPEM = key
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll(System.lineSeparator(), "")
                .replace("-----END PRIVATE KEY-----", "");
        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        return factory.generatePrivate(keySpec);
    }

    private byte[] readAllBytes(final InputStream inputStream) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] data = new byte[1024];
        int numBytesRead;
        while ((numBytesRead = inputStream.read(data, 0, data.length)) != -1) {
            outputStream.write(data, 0, numBytesRead);
        }
        outputStream.flush();
        return outputStream.toByteArray();
    }
}
