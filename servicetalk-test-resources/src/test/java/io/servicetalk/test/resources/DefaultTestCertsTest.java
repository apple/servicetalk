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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

// Not a particularly robust test, but ensures the file is found and loaded.
// If we change algorithms/settings this size may need to change.
class DefaultTestCertsTest {

    @Test
    void loadServerKey() throws Exception {
        String contents = readFully(DefaultTestCerts.loadServerKey());
        assertEquals(1707, contents.length());
    }

    @Test
    void loadServerPem() throws Exception {
        String contents = readFully(DefaultTestCerts.loadServerPem());
        assertEquals(992, contents.length());
    }

    @Test
    void loadServerCAPem() throws Exception {
        String contents = readFully(DefaultTestCerts.loadServerCAPem());
        assertEquals(1020, contents.length());
    }

    @Test
    void loadClientKey() throws Exception {
        String contents = readFully(DefaultTestCerts.loadClientKey());
        assertEquals(1707, contents.length());
    }

    @Test
    void loadClientPem() throws Exception {
        String contents = readFully(DefaultTestCerts.loadClientPem());
        assertEquals(992, contents.length());
    }

    @Test
    void loadClientCAPem() throws Exception {
        String contents = readFully(DefaultTestCerts.loadClientCAPem());
        assertEquals(1020, contents.length());
    }

    private String readFully(final InputStream inputStream) throws IOException {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(inputStream, US_ASCII))) {
            return buffer.lines().collect(joining("\n"));
        }
    }
}
