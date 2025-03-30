# Axor Cluster PubSub Example

[Full executable code is here](../../axor-examples/src/main/java/io/masterkun/axor/example/_05_ClusterPubsubExample.java)

This example demonstrates how to use the Axor framework to implement a publish-subscribe pattern in
a cluster. It showcases defining message types, creating publisher and subscriber actors, and
facilitating communication between these actors.

## 1. Importing Necessary Packages

```java
import com.typesafe.config.Config;
import io.masterkun.axor.api.*;
import io.masterkun.axor.cluster.*;
import io.masterkun.axor.runtime.MsgType;

import static com.typesafe.config.ConfigFactory.load;
import static com.typesafe.config.ConfigFactory.parseString;
```

## 2. Defining Message Types

To pass messages between actors, two message types, `PublishMessage` and `SendToOneMessage`, are
defined first.

```java
public sealed interface TopicMessage {
}

public record PublishMessage(int id, String content) implements TopicMessage {
}

public record SendToOneMessage(int id, String content) implements TopicMessage {
}
```

## 3. Creating a Subscriber

The `Subscriber` is an actor that receives `TopicMessage` type messages and simply logs the received
messages.

```java
public static class Subscriber extends Actor<TopicMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(Subscriber.class);

    protected Subscriber(ActorContext<TopicMessage> context) {
        super(context);
    }

    @Override
    public void onReceive(TopicMessage msg) {
        LOG.info("Receive: {} from {}", msg, sender());
    }

    @Override
    public MsgType<TopicMessage> msgType() {
        return MsgType.of(TopicMessage.class);
    }
}
```

## 4. Creating a Publisher

The `Publisher` is a publishing actor that periodically sends messages of `PublishMessage` and
`SendToOneMessage` types.

```java
public static class Publisher extends Actor<String> {
    private ScheduledFuture<?> future;
    private int id;

    protected Publisher(ActorContext<String> context) {
        super(context);
    }

    @Override
    public void onStart() {
        Cluster cluster = Cluster.get(context().system());
        Pubsub<TopicMessage> pubsub = cluster.pubsub("example-topic",
                MsgType.of(TopicMessage.class));
        // Setting up a scheduled task at startup
        future = context().dispatcher().scheduleAtFixedRate(() -> {
            pubsub.publishToAll(new PublishMessage(
                    id++,
                    UUID.randomUUID().toString()
            ), self());
        }, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    public void preStop() {
        // Cancelling the scheduled task upon stopping
        if (future != null) {
            future.cancel(false);
        }
    }

    @Override
    public void onReceive(String s) {
        // Handling received string messages
    }

    @Override
    public MsgType<String> msgType() {
        return MsgType.of(String.class);
    }
}
```

## 5. Starting the System

Create an actor system and start instances of `Subscriber` and `Publisher`.

```java
private static void startNode(int port) throws Exception {
    Config config = load(parseString(("""
            axor.network.bind {
                port = %d
                host = "localhost"
            }
            axor.cluster.default-cluster {
                join.seeds = ["localhost:1101"]
            }
            """.formatted(port)))).resolve();
    ActorSystem system = ActorSystem.create("example", config);
    system.getSerdeRegistry().getFactory(KryoSerdeFactory.class)
            .addInitializer(kryo -> {
                kryo.register(PublishMessage.class, 501);
                kryo.register(SendToOneMessage.class, 502);
            });
    Pubsub<TopicMessage> pubsub = Cluster.get(system)
            .pubsub("example-topic", MsgType.of(TopicMessage.class));
    pubsub.subscribe(system.start(Subscriber::new, "subscriber"));
    Thread.sleep(1000);
    // Starting the Publisher, which will send PublishToAll messages at regular intervals
    var publisher = system.start(Publisher::new, "publisher");

    // Scheduling a task to send SendToOne messages at regular intervals
    system.getDispatcherGroup().nextDispatcher().scheduleAtFixedRate(new Runnable() {
        private int id;

        @Override
        public void run() {
            pubsub.sendToOne(new SendToOneMessage(
                    id++,
                    UUID.randomUUID().toString()
            ), publisher);
        }
    }, 1, 1, TimeUnit.SECONDS);
}
```

## 6. Node Implementation

Provide a main method implementation for each node in the cluster so that nodes can be started
independently.

```java
public static class Node1 {
    public static void main(String[] args) throws Exception {
        startNode(1101);
    }
}

public static class Node2 {
    public static void main(String[] args) throws Exception {
        startNode(1102);

    }
}

public static class Node3 {
    public static void main(String[] args) throws Exception {
        startNode(1103);
    }
}
```

### 执行后日志输出

When running the above code, the console will display log output similar to the following:

```plain text
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: SendToOneMessage[id=4, content=153e3d82-ade3-45e1-bd49-92467f9caec8] from ActorRef[example@localhost:1101/publisher]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: PublishMessage[id=0, content=267e951a-cb9c-4e36-8950-7b8610b958ac] from ActorRef[example@localhost:1101/publisher]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: SendToOneMessage[id=2, content=3420da9e-d4e5-4a70-bdac-62769d54d484] from ActorRef[example@localhost:1102/publisher]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: SendToOneMessage[id=0, content=4435d6f3-222a-4f57-9e0b-79a06fc7e103] from ActorRef[example@:/publisher]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: PublishMessage[id=0, content=dc5b97e0-85b3-44c5-a45e-100a7980715f] from ActorRef[example@localhost:1102/publisher]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: SendToOneMessage[id=7, content=2e9d9b82-5def-4665-be83-14617cb7c4f1] from ActorRef[example@localhost:1101/publisher]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: SendToOneMessage[id=5, content=9f1a5073-8324-4edf-a47b-61726b415e30] from ActorRef[example@localhost:1102/publisher]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: SendToOneMessage[id=3, content=6a7aa2f6-af6f-42d2-b525-9c12daa214e7] from ActorRef[example@:/publisher]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: PublishMessage[id=0, content=d5454d56-161d-4ad7-832a-39a7585d5761] from ActorRef[example@:/publisher]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: PublishMessage[id=1, content=605990dc-ac40-44d5-89e8-0330af7bcecd] from ActorRef[example@localhost:1101/publisher]"
...
```
