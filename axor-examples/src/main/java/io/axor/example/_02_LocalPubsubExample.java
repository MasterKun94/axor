package io.axor.example;

import io.axor.api.Actor;
import io.axor.api.ActorContext;
import io.axor.api.ActorSystem;
import io.axor.api.Pubsub;
import io.axor.runtime.MsgType;
import io.axor.runtime.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class demonstrates a local publish-subscribe (pubsub) pattern using an actor system. It
 * creates multiple subscribers that listen to messages from a pubsub topic and logs the received
 * messages. The example publishes messages to all subscribers and also sends individual messages to
 * one of the subscribers. After sending the messages, it shuts down the actor system gracefully.
 */
public class _02_LocalPubsubExample {
    public static void main(String[] args) throws Exception {
        ActorSystem system = ActorSystem.create("example");
        String pubsubName = "example-pubsub";
        system.<String>start(c -> new Subscriber(c, pubsubName), "Subscriber1");
        system.<String>start(c -> new Subscriber(c, pubsubName), "Subscriber2");
        system.<String>start(c -> new Subscriber(c, pubsubName), "Subscriber3");
        Pubsub<String> pubsub = Pubsub.get(pubsubName, MsgType.of(String.class), system);
        Thread.sleep(1000);
        for (int i = 0; i < 10; i++) {
            pubsub.publishToAll("Message Publish To All : " + i);
        }
        for (int i = 0; i < 10; i++) {
            pubsub.sendToOne("Message Send To One : " + i);
        }
        Thread.sleep(1000);
        system.shutdownAsync().join();
    }

    public static class Subscriber extends Actor<String> {
        private static final Logger LOG = LoggerFactory.getLogger(Subscriber.class);
        private final String pubsubName;

        protected Subscriber(ActorContext<String> context, String pubsubName) {
            super(context);
            this.pubsubName = pubsubName;
        }

        @Override
        public void onStart() {
            Pubsub.get(pubsubName, msgType(), context().system()).subscribe(self());
        }

        @Override
        public void preStop() {
            Pubsub.get(pubsubName, msgType(), context().system()).unsubscribe(self());
        }

        @Override
        public void onReceive(String helloBack) {
            LOG.info("Receive: {} from {}", helloBack, sender());
        }

        @Override
        public void onSignal(Signal signal) {
            LOG.info("Signal: {}", signal);
        }

        @Override
        public MsgType<String> msgType() {
            return MsgType.of(String.class);
        }
    }
}
