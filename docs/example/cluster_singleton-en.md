# Axor Cluster Singleton Example

[Full executable code is here](../../axor-examples/src/main/java/io/masterkun/axor/example/_06_ClusterSingletonExample.java)

## 1. Importing Necessary Packages

```java
import com.typesafe.config.Config;
import io.masterkun.axor.api.*;
import io.masterkun.axor.cluster.Cluster;
import io.masterkun.axor.cluster.singleton.SingletonSystem;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.serde.kryo.KryoSerdeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.typesafe.config.ConfigFactory.load;
import static com.typesafe.config.ConfigFactory.parseString;
```

## 2. Defining Message Types

To pass messages between actors, two message types, `Hello` and `HelloReply`, are defined first.

```java
public record Hello(String msg) {
}

public record HelloReply(String msg) {
}
```

## 3. Creating HelloWorldActor (Singleton Actor)

The `HelloWorldActor` is a singleton actor responsible for receiving `Hello` type messages and
returning `HelloReply` type messages.

```java
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
```

## 4. Creating HelloBot

The `HelloBot` is an actor that periodically sends `Hello` messages to the `HelloWorldActor` and
handles `HelloReply` messages received from the `HelloWorldActor`.

```java
public static class HelloBot extends Actor<HelloReply> {
    private static final Logger LOG = LoggerFactory.getLogger(HelloBot.class);

    protected HelloBot(ActorContext<HelloReply> context) {
        super(context);
    }

    @Override
    public void onReceive(HelloReply helloReply) {
        LOG.info("Receive: {} from {}", helloReply, sender());
    }

    @Override
    public MsgType<HelloReply> msgType() {
        return MsgType.of(HelloReply.class);
    }
}
```

## 5. Starting the System

This method is used to start the actor system on each node and attempt to start or obtain a proxy
for the singleton actor. Additionally, it will start a `HelloBot` instance in the system, which will
periodically send messages to the singleton via its proxy.

```java
private static void startNode(int port) {
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
                kryo.register(Hello.class, 601);
                kryo.register(HelloReply.class, 602);
            });
    Cluster cluster = Cluster.get(system);
    SingletonSystem singletonSystem = SingletonSystem.get(cluster);
    ActorRef<Hello> singletonProxy = singletonSystem.getOrStart(HelloWorldActor::new,
            MsgType.of(Hello.class), "HelloSingleton");
    ActorRef<HelloReply> bot = system.start(HelloBot::new, "HelloBot");
    system.getDispatcherGroup().nextDispatcher().scheduleAtFixedRate(() -> {
        singletonProxy.tell(new Hello("Hello"), bot);
    }, 3, 3, TimeUnit.SECONDS);
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

### Log Output After Execution

When running the above code, the console will display log output similar to the following:

#### Non-Singleton Node

```plain text
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@localhost:1101/cluster/singleton/HelloSingleton/instance]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@localhost:1101/cluster/singleton/HelloSingleton/instance]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@localhost:1101/cluster/singleton/HelloSingleton/instance]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@localhost:1101/cluster/singleton/HelloSingleton/instance]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@localhost:1101/cluster/singleton/HelloSingleton/instance]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@localhost:1101/cluster/singleton/HelloSingleton/instance]"
...
```

#### Singleton Node

```plain text
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._06_ClusterSingletonExample$HelloWorldActor actor=example@:/cluster/singleton/HelloSingleton/instance msg="Receive: Hello[msg=Hello] from ActorRef[example@localhost:1102/HelloBot]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._06_ClusterSingletonExample$HelloWorldActor actor=example@:/cluster/singleton/HelloSingleton/instance msg="Receive: Hello[msg=Hello] from ActorRef[example@localhost:1103/HelloBot]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._06_ClusterSingletonExample$HelloWorldActor actor=example@:/cluster/singleton/HelloSingleton/instance msg="Receive: Hello[msg=Hello] from ActorRef[example@:/HelloBot]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@:/cluster/singleton/HelloSingleton/instance]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._06_ClusterSingletonExample$HelloWorldActor actor=example@:/cluster/singleton/HelloSingleton/instance msg="Receive: Hello[msg=Hello] from ActorRef[example@localhost:1102/HelloBot]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._06_ClusterSingletonExample$HelloWorldActor actor=example@:/cluster/singleton/HelloSingleton/instance msg="Receive: Hello[msg=Hello] from ActorRef[example@localhost:1103/HelloBot]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._06_ClusterSingletonExample$HelloWorldActor actor=example@:/cluster/singleton/HelloSingleton/instance msg="Receive: Hello[msg=Hello] from ActorRef[example@:/HelloBot]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@:/cluster/singleton/HelloSingleton/instance]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._06_ClusterSingletonExample$HelloWorldActor actor=example@:/cluster/singleton/HelloSingleton/instance msg="Receive: Hello[msg=Hello] from ActorRef[example@localhost:1102/HelloBot]"
```
