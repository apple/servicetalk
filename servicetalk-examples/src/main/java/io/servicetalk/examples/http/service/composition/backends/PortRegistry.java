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
package io.servicetalk.examples.http.service.composition.backends;

import io.servicetalk.transport.api.HostAndPort;

/**
 * A static registry for backends to port mapping. This typically will be fetched from a service discovery system
 * instead of a static registry.
 */
public final class PortRegistry {

    public static final HostAndPort RECOMMENDATIONS_BACKEND_ADDRESS = HostAndPort.of("127.0.0.1", 8081);

    public static final HostAndPort METADATA_BACKEND_ADDRESS = HostAndPort.of("127.0.0.1", 8082);

    public static final HostAndPort RATINGS_BACKEND_ADDRESS = HostAndPort.of("127.0.0.1", 8083);

    public static final HostAndPort USER_BACKEND_ADDRESS = HostAndPort.of("127.0.0.1", 8084);
}
