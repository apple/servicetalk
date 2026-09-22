/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import org.junit.jupiter.api.Test;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutlierDetectorConfigTest {

    @Test
    void defaultMaxEjectionTime() {
        assertThat(new OutlierDetectorConfig.Builder().build().maxEjectionTime(), equalTo(ofSeconds(300)));
    }

    @Test
    void defaultMaxEjectionTimeIsAtLeastBaseEjectionTime() {
        OutlierDetectorConfig config = new OutlierDetectorConfig.Builder()
                .baseEjectionTime(ofSeconds(400))
                .build();
        assertThat(config.maxEjectionTime(), equalTo(ofSeconds(400)));
    }

    @Test
    void copiedDefaultMaxEjectionTimeFollowsBaseEjectionTime() {
        OutlierDetectorConfig config = new OutlierDetectorConfig.Builder(new OutlierDetectorConfig.Builder().build())
                .baseEjectionTime(ofSeconds(400))
                .build();
        assertThat(config.maxEjectionTime(), equalTo(ofSeconds(400)));
    }

    @Test
    void explicitMaxEjectionTimeLessThanBaseEjectionTimeIsRejected() {
        OutlierDetectorConfig.Builder builder = new OutlierDetectorConfig.Builder()
                .baseEjectionTime(ofSeconds(30))
                .maxEjectionTime(ofSeconds(10));
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void zeroMaxEjectionTimeIsRejected() {
        OutlierDetectorConfig.Builder builder = new OutlierDetectorConfig.Builder()
                .maxEjectionTime(ZERO);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void copiedExplicitMaxEjectionTimeLessThanRaisedBaseEjectionTimeIsRejected() {
        OutlierDetectorConfig config = new OutlierDetectorConfig.Builder()
                .maxEjectionTime(ofSeconds(300))
                .build();
        OutlierDetectorConfig.Builder builder = new OutlierDetectorConfig.Builder(config)
                .baseEjectionTime(ofSeconds(400));
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void explicitMaxEjectionTimeEqualToBaseEjectionTimeIsAllowed() {
        OutlierDetectorConfig config = new OutlierDetectorConfig.Builder()
                .baseEjectionTime(ofSeconds(30))
                .maxEjectionTime(ofSeconds(30))
                .build();
        assertThat(config.maxEjectionTime(), equalTo(ofSeconds(30)));
    }
}
