package io.servicetalk.dns.discovery.netty;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;

class MovingVarianceTest {

    @Test
    void twoSamples() {
        MovingVariance m = new MovingVariance(2);
        m.addSample(1);
        m.addSample(1);
        assertThat(m.variance(), equalTo(0.0));
    }

    @RepeatedTest(1000)
    void jquikish() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        final int size = r.nextInt(2, 100);
        MovingVariance m = new MovingVariance(size);
        int[] samples = new int[size];
        for (int i = 0; i < size; i++) {
            int xi = r.nextInt(-100, 100);
            samples[i] = xi;
            m.addSample(xi);
        }

        assertThat(m.variance(), closeTo(variance(samples), 0.0001));
        assertThat(m.mean(), closeTo(mean(samples), 0.0001));
    }

    private double variance(int[] values) {
        final double mean = mean(values);
        double accumulator = 0;
        for (int value : values) {
            double diff = value - mean;
            accumulator += diff * diff;
        }
        return accumulator / (values.length - 1);
    }

    double mean(int[] values) {
        long sum = 0;
        for (double v : values) {
            sum += v;
        }
        return ((double) sum) / values.length;
    }
}
