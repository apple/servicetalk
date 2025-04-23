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
package io.servicetalk.log4j2.mdc;

import io.servicetalk.log4j2.mdc.utils.MdcCapturedContextProvider;

/**
 * Empty subclass to differentiate uses of MDC.
 * Note: this is intended to be service-loaded and not instantiated directly.
 */
public final class DefaultMdcCapturedContextProvider extends MdcCapturedContextProvider {

    /**
     * Create a new instance.
     * Note: this is intended to be service-loaded and not instantiated directly.
     */
    public DefaultMdcCapturedContextProvider() {
        super(DefaultServiceTalkThreadContextMap.class);
    }
}
