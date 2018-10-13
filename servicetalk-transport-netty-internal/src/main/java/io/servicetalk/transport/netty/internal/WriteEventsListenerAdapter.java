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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.transport.netty.internal.FlushStrategy.WriteEventsListener;

/**
 * A {@link WriteEventsListener} that by default does nothing for all method. This can be used to selectively implement
 * relevant methods.
 */
public abstract class WriteEventsListenerAdapter implements WriteEventsListener {

    @Override
    public void writeStarted() {
        // No op
    }

    @Override
    public void itemWritten() {
        // No op
    }

    @Override
    public void writeTerminated() {
        // No op
    }

    @Override
    public void writeCancelled() {
        // No op
    }
}
