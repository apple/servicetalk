package io.servicetalk.http.netty;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.SourceAdapters;
import io.servicetalk.concurrent.internal.CancelImmediatelySubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

final class LeakDetection {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeakDetection.class);
    private LeakDetection() {
        // no instances.
    }

    static <T> Publisher<T> instrument(Publisher<T> publisher) {
        FinishedToken token = new FinishedToken(publisher);
        return publisher.liftSync(subscriber -> new InstrumentedSubscriber<>(subscriber, token));
    }
    private static final class InstrumentedSubscriber<T> implements Subscriber<T> {

        private final Subscriber<T> delegate;
        private final FinishedToken token;

        public InstrumentedSubscriber(Subscriber<T> delegate, FinishedToken token) {
            this.delegate = delegate;
            this.token = token;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            token.subscribed(subscription);
            delegate.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    subscription.request(n);
                }

                @Override
                public void cancel() {
                    token.doComplete();
                    subscription.cancel();
                }
            });
        }

        @Override
        public void onNext(@Nullable T t) {
            delegate.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            token.doComplete();
            delegate.onError(t);
        }

        @Override
        public void onComplete() {
            token.doComplete();
            delegate.onComplete();
        }


    }

    private static final class FinishedToken {

        private static final AtomicReferenceFieldUpdater<FinishedToken, Object> UPDATER =
                AtomicReferenceFieldUpdater.newUpdater(FinishedToken.class, Object.class,"state");
        private static final String COMPLETE = "complete";

        volatile Object state;

        public FinishedToken(Publisher<?> parent) {
            this.state = parent;
        }

        void doComplete() {
            UPDATER.set(this, COMPLETE);
        }

        private boolean checkComplete() {
            Object previous = UPDATER.getAndSet(this, COMPLETE);
            if (previous != COMPLETE) {
                // This means something leaked.
                if (previous instanceof Publisher) {
                    // never subscribed to.
                    SourceAdapters.toSource((Publisher<?>) previous).subscribe(CancelImmediatelySubscriber.INSTANCE);
                } else {
                    assert previous instanceof Cancellable;
                    Cancellable cancellable = (Cancellable) previous;
                    cancellable.cancel();
                }
                return true;
            } else {
                return false;
            }
        }

        void subscribed(Subscription subscription) {
            while (true) {
                Object old = UPDATER.get(this);
                if (old == COMPLETE || old instanceof Subscription) {
                    // TODO: What to do here?
                    LOGGER.debug("Publisher subscribed to multiple times.");
                    return;
                } else if (UPDATER.compareAndSet(this, old, subscription)) {
                    return;
                }
            }
        }

        // TODO: move this to a phantom reference approach.
        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            if (checkComplete()) {
                LOGGER.warn("LEAK detected.");
            }
        }
    }
}
