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

package io.servicetalk.encoding.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class IdentityContentCodecTest {

    private static final IdentityContentCodec IDENTITY_CODEC = new IdentityContentCodec();

    private static Stream<Arguments> equalsParams() {
        return Stream.of(
                Arguments.of(IDENTITY_CODEC, new IdentityContentCodec()),
                Arguments.of(IDENTITY_CODEC, new CustomIdentityContentCodec()),
                Arguments.of(IDENTITY_CODEC, new CustomIdentityContentCodec() {
                    @Override
                    public int hashCode() {
                        return IDENTITY_CODEC.hashCode() + 1;
                    }

                    @SuppressWarnings("RedundantMethodOverride")
                    @Override
                    public boolean equals(Object other) {
                        return super.equals(other);
                    }
                }),
                Arguments.of(IDENTITY_CODEC, new NoopContentCodec(IDENTITY_CODEC.name().toString().toUpperCase()))
        );
    }

    private static Stream<Arguments> notEqualsParams() {
        return Stream.of(
                Arguments.of(IDENTITY_CODEC, new Object()),
                Arguments.of(IDENTITY_CODEC, new NoopContentCodec(IDENTITY_CODEC.name() + "different")),
                Arguments.of(IDENTITY_CODEC, null)
        );
    }

    private static Stream<Arguments> hasSameHashCodeParams() {
        return Stream.of(
                Arguments.of(IDENTITY_CODEC, new IdentityContentCodec()),
                Arguments.of(IDENTITY_CODEC, new CustomIdentityContentCodec())
        );
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @MethodSource("equalsParams")
    void equals(IdentityContentCodec identity, Object other) {
        assertThat(identity, is(equalTo(other)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @MethodSource("notEqualsParams")
    void notEquals(IdentityContentCodec identity, Object other) {
        assertThat(identity, is(not(equalTo(other))));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @MethodSource("hasSameHashCodeParams")
    void hasSameHashCode(IdentityContentCodec identity, Object other) {
        assertThat(identity.hashCode(), is(equalTo(other.hashCode())));
    }
}
