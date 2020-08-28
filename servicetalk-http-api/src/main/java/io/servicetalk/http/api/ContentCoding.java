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
package io.servicetalk.http.api;

/**
 * API for HTTP <a href="https://tools.ietf.org/html/rfc7231#section-3.1.2.1">Content Codings</a>.
 */
public interface ContentCoding {

    /**
     * A string representation for the content coding.
     *
     * @return a string representation for the content coding.
     */
    String name();

    /**
     * The codec that supports encoding/decoding for this type of content coding.
     *
     * @return a shared instance of the codec for that content coding
     */
    ContentCodec codec();
}
