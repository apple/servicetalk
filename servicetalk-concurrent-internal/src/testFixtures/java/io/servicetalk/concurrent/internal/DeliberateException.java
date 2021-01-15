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
package io.servicetalk.concurrent.internal;

public class DeliberateException extends RuntimeException {
    private static final long serialVersionUID = 3895872583544069787L;

    public static final DeliberateException DELIBERATE_EXCEPTION = new DeliberateException(false);

    public DeliberateException() {
        super("Deliberate Exception");
    }

    private DeliberateException(boolean enableSuppression) {
        super("Deliberate Exception", null, enableSuppression, false);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
