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
package io.servicetalk.buffer.api;

@FunctionalInterface
public interface ByteProcessor {
    /**
     * Process bytes until returns {@code false}.
     *
     * @param value the {@code byte} to process.
     * @return {@code true} if the consumer wants to continue the loop and handle the next byte in the buffer.
     *         {@code false} if the consumer wants to stop handling bytes and abort the loop.
     */
    boolean process(byte value);
}
