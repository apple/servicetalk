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

import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.closeTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

public class StatUtilsTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatUtilsTest.class);

    private static final long T_0 = 0L,
            T_1x_HALF_LIFE = 2000L,
            T_2x_HALF_LIFE = 4000L,
            T_3x_HALF_LIFE = 6000L,
            T_4x_HALF_LIFE = 8000L;

    @Test
    public void testEwmaNoDecay() {
        LongSupplier timeSource = Mockito.mock(LongSupplier.class);
        when(timeSource.getAsLong()).thenReturn(T_0, T_0, T_0, T_0);

        StatUtils.UnevenExpWeightedMovingAvg ewma = newEwma(timeSource, T_1x_HALF_LIFE, 100); // T0000 = 100

        assertThat((double) ewma.value(), closeTo(100, 0.01)); // T0
        assertThat((double) ewma.value(), closeTo(100, 0.01)); // T0
        assertThat((double) ewma.value(), closeTo(100, 0.01)); // T0
    }

    @Test
    public void testEwmaDecayByObservedValue() {
        LongSupplier timeSource = Mockito.mock(LongSupplier.class);
        when(timeSource.getAsLong()).thenReturn(T_0, T_1x_HALF_LIFE, T_2x_HALF_LIFE, T_3x_HALF_LIFE, T_4x_HALF_LIFE);

        StatUtils.UnevenExpWeightedMovingAvg ewma = newEwma(timeSource, T_1x_HALF_LIFE, 100);

        ewma.observe(0); // T_1x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(50, 0.01));
        ewma.observe(0); // T_2x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(25, 0.01));
        ewma.observe(0); // T_3x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(12.5, 0.01));
        ewma.observe(0); // T_4x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(6.25, 0.01));
    }

    @Test
    public void testEwmaDecay() {
        LongSupplier timeSource = Mockito.mock(LongSupplier.class);
        when(timeSource.getAsLong()).thenReturn(T_0, T_1x_HALF_LIFE, T_2x_HALF_LIFE, T_3x_HALF_LIFE, T_4x_HALF_LIFE);

        StatUtils.UnevenExpWeightedMovingAvg ewma = newEwma(timeSource, T_1x_HALF_LIFE, 100);

        // T_1x_HALF_LIFE
        assertThat((double) ewma.valueDecayed(), closeTo(50, 0.01));
        // T_2x_HALF_LIFE
        assertThat((double) ewma.valueDecayed(), closeTo(25, 0.01));
        // T_3x_HALF_LIFE
        assertThat((double) ewma.valueDecayed(), closeTo(12.5, 0.01));
        // T_4x_HALF_LIFE
        assertThat((double) ewma.valueDecayed(), closeTo(6.25, 0.01));
    }

    @Test
    public void testEwmaStableWithContinuousValue() {
        LongSupplier timeSource = Mockito.mock(LongSupplier.class);
        when(timeSource.getAsLong()).thenReturn(T_0, T_1x_HALF_LIFE, T_2x_HALF_LIFE, T_3x_HALF_LIFE, T_4x_HALF_LIFE);

        StatUtils.UnevenExpWeightedMovingAvg ewma = newEwma(timeSource, T_1x_HALF_LIFE, 100);

        ewma.observe(100); // T_1x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(100, 0.01));
        ewma.observe(100); // T_2x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(100, 0.01));
        ewma.observe(100); // T_3x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(100, 0.01));
        ewma.observe(100); // T_4x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(100, 0.01));
    }

    @Test
    public void testEwmaDampensFluctuatingValue() {
        LongSupplier timeSource = Mockito.mock(LongSupplier.class);
        when(timeSource.getAsLong()).thenReturn(T_0, T_1x_HALF_LIFE, T_2x_HALF_LIFE, T_3x_HALF_LIFE, T_4x_HALF_LIFE);

        StatUtils.UnevenExpWeightedMovingAvg ewma = newEwma(timeSource, T_1x_HALF_LIFE, 100);

        ewma.observe(0); // T_1x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(50, 0.01));
        ewma.observe(100); // T_2x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(75, 0.01));
        ewma.observe(0); // T_3x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(37.5, 0.01));
        ewma.observe(100); // T_4x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(68.75, 0.01));
    }

    @Test
    public void testEwmaDampensUpwardTrend() {
        LongSupplier timeSource = Mockito.mock(LongSupplier.class);
        when(timeSource.getAsLong()).thenReturn(T_0, T_1x_HALF_LIFE, T_2x_HALF_LIFE, T_3x_HALF_LIFE, T_4x_HALF_LIFE);

        StatUtils.UnevenExpWeightedMovingAvg ewma = newEwma(timeSource, T_1x_HALF_LIFE, 100);

        ewma.observe(200); // T_1x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(150, 0.01));
        ewma.observe(150); // T_2x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(150, 0.01));
        ewma.observe(250); // T_3x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(200, 0.01));
        ewma.observe(300); // T_4x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(250, 0.01));
    }

    @Test
    public void testEwmaDampensUpwardTrendUnevenIsCloseToEvenlySpaced() {
        LongSupplier timeSource = Mockito.mock(LongSupplier.class);
        when(timeSource.getAsLong()).thenReturn(T_0, T_1x_HALF_LIFE,
                T_2x_HALF_LIFE - 500, T_3x_HALF_LIFE + 200, T_4x_HALF_LIFE);

        StatUtils.UnevenExpWeightedMovingAvg ewma = newEwma(timeSource, T_1x_HALF_LIFE, 100);

        ewma.observe(200); // T_1x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(150, 0.01));
        ewma.observe(150); // T_2x_HALF_LIFE -500ms
        assertThat((double) ewma.value(), closeTo(150, 0.01));
        ewma.observe(250); // T_3x_HALF_LIFE +200ms
        assertThat((double) ewma.value(), closeTo(210.77, 0.01));
        ewma.observe(300); // T_4x_HALF_LIFE
        assertThat((double) ewma.value(), closeTo(252.19, 0.01));
    }

    static StatUtils.UnevenExpWeightedMovingAvg newEwma(final LongSupplier timeSource,
                                                        final long halfLifeMs,
                                                        final float initialValue) {

        return new StatUtils.UnevenExpWeightedMovingAvg(Duration.ofMillis(halfLifeMs)) {
            {
                set(initialValue);
            }

            @Override
            long currentTimeNs() {
                return Duration.ofMillis(timeSource.getAsLong()).toNanos();
            }
        };
    }

    @Test
    public void testStreaming0100() {
        testQuantile(0.10f, 0.20f);
    }

    @Test
    public void testStreaming0250() {
        testQuantile(0.25f, 0.20f);
    }

    @Test
    public void testStreaming0500() {
        testQuantile(0.50f, 0.20f);
    }

    @Test
    public void testStreaming0750() {
        testQuantile(0.75f, 0.20f);
    }

    @Test
    public void testStreaming0900() {
        testQuantile(0.90f, 0.20f);
    }

    @Test
    public void testStreaming0990() {
        testQuantile(0.99f, 0.20f);
    }

    @Test
    public void testStreaming0999() {
        testQuantile(0.999f, 0.20f);
    }

    @Test
    public void testStreaming1000() {
        testQuantile(1, 0.20f);
    }

    private void testQuantile(final float q, final float tolerance) {
        final Random rnd = new Random(0xA633FDFE45550B7EL); // repeatable tests
        StatUtils.StreamingQuantile estimator = new StatUtils.StreamingQuantile(q) {
            @Override
            Random randomGenerator() {
                return rnd;
            }
        };

        // The estimation algorithm needs a fair amount of data points to get to reasonable error rates for data
        // resembling a Gamma/Exponential distribution. Implementation depends on uniform distribution.
        for (int i = 0; i < 10; i++) {
            streamingValues().forEachOrdered(estimator::observe);
        }

        float expected = exactLinearQuantile(streamingValues(), q);
        float estimate = estimator.estimation();
        float error = expected * tolerance;
        logErrorRate(q, expected, estimate);
        // allow estimate to be off by some tolerance (<20% is probably recommended)
        assertThat((double) estimate, closeTo(expected, error));
    }

    /**
     * Stream of 1000 values sampled from a Gamma(2.0, 75) distribution, resembling latencies of a networked service
     * where the majority of requests are fast, with a long tail of outliers.
     *
     * <pre>
     *
     * Quantiles:
     * ----------
     * 0.100     39
     * 0.250     71
     * 0.500    118
     * 0.750    199
     * 0.900    283
     * 0.990    466
     * 0.999    684
     * 1.000    751
     *
     * </pre>
     */
    private Stream<Float> streamingValues() {
        // python:
        // l = list(map(int, np.random.gamma(2, 75, 1000)))
        // pd.Series(l).quantile(q=[0.1, .25, .5, .75, .9, .99, .999, 1], interpolation='lower')
        // s.hist()
        return DoubleStream.of(
                45, 10, 78, 37, 132, 91, 174, 37, 200, 327, 246, 423, 26, 105, 109, 324, 104, 90, 167, 305, 89, 342,
                341, 534, 23, 287, 204, 74, 124, 163, 58, 98, 273, 449, 186, 10, 87, 241, 129, 56, 213, 5, 214, 259, 66,
                435, 194, 342, 235, 134, 103, 78, 151, 29, 238, 58, 186, 46, 234, 170, 242, 108, 83, 23, 376, 154, 95,
                204, 232, 199, 266, 234, 308, 118, 150, 387, 69, 24, 84, 116, 213, 43, 17, 74, 101, 252, 197, 13, 30,
                142, 40, 108, 155, 114, 150, 42, 8, 86, 26, 197, 7, 196, 243, 94, 42, 43, 49, 59, 92, 248, 40, 211, 118,
                57, 299, 433, 77, 273, 28, 101, 219, 245, 374, 24, 23, 48, 233, 98, 220, 176, 147, 109, 89, 44, 47, 55,
                112, 98, 751, 21, 68, 77, 207, 115, 368, 9, 87, 185, 247, 146, 273, 245, 139, 110, 142, 85, 29, 192, 41,
                46, 52, 78, 223, 129, 123, 63, 318, 273, 241, 307, 123, 82, 153, 222, 125, 197, 409, 97, 137, 96, 18,
                310, 155, 34, 65, 222, 116, 116, 128, 147, 64, 99, 138, 101, 77, 90, 62, 72, 222, 54, 128, 195, 143,
                142, 301, 316, 22, 347, 157, 117, 93, 65, 144, 140, 86, 280, 349, 34, 15, 234, 126, 222, 278, 187, 10,
                147, 52, 172, 172, 32, 134, 177, 80, 73, 115, 157, 216, 73, 152, 210, 127, 370, 87, 226, 375, 105, 6,
                45, 154, 332, 119, 165, 263, 186, 48, 193, 74, 85, 67, 154, 118, 211, 58, 89, 134, 67, 110, 157, 107,
                280, 197, 81, 81, 181, 339, 55, 195, 186, 195, 124, 31, 116, 66, 298, 89, 96, 128, 94, 41, 252, 94, 44,
                166, 250, 325, 196, 98, 257, 90, 144, 52, 24, 62, 94, 69, 135, 35, 114, 209, 223, 213, 273, 101, 145,
                37, 173, 243, 110, 46, 121, 254, 83, 93, 259, 141, 76, 131, 84, 149, 114, 137, 139, 367, 188, 181, 264,
                58, 32, 187, 29, 91, 76, 106, 100, 64, 231, 151, 261, 83, 147, 53, 191, 222, 171, 148, 51, 76, 184, 155,
                235, 77, 76, 103, 215, 133, 177, 209, 160, 75, 176, 73, 90, 87, 242, 15, 152, 78, 127, 166, 130, 167,
                123, 19, 363, 378, 184, 18, 277, 172, 214, 115, 97, 86, 13, 92, 89, 90, 188, 167, 83, 49, 32, 426, 206,
                88, 170, 168, 145, 209, 202, 34, 183, 105, 66, 132, 37, 208, 42, 185, 275, 279, 268, 259, 221, 98, 58,
                91, 94, 228, 108, 160, 77, 127, 267, 129, 44, 64, 115, 54, 91, 71, 158, 191, 68, 43, 135, 144, 115, 148,
                40, 96, 30, 331, 284, 93, 71, 69, 269, 253, 64, 260, 229, 253, 48, 56, 156, 179, 38, 62, 78, 206, 110,
                326, 71, 226, 220, 136, 330, 85, 323, 17, 92, 82, 136, 237, 47, 313, 83, 53, 32, 347, 231, 202, 25, 281,
                135, 56, 157, 81, 115, 162, 63, 50, 88, 29, 100, 13, 161, 45, 236, 103, 153, 34, 95, 99, 206, 79, 161,
                165, 114, 129, 121, 29, 132, 178, 155, 16, 139, 128, 133, 41, 67, 170, 159, 287, 102, 52, 177, 73, 441,
                117, 70, 132, 154, 61, 131, 226, 37, 357, 99, 61, 96, 124, 21, 46, 27, 24, 448, 52, 317, 89, 69, 201,
                320, 98, 259, 132, 421, 72, 214, 134, 270, 215, 91, 187, 115, 62, 56, 108, 135, 80, 41, 101, 72, 88,
                227, 184, 327, 217, 93, 135, 30, 99, 218, 69, 62, 77, 110, 43, 81, 370, 16, 39, 84, 194, 36, 34, 320,
                132, 85, 35, 147, 24, 51, 45, 80, 126, 244, 154, 75, 104, 299, 317, 110, 98, 45, 203, 175, 113, 88, 64,
                16, 51, 7, 136, 81, 149, 50, 91, 30, 47, 124, 58, 11, 68, 120, 376, 53, 193, 76, 146, 71, 95, 176, 174,
                193, 113, 189, 104, 185, 290, 147, 99, 284, 160, 149, 500, 27, 33, 205, 267, 97, 47, 12, 189, 115, 152,
                10, 85, 192, 321, 90, 92, 269, 149, 178, 284, 216, 47, 86, 53, 100, 116, 215, 96, 203, 197, 46, 57, 47,
                58, 93, 58, 220, 281, 359, 302, 342, 205, 120, 83, 317, 333, 85, 102, 192, 95, 41, 58, 68, 115, 163,
                198, 55, 118, 108, 120, 78, 215, 127, 152, 117, 81, 184, 239, 144, 87, 251, 149, 249, 99, 105, 29, 45,
                211, 178, 291, 72, 341, 602, 192, 161, 194, 80, 160, 241, 69, 29, 190, 126, 67, 466, 30, 684, 6, 143,
                317, 73, 94, 68, 116, 139, 162, 79, 179, 162, 102, 183, 170, 54, 17, 78, 77, 102, 76, 106, 374, 63, 280,
                82, 73, 111, 243, 178, 186, 67, 183, 68, 161, 217, 145, 168, 51, 137, 113, 226, 262, 416, 211, 108, 71,
                121, 281, 25, 87, 279, 104, 440, 57, 125, 183, 42, 311, 68, 185, 174, 188, 166, 16, 93, 257, 52, 66,
                109, 115, 95, 38, 95, 108, 75, 241, 315, 18, 424, 161, 127, 422, 161, 91, 326, 178, 65, 49, 64, 7, 81,
                260, 176, 224, 32, 65, 264, 111, 109, 257, 256, 517, 228, 138, 179, 9, 80, 95, 99, 554, 99, 340, 185,
                243, 67, 305, 298, 94, 94, 200, 45, 16, 35, 283, 78, 41, 125, 251, 63, 251, 301, 445, 223, 216, 53, 60,
                65, 111, 240, 476, 178, 188, 86, 104, 391, 160, 74, 217, 149, 12, 118, 21, 40, 332, 133, 77, 264, 364,
                12, 97, 60, 176, 271, 64, 315, 214, 53, 180, 245, 118, 116, 127, 407, 49, 106, 31, 366, 261, 76, 152,
                82, 504, 49, 45, 217, 33, 37, 22, 45, 112, 79, 141, 478, 128, 139, 309, 66, 180, 150, 96, 158, 34, 27,
                117, 69, 143, 15, 128, 342, 52, 178, 371, 136, 93, 77, 59, 28, 236, 87, 148, 74, 12, 45, 182, 190, 47,
                165, 45, 51, 206, 100, 6, 172, 83, 22
        ).mapToObj(Float::new);
    }

    float exactLinearQuantile(Stream<Float> stream, float quantile) {
        List<Float> collect = stream.sorted().collect(Collectors.toList());
        float pos = (quantile * (float) collect.size()) - 1;
        int leftPos = (int) pos;
        float scale = pos - leftPos;
        if (scale < 0.01) { // probably close enough, don't interpolate
            return collect.get(leftPos);
        }
        float leftValue = collect.get(leftPos);
        float delta = collect.get(leftPos + 1) - leftValue;
        return leftValue + (scale * delta);
    }

    private void logErrorRate(final float q, final float expected, final float estimate) {
        int delta = (int) (expected - estimate);
        int absDelta = Math.abs(delta);
        String sign = delta > 0 ? "-" : "+";
        int absPct = (int) ((absDelta / expected) * 100f);
        LOGGER.debug("{} \t=>\t{}\t<=>\t{}\t({}{})\t[{}%]",
                q, expected, estimate, sign, absDelta, absPct);
    }
}
