# Axor Local PubSub Example

[Full executable code is here](../../axor-examples/src/main/java/io/masterkun/axor/example/_02_LocalPubsubExample.java)

This example demonstrates how to implement a local publish-subscribe pattern using the Axor
framework. Through this example, you can learn how to set up an Actor system, create subscribers,
and publish messages. We will create multiple subscribers to listen to the same pubsub topic and
publish messages to that topic. Additionally, we will send some individual messages to one of the
subscribers.

## 1. Importing Necessary Packages

```java

import io.axor.runtime.MsgType;

```

## 2. Creating the Subscriber

`Subscriber` is an Actor that subscribes to a specified pubsub topic and logs the messages it
receives.

```java
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
    public void onReceive(String message) {
        LOG.info("Receive: {} from {}", message, sender());
    }

    @Override
    public MsgType<String> msgType() {
        return MsgType.of(String.class);
    }
}
```

## 3. Publishing and Subscribing

An Actor system is created, and multiple `Subscriber` instances are started. Each instance listens
to the same pubsub topic, `example-pubsub`. Multiple messages are then published to this topic, and
some individual messages are sent to one of the subscribers.

```java
public static void main(String[] args) throws Exception {
    ActorSystem system = ActorSystem.create("example");
    String pubsubName = "example-pubsub";
    // Start multiple subscribers
    system.<String>start(c -> new Subscriber(c, pubsubName), "Subscriber1");
    system.<String>start(c -> new Subscriber(c, pubsubName), "Subscriber2");
    system.<String>start(c -> new Subscriber(c, pubsubName), "Subscriber3");
    // Get the pubsub instance
    Pubsub<String> pubsub = Pubsub.get(pubsubName, MsgType.of(String.class), system);
    // Wait for subscribers to start
    Thread.sleep(1000);
    // Publish messages to all subscribers
    for (int i = 0; i < 10; i++) {
        pubsub.publishToAll("Message Publish To All : " + i);
    }
    // Send individual messages to one subscriber
    for (int i = 0; i < 10; i++) {
        pubsub.sendToOne("Message Send To One : " + i);
    }
    // Wait for a while to ensure all messages are processed
    Thread.sleep(1000);
    // Shutdown the system
    system.shutdownAsync().join();
}
```

### Log Output After Execution

When running the above code, the console will display log output similar to the following:

```
ts="..." level=INFO  th="..." logger=io.axor.example._02_LocalPubsubExample$Subscriber actor=example@:/Subscriber1 msg="Receive: Message Publish To All : 0 from ActorRef[no_sender@:/no_sender]"
ts="..." level=INFO  th="..." logger=io.axor.example._02_LocalPubsubExample$Subscriber actor=example@:/Subscriber2 msg="Receive: Message Publish To All : 0 from ActorRef[no_sender@:/no_sender]"
ts="..." level=INFO  th="..." logger=io.axor.example._02_LocalPubsubExample$Subscriber actor=example@:/Subscriber3 msg="Receive: Message Publish To All : 0 from ActorRef[no_sender@:/no_sender]"
...
ts="..." level=INFO  th="..." logger=io.axor.example._02_LocalPubsubExample$Subscriber actor=example@:/Subscriber1 msg="Receive: Message Send To One : 0 from ActorRef[no_sender@:/no_sender]"
...
```
