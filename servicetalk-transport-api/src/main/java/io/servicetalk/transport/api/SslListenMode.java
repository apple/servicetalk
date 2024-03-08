/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.api;

/**
 * Describes the different modes on how the server will accept incoming connections when SSL is enabled.
 */
public enum SslListenMode {
    /**
     * If this mode is selected and SSL is enabled, only encrypted connections will be accepted.
     */
    SSL_REQUIRED,

    /**
     * If this mode is selected and SSL is enabled, the server will accept both encrypted and unencrypted connections.
     */
    SSL_OPTIONAL
}
