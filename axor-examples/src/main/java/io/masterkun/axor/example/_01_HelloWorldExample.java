package io.masterkun.axor.example;

import io.masterkun.axor.api.Actor;
import io.masterkun.axor.api.ActorContext;
import io.masterkun.axor.api.ActorPatterns;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.runtime.MsgType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * This class, _01_HelloWorldExample, demonstrates a basic example of using an actor system to
 * create and communicate between actors. It sets up an actor system, starts two actors
 * (HelloWorldActor and HelloBot), and facilitates message passing between them. The HelloWorldActor
 * responds to greetings by sending a reply back, while the HelloBot logs any received messages. The
 * main method also showcases how to use the ask pattern to request and receive a response from an
 * actor.
 */
public class _01_HelloWorldExample {
    private static final Logger LOG = LoggerFactory.getLogger(_01_HelloWorldExample.class);

    public static void main(String[] args) throws Exception {
        ActorSystem system = ActorSystem.create("example");
        ActorRef<Hello> actor = system.start(HelloWorldActor::new, "HelloWorld");

        ActorRef<HelloReply> bot = system.start(HelloBot::new, "HelloBot");
        actor.tell(new Hello("Hello"), bot);

        var reply = ActorPatterns.ask(actor, new Hello("Greeting"),
                        MsgType.of(HelloReply.class),
                        Duration.ofSeconds(1), system)
                .toFuture()
                .get();
        LOG.info("Receive reply: {} from {} by ask pattern", reply, actor);
        Thread.sleep(1000);
        system.shutdownAsync().join();
    }

    public record Hello(String msg) {
    }

    public record HelloReply(String msg) {
    }

    public static class HelloWorldActor extends Actor<Hello> {
        private static final Logger LOG = LoggerFactory.getLogger(HelloWorldActor.class);

        protected HelloWorldActor(ActorContext<Hello> context) {
            super(context);
        }

        @Override
        public void onReceive(Hello sayHello) {
            LOG.info("Receive: {} from {}", sayHello, sender());
            sender(HelloReply.class).tell(new HelloReply(sayHello.msg), self());
        }

        @Override
        public MsgType<Hello> msgType() {
            return MsgType.of(Hello.class);
        }
    }

    public static class HelloBot extends Actor<HelloReply> {
        private static final Logger LOG = LoggerFactory.getLogger(HelloBot.class);

        protected HelloBot(ActorContext<HelloReply> context) {
            super(context);
        }

        @Override
        public void onReceive(HelloReply helloBack) {
            LOG.info("Receive: {} from {}", helloBack, sender());
        }

        @Override
        public MsgType<HelloReply> msgType() {
            return MsgType.of(HelloReply.class);
        }
    }
}
