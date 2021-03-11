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

import java.io.InputStream;

/**
 * Provides a set of certificates useful for tests that require SSL.
 */
public final class DefaultTestCerts {

    private DefaultTestCerts() {
        // No instances
    }

    /**
     * Load the server private key.
     *
     * @return an {@link InputStream} from the server private key file.
     */
    public static InputStream loadServerKey() {
        return DefaultTestCerts.class.getResourceAsStream("localhost_server.key");
    }

    /**
     * Load the server certificate chain file.
     *
     * @return an {@link InputStream} from the server certificate chain file.
     */
    public static InputStream loadServerPem() {
        return DefaultTestCerts.class.getResourceAsStream("localhost_server.pem");
    }

    /**
     * Get the hostname contained within {@link #loadServerPem()} which should match for client hostname verification.
     * @return hostname contained within {@link #loadServerPem()} which should match for client hostname verification.
     */
    public static String serverPemHostname() {
        return "localhost";
    }

    /**
     * Load the certificate of the Certificate Authority used to sign the {@link #loadServerPem()}.
     *
     * @return an {@link InputStream} whose contents is the certificate of the Certificate Authority used to sign the
     * {@link #loadServerPem()}.
     */
    public static InputStream loadServerCAPem() {
        return DefaultTestCerts.class.getResourceAsStream("server_ca.pem");
    }

    /**
     * Load the client private key.
     *
     * @return an {@link InputStream} from the client private key file.
     */
    public static InputStream loadClientKey() {
        return DefaultTestCerts.class.getResourceAsStream("localhost_client.key");
    }

    /**
     * Load the client certificate chain file.
     *
     * @return an {@link InputStream} from the client certificate chain file.
     */
    public static InputStream loadClientPem() {
        return DefaultTestCerts.class.getResourceAsStream("localhost_client.pem");
    }

    /**
     * Load the certificate of the Certificate Authority used to sign the {@link #loadClientPem()}.
     *
     * @return an {@link InputStream} whose contents is the certificate of the Certificate Authority used to sign the
     * {@link #loadClientPem()}.
     */
    public static InputStream loadClientCAPem() {
        return DefaultTestCerts.class.getResourceAsStream("client_ca.pem");
    }
}
