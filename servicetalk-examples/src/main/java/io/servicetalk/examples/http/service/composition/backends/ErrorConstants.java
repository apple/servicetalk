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
package io.servicetalk.examples.http.service.composition.backends;

/**
 * A collection of constants for the error triggering mechanism for demonstrating error handling.
 */
public final class ErrorConstants {
    static final String RECOMMENDATION_ERROR_USER_ID = "recommendation_error";
    static final String METADATA_ERROR_USER_ID = "metadata_error";
    static final String USER_ERROR_USER_ID = "user_error";
    static final String RATING_ERROR_USER_ID = "rating_error";

    static final int METADATA_ERROR_ENTITY_ID = -100;
    static final int USER_ERROR_ENTITY_ID = -101;
    static final int RATING_ERROR_ENTITY_ID = -102;
}
