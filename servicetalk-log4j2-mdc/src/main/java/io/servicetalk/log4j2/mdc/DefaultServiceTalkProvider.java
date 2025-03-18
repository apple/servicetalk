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

import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.spi.Provider;

import java.lang.reflect.Field;

/**
 * Service loaded MDC thread context map implementation.
 *
 * This class is service loaded by log4j2 and is used to provide an MDC context map implementation that will work
 * with ServiceTalks reactive primitives.
 */
public class DefaultServiceTalkProvider extends Provider {

    private static final String DEFAULT_CURRENT_VERSION = "2.6.0";

    /**
     * Create a new DefaultServiceTalkProvider.
     *
     * The zero-argument constructor is required by the service loading mechanism.
     */
    public DefaultServiceTalkProvider() {
        super(20, getCurrentVersion(), Log4jContextFactory.class, DefaultServiceTalkThreadContextMap.class);
    }

    private static String getCurrentVersion() {
        // The CURRENT_VERSION field is only available as of 2.24.0. Once we upgrade to 2.24+ we can drop this.
        try {
            Field field = Provider.class.getField("CURRENT_VERSION");
            return (String) field.get(null /* static field */);
        } catch (Exception ex) {
            return DEFAULT_CURRENT_VERSION;
        }
    }
}
