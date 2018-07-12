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
     * Load the mutual auth CA file.
     *
     * @return an {@link InputStream} from the mutual auth CA file.
     */
    public static InputStream loadMutualAuthCaPem() {
        return DefaultTestCerts.class.getResourceAsStream("mutual_auth_ca.pem");
    }
}
