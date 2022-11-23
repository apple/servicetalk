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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

class DefaultTestCertsTest {
    private static final char[] PASSWORD = "changeit".toCharArray();

    @Test
    void loadServerKey() throws Exception {
        assertThat(loadPrivateKey(DefaultTestCerts.loadServerKey()), notNullValue());
    }

    @Test
    void loadServerPem() throws Exception {
        assertThat(loadCertificate(DefaultTestCerts.loadServerPem()), notNullValue());
    }

    @Test
    void loadServerCAPem() throws Exception {
        assertThat(loadCertificate(DefaultTestCerts.loadServerCAPem()), notNullValue());
    }

    @Test
    void loadServerP12() throws Exception {
        assertThat(loadKeystore(DefaultTestCerts.loadServerP12()), notNullValue());
    }

    @Test
    void loadClientKey() throws Exception {
        assertThat(loadPrivateKey(DefaultTestCerts.loadClientKey()), notNullValue());
    }

    @Test
    void loadClientPem() throws Exception {
        assertThat(loadCertificate(DefaultTestCerts.loadClientPem()), notNullValue());
    }

    @Test
    void loadClientCAPem() throws Exception {
        assertThat(loadCertificate(DefaultTestCerts.loadClientCAPem()), notNullValue());
    }

    @Test
    void loadClientP12() throws Exception {
        assertThat(loadKeystore(DefaultTestCerts.loadClientP12()), notNullValue());
    }

    @Test
    void loadTrustP12() throws Exception {
        assertThat(loadKeystore(DefaultTestCerts.loadTruststoreP12()), notNullValue());
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
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, US_ASCII))) {
            StringBuilder sb = new StringBuilder(2048);
            String line = br.readLine();
            if (!"-----BEGIN PRIVATE KEY-----".equals(line)) {
                sb.append(line);
            }
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            int endKeyIndex = sb.lastIndexOf("-----END PRIVATE KEY-----");
            String privateKeyPEM = endKeyIndex > 0 ? sb.substring(0, endKeyIndex) : sb.toString();
            byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            return factory.generatePrivate(keySpec);
        }
    }
}
