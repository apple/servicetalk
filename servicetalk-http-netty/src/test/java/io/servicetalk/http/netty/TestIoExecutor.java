/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.DelegatingExecutor;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.transport.api.IoExecutor;

final class TestIoExecutor extends DelegatingExecutor implements IoExecutor {

    static final IoExecutor INSTANCE = new TestIoExecutor(new TestExecutor());

    final TestExecutor underlying;

    private TestIoExecutor(TestExecutor underlying) {
        super(underlying);
        this.underlying = underlying;
    }

    @Override
    public boolean isUnixDomainSocketSupported() {
        return false;
    }

    @Override
    public boolean isFileDescriptorSocketAddressSupported() {
        return false;
    }

    @Override
    public boolean isIoThreadSupported() {
        return false;
    }
}
