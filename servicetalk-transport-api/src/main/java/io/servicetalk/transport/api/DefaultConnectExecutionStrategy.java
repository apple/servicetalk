/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
 * Implements the built-in {@link ConnectExecutionStrategy} instances.
 */
enum DefaultConnectExecutionStrategy implements ConnectExecutionStrategy {
    /**
     * Does not require either connect or close offloads
     */
    CONNECT_NO_OFFLOADS {
        @Override
        public boolean isConnectOffloaded() {
            return false;
        }

        @Override
        public boolean isCloseOffloaded() {
            return false;
        }
    },
    /**
     * Offload all close invocations
     */
    CLOSE_OFFLOADED {
        @Override
        public boolean isConnectOffloaded() {
            return false;
        }

        @Override
        public boolean isCloseOffloaded() {
            return true;
        }
    },
    /**
     * Offload all connect invocations
     */
    CONNECT_OFFLOADED {
        @Override
        public boolean isConnectOffloaded() {
            return true;
        }

        @Override
        public boolean isCloseOffloaded() {
            return false;
        }
    },
    /**
     * Offload all connect and close invocations
     */
    CONNECT_CLOSE_OFFLOADED {
        @Override
        public boolean isConnectOffloaded() {
            return true;
        }

        @Override
        public boolean isCloseOffloaded() {
            return true;
        }
    }
}
