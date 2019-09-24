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
package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.ScoreSupplier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class P2CSelectorTest {

    private static final List<ScoreSupplier> list20pct = DoubleStream.generate(() ->
            ThreadLocalRandom.current().nextFloat() < 0.2 ? 1 : 0)
            .limit(100).mapToObj(DoubleScore::new).collect(Collectors.toList());

    private static final List<ScoreSupplier> list50pct = DoubleStream.generate(() ->
            ThreadLocalRandom.current().nextFloat() < 0.5 ? 1 : 0)
            .limit(100).mapToObj(DoubleScore::new).collect(Collectors.toList());

    private static final List<ScoreSupplier> list80pct = DoubleStream.generate(() ->
            ThreadLocalRandom.current().nextFloat() < 0.8 ? 1 : 0)
            .limit(100).mapToObj(DoubleScore::new).collect(Collectors.toList());

    private static final List<ScoreSupplier> list100pct = DoubleStream.generate(() ->
            ThreadLocalRandom.current().nextFloat() < 1.0 ? 1 : 0)
            .limit(100).mapToObj(DoubleScore::new).collect(Collectors.toList());

    private final double pct;
    private final List<ScoreSupplier> samples;

    public P2CSelectorTest(double pct, List<ScoreSupplier> samples) {
        this.pct = pct;
        this.samples = samples;
    }

    @Parameterized.Parameters(name = "Avg Score {0}")
    public static Object[][] config() {
        return new Object[][]{{.2, list20pct}, {.5, list50pct}, {.8, list80pct}, {1.0, list100pct}};
    }

    @Test
    public void ensureBestOfTwo() {

        P2CSelector<ScoreSupplier> scoreSupplierP2CSelector = new P2CSelector<>(3);

        double sampledAvg = samples.stream().mapToDouble(ScoreSupplier::score).average().getAsDouble();
        assertThat(sampledAvg, closeTo(pct, 0.1));

        double selectedAvg = IntStream.range(0, 100)
                .mapToDouble(__ -> scoreSupplierP2CSelector.apply(samples, ___ -> true).score())
                .average().getAsDouble();
        assertThat(selectedAvg, greaterThanOrEqualTo(sampledAvg));
    }

    private static final class DoubleScore implements ScoreSupplier {

        private final double score;

        private DoubleScore(double score) {
            this.score = score;
        }

        @Override
        public float score() {
            return (float) score;
        }
    }
}
