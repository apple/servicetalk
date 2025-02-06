/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

/**
 * Functionality related to capturing thread-local like context for later restoration across async boundaries.
 */
interface ContextCaptureProvider {

    /**
     * Save existing context in preparation for an asynchronous thread jump.
     *
     * Note that this can do more than just package up the ServiceTalk {@link AsyncContext} and could be enhanced or
     * wrapped to bundle up additional contexts such as the OpenTelemetry or grpc contexts.
     * @return the saved context state that may be restored later.
     */
    CapturedContext captureContext();

    /**
     * Save a copy of the existing context in preparation for an asynchronous thread jump.
     *
     * Note that this can do more than just package up the ServiceTalk {@link AsyncContext} and could be enhanced or
     * wrapped to bundle up additional contexts such as the OpenTelemetry or grpc contexts.
     * @return the copied saved context state that may be restored later.
     */
    CapturedContext captureContextCopy();
}
