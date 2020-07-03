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
package io.servicetalk.transport.api;

/**
 * An observer interface that provides visibility into events associated with a single network connection.
 */
public interface ConnectionObserver {

    /**
     * Notifies when the connection reads a chunk of data.
     *
     * @param size size of the data chunk read
     */
    void dataRead(int size);

    /**
     * Notifies when the connection writes a chunk of data.
     *
     * @param size size of the data chunk written
     */
    void dataWritten(int size);

    /**
     * Notifies when flush operation is made on the connection. The flush operation will try to flush out all previous
     * written messages that are pending.
     */
    void flushed();

    /**
     * Notifies when the connection is closed due to an {@link Throwable error}.
     *
     * @param error an occurred error
     */
    void connectionClosed(Throwable error);

    /**
     * Notifies when the connection is closed.
     */
    void connectionClosed();
}
