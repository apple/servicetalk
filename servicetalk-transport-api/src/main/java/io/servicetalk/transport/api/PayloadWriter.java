/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
 * An interface which mimics behavior like {@link java.io.OutputStream}, but allows for writing of Objects of type
 * {@link T}.
 * @param <T> The type of Objects to write.
 */
public interface PayloadWriter<T> extends AutoCloseable {
    /**
     * Write an Object of type {@link T}.
     * @param t the object to write.
     */
    void write(T t);

    /**
     * Attempt to flush previously written Objects to the underlying transport.
     */
    void flush();
}
