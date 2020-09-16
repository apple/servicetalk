/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.uds.blocking;

import io.servicetalk.transport.api.DomainSocketAddress;

import java.io.File;

public final class UdsUtils {
    /**
     * Create a {@link DomainSocketAddress} that is backed by a fixed file such that different JVMs
     * can use it to communicate via UDS.
     * @return a {@link DomainSocketAddress} that is backed by a fixed file such that different JVMs
     * can use it to communicate via UDS.
     */
    public static DomainSocketAddress udsAddress() {
        final String tempDirProp = "java.io.tmpdir";
        final String tempDir = System.getProperty(tempDirProp);
        if (tempDir == null) {
            throw new IllegalStateException("unable to find " + tempDirProp + " in System properties");
        }
        return new DomainSocketAddress(new File(tempDir + "servicetalk.uds"));
    }
}
