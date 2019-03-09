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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Executor;

/**
 * A {@link SignalOffloaderFactory} that delegates all calls to another {@link SignalOffloaderFactory}.
 */
public class DelegatingSignalOffloaderFactory implements SignalOffloaderFactory {

    private final SignalOffloaderFactory deleagte;

    /**
     * Create a new instance.
     *
     * @param delegate {@link SignalOffloaderFactory} to delegate all calls.
     */
    public DelegatingSignalOffloaderFactory(final SignalOffloaderFactory delegate) {
        this.deleagte = delegate;
    }

    @Override
    public SignalOffloader newSignalOffloader(final Executor executor) {
        return deleagte.newSignalOffloader(executor);
    }

    @Override
    public boolean hasThreadAffinity() {
        return deleagte.hasThreadAffinity();
    }
}
