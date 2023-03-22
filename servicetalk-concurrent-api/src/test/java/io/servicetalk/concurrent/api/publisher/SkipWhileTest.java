package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SkipWhileTest {

    private final TestPublisher<String> publisher = new TestPublisher<>();
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();


    @Test
    void skipWhile() {
        Publisher<String> p = publisher.skipWhile(s -> !s.equals("Hello2"));
        toSource(p).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(4);
        publisher.onNext("Hello1", "Hello2", "Hello3");
        assertThat(subscriber.takeOnNext(2), contains("Hello2", "Hello3"));
        assertFalse(subscription.isCancelled());
    }

    @Test
    void skipWhileComplete() {
        Publisher<String> p = publisher.skipWhile(s -> !s.equals("Hello2"));
        toSource(p).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(4);
        publisher.onNext("Hello1", "Hello2");
        publisher.onComplete();
        assertEquals("Hello2", subscriber.takeOnNext());
    }

    @Test
    void skipWhileCancelled() {
        Publisher<String> p = publisher.skipWhile(s -> !s.equals("Hello2"));
        toSource(p).subscribe(subscriber);
        publisher.onSubscribe(subscription);
        subscriber.awaitSubscription().request(3);
        publisher.onNext("Hello1", "Hello2");
        assertEquals("Hello2", subscriber.takeOnNext());
        subscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
    }
}
