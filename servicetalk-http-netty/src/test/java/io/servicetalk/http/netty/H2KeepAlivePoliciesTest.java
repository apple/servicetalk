/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.netty.H2KeepAlivePolicies.KeepAlivePolicyBuilder;
import io.servicetalk.http.netty.H2ProtocolConfig.KeepAlivePolicy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static java.time.Duration.ofDays;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class H2KeepAlivePoliciesTest {

    private static final KeepAlivePolicy DEFAULT = new KeepAlivePolicyBuilder().build();

    @ParameterizedTest(name = "{displayName} [{index}] millis={0}")
    @ValueSource(longs = {-1, 0, 999})
    void invalidIdleDuration(long millis) {
        assertThrows(IllegalArgumentException.class, () -> H2KeepAlivePolicies.whenIdleFor(ofMillis(millis)));
        assertThrows(IllegalArgumentException.class,
                () -> H2KeepAlivePolicies.whenIdleFor(ofMillis(millis), DEFAULT.ackTimeout()));
        assertThrows(IllegalArgumentException.class,
                () -> new KeepAlivePolicyBuilder().idleDuration(ofMillis(millis)).build());
    }

    @ParameterizedTest(name = "{displayName} [{index}] millis={0}")
    @ValueSource(longs = {-1, 0})
    void invalidAckTimeout(long millis) {
        assertThrows(IllegalArgumentException.class,
                () -> H2KeepAlivePolicies.whenIdleFor(DEFAULT.idleDuration(), ofMillis(millis)));
        assertThrows(IllegalArgumentException.class,
                () -> new KeepAlivePolicyBuilder().ackTimeout(ofMillis(millis)).build());
    }

    @Test
    void invalidDurationCombination() {
        assertThrows(IllegalArgumentException.class,
                () -> H2KeepAlivePolicies.whenIdleFor(ofSeconds(1), ofSeconds(2)));
        assertThrows(IllegalArgumentException.class,
                () -> new KeepAlivePolicyBuilder().idleDuration(ofSeconds(1)).ackTimeout(ofSeconds(2)).build());
    }

    @Test
    void whenIdleFor1Arg() {
        assertPolicy(H2KeepAlivePolicies.whenIdleFor(ofSeconds(1)), ofSeconds(1), ofSeconds(1));
        assertPolicy(H2KeepAlivePolicies.whenIdleFor(DEFAULT.ackTimeout()),
                DEFAULT.ackTimeout(), DEFAULT.ackTimeout());
        assertPolicy(H2KeepAlivePolicies.whenIdleFor(DEFAULT.ackTimeout().plusMillis(1)),
                DEFAULT.ackTimeout().plusMillis(1), DEFAULT.ackTimeout());
        assertPolicy(H2KeepAlivePolicies.whenIdleFor(DEFAULT.idleDuration()),
                DEFAULT.idleDuration(), DEFAULT.ackTimeout());
        assertPolicy(H2KeepAlivePolicies.whenIdleFor(ofDays(30)), ofDays(30), DEFAULT.ackTimeout());
    }

    @Test
    void whenIdleFor2Args() {
        assertPolicy(H2KeepAlivePolicies.whenIdleFor(ofSeconds(1), ofSeconds(1)), ofSeconds(1), ofSeconds(1));
        assertPolicy(H2KeepAlivePolicies.whenIdleFor(ofSeconds(2), ofSeconds(1)), ofSeconds(2), ofSeconds(1));
        assertPolicy(H2KeepAlivePolicies.whenIdleFor(DEFAULT.ackTimeout(), DEFAULT.ackTimeout()),
                DEFAULT.ackTimeout(), DEFAULT.ackTimeout());
        assertPolicy(H2KeepAlivePolicies.whenIdleFor(DEFAULT.idleDuration(), DEFAULT.idleDuration()),
                DEFAULT.idleDuration(), DEFAULT.idleDuration());
        assertPolicy(H2KeepAlivePolicies.whenIdleFor(ofDays(30), ofDays(30)), ofDays(30), ofDays(30));
    }

    @Test
    void buildCustom() {
        assertPolicy(new KeepAlivePolicyBuilder().idleDuration(ofSeconds(1)).ackTimeout(ofSeconds(1)).build(),
                ofSeconds(1), ofSeconds(1));
        assertPolicy(new KeepAlivePolicyBuilder().idleDuration(ofSeconds(2)).ackTimeout(ofSeconds(1)).build(),
                ofSeconds(2), ofSeconds(1));
        assertPolicy(new KeepAlivePolicyBuilder()
                        .idleDuration(DEFAULT.ackTimeout()).ackTimeout(DEFAULT.ackTimeout()).build(),
                DEFAULT.ackTimeout(), DEFAULT.ackTimeout());
        assertPolicy(new KeepAlivePolicyBuilder()
                        .idleDuration(DEFAULT.idleDuration()).ackTimeout(DEFAULT.idleDuration()).build(),
                DEFAULT.idleDuration(), DEFAULT.idleDuration());
        assertPolicy(new KeepAlivePolicyBuilder().idleDuration(ofDays(30)).ackTimeout(ofDays(30)).build(),
                ofDays(30), ofDays(30));
    }

    @Test
    void buildWithIdleDuration() {
        assertPolicy(new KeepAlivePolicyBuilder().idleDuration(ofSeconds(1)).build(), ofSeconds(1), ofSeconds(1));
        assertPolicy(new KeepAlivePolicyBuilder().idleDuration(DEFAULT.ackTimeout()).build(),
                DEFAULT.ackTimeout(), DEFAULT.ackTimeout());
        assertPolicy(new KeepAlivePolicyBuilder().idleDuration(DEFAULT.ackTimeout().plusMillis(1)).build(),
                DEFAULT.ackTimeout().plusMillis(1), DEFAULT.ackTimeout());
        assertPolicy(new KeepAlivePolicyBuilder().idleDuration(DEFAULT.idleDuration()).build(),
                DEFAULT.idleDuration(), DEFAULT.ackTimeout());
        assertPolicy(new KeepAlivePolicyBuilder().idleDuration(ofDays(30)).build(), ofDays(30), DEFAULT.ackTimeout());
    }

    @Test
    void buildWithAckTimeout() {
        assertPolicy(new KeepAlivePolicyBuilder().ackTimeout(ofSeconds(1)).build(),
                DEFAULT.idleDuration(), ofSeconds(1));
        assertPolicy(new KeepAlivePolicyBuilder().ackTimeout(DEFAULT.ackTimeout()).build(),
                DEFAULT.idleDuration(), DEFAULT.ackTimeout());
        assertPolicy(new KeepAlivePolicyBuilder().ackTimeout(DEFAULT.ackTimeout().plusSeconds(1)).build(),
                DEFAULT.idleDuration(), DEFAULT.ackTimeout().plusSeconds(1));
        assertPolicy(new KeepAlivePolicyBuilder().ackTimeout(DEFAULT.idleDuration()).build(),
                DEFAULT.idleDuration(), DEFAULT.idleDuration());
    }

    @Test
    void buildWithoutActiveStreams() {
        assertPolicy(new KeepAlivePolicyBuilder()
                        .idleDuration(DEFAULT.idleDuration())
                        .ackTimeout(DEFAULT.ackTimeout())
                        .withoutActiveStreams(true)
                        .build(),
                DEFAULT.idleDuration(), DEFAULT.ackTimeout(), true);
    }

    @Test
    void buildAckTimeoutAlwaysAdjustedByDefault() {
        KeepAlivePolicyBuilder builder = new KeepAlivePolicyBuilder();
        assertPolicy(builder.idleDuration(ofSeconds(1)).build(), ofSeconds(1), ofSeconds(1));
        assertPolicy(builder.idleDuration(ofSeconds(2)).build(), ofSeconds(2), ofSeconds(2));
        builder.ackTimeout(ofSeconds(1));
        assertPolicy(builder.idleDuration(ofSeconds(3)).build(), ofSeconds(3), ofSeconds(1));
    }

    private static void assertPolicy(KeepAlivePolicy policy, Duration idleDuration, Duration ackTimeout) {
        assertPolicy(policy, idleDuration, ackTimeout, DEFAULT.withoutActiveStreams());
    }

    private static void assertPolicy(KeepAlivePolicy policy,
            Duration idleDuration, Duration ackTimeout, boolean withoutActiveStreams) {
        assertThat(policy.idleDuration(), is(equalTo(idleDuration)));
        assertThat(policy.ackTimeout(), is(equalTo(ackTimeout)));
        assertThat(policy.withoutActiveStreams(), is(withoutActiveStreams));
    }
}
