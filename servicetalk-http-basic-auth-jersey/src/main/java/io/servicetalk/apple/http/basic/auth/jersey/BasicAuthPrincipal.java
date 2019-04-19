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
package io.servicetalk.apple.http.basic.auth.jersey;

import java.security.Principal;

import static java.util.Objects.requireNonNull;

/**
 * A generic {@link Principal} that wraps a user provided {@code UserInfo}.
 *
 * @param <UserInfo> the type of user info objects wrapped by this instance
 */
public final class BasicAuthPrincipal<UserInfo> implements Principal {
    private final UserInfo userInfo;

    BasicAuthPrincipal(final UserInfo userInfo) {
        this.userInfo = requireNonNull(userInfo);
    }

    /**
     * Get the wrapped {@code UserInfo}.
     *
     * @return the wrapped {@code UserInfo}
     */
    public UserInfo userInfo() {
        return userInfo;
    }

    @Override
    public String getName() {
        return userInfo.toString();
    }

    @Override
    public String toString() {
        return BasicAuthPrincipal.class.getSimpleName() + "{userInfo=" + userInfo + '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BasicAuthPrincipal that = (BasicAuthPrincipal) o;
        return userInfo.equals(that.userInfo);
    }

    @Override
    public int hashCode() {
        return userInfo.hashCode();
    }
}
