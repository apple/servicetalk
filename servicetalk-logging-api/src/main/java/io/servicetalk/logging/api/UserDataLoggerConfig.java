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
package io.servicetalk.logging.api;

import java.util.function.BooleanSupplier;

/**
 * {@link LoggerConfig} used in areas that can filter logging output to include user data or not.
 */
public interface UserDataLoggerConfig extends LoggerConfig {
    /**
     * Determine if user data (e.g. data, headers, etc.) should be included in logs.
     * @return {@code true} to include user data (e.g. data, headers, etc.). {@code false} to exclude user
     * data and log only network events.
     */
    BooleanSupplier logUserData();
}
