/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

/**
 *  Package private special purpose implementation for {@link HttpExecutionStrategy} to be used across programming model
 *  adapters, should not be made public. Provides a special execution strategy that overrides offloading behavior.
 *
 * @see DefaultHttpExecutionStrategy
 */
enum SpecialHttpExecutionStrategy implements HttpExecutionStrategy {

    /**
     * Enforces no offloading and maintains this even when merged.
     */
    OFFLOAD_NEVER_STRATEGY {

        @Override
        public boolean hasOffloads() {
            return false;
        }

        @Override
        public boolean isMetadataReceiveOffloaded() {
            return false;
        }

        @Override
        public boolean isDataReceiveOffloaded() {
            return false;
        }

        @Override
        public boolean isSendOffloaded() {
            return false;
        }

        /**
         * Always returns itself, the {@link #OFFLOAD_NEVER_STRATEGY} strategy.
         *
         * @param other the ignored execution strategy.
         * @return the {@link #OFFLOAD_NEVER_STRATEGY} strategy.
         */
        @Override
        public HttpExecutionStrategy merge(final HttpExecutionStrategy other) {
            return this;
        }
    },
    /**
     * "default safe" execution strategy that offloads everything and defers to other execution strategies when merged.
     */
    DEFAULT_HTTP_EXECUTION_STRATEGY {

        @Override
        public boolean hasOffloads() {
            return true;
        }

        @Override
        public boolean isMetadataReceiveOffloaded() {
            return true;
        }

        @Override
        public boolean isDataReceiveOffloaded() {
            return true;
        }

        @Override
        public boolean isSendOffloaded() {
            return true;
        }

        /**
         * Always returns the other execution strategy.
         *
         * @param other the preferred execution strategy.
         * @return the preferred execution strategy.
         */
        @Override
        public HttpExecutionStrategy merge(final HttpExecutionStrategy other) {
            return other;
        }
    }
}
