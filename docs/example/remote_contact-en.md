# Axor Remote Contact Example

[Full executable code is here](../../axor-examples/src/main/java/io/masterkun/axor/example/_03_RemoteContactExample.java)

## Introduction

This example demonstrates how to use the Axor framework to implement remote communication between
two Actors. Through this example, you can learn how to create and configure Actor systems, define
message types, and exchange messages between Actors in different systems. We will create a simple
server-client model where the client periodically sends `Ping` messages to the server, and the
server responds with `Pong` messages.

## 1. Importing Necessary Packages

``` java
import com.typesafe.config.Config;
import io.axor.api.*;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.runtime.MsgType;
import io.axor.runtime.serde.kryo.KryoSerdeFactory;
```

## 2. Defining Message Types

To pass messages between Actors, we define two message types: `Ping` and `Pong`.

``` java
public record Ping(int id) {
}

public record Pong(int id) {
}
```

## 3. Creating the ServerActor

The `ServerActor` is an Actor that receives `Ping` type messages and replies with `Pong` type
messages to the sender.

``` java
public static class ServerActor extends Actor<Ping> {
    private static final Logger LOG = LoggerFactory.getLogger(ServerActor.class);

    protected ServerActor(ActorContext<Ping> context) {
        super(context);
    }

    @Override
    public void onReceive(Ping ping) {
        LOG.info("Receive: {} from {}", ping, sender());
        sender(Pong.class).tell(new Pong(ping.id), self());
    }

    @Override
    public MsgType<Ping> msgType() {
        return MsgType.of(Ping.class);
    }
}
```

## 4. Creating the ClientActor

The `ClientActor` is an Actor that receives `Pong` type messages and simply logs the received
messages.

``` java
public static class ClientActor extends Actor<Pong> {
    private static final Logger LOG = LoggerFactory.getLogger(ClientActor.class);
    private final ActorAddress pingAddress;
    private int id = 0;

    protected ClientActor(ActorContext<Pong> context, ActorAddress pingAddress) {
        super(context);
        this.pingAddress = pingAddress;
    }

    @Override
    public void onStart() {
        // Schedule periodic sending of Ping messages
        ActorRef<Ping> actor;
        try {
            actor = context().system().get(pingAddress, Ping.class);
        } catch (ActorNotFoundException | IllegalMsgTypeException e) {
            throw new RuntimeException(e);
        }
        context().dispatcher().scheduleAtFixedRate(() -> {
            actor.tell(new Ping(id++), self());
        }, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public void onReceive(Pong pong) {
        LOG.info("Receive: {} from {}", pong, sender());
    }
    
    @Override
    public MsgType<Pong> msgType() {
        return MsgType.of(Pong.class);
    }
}
```

## 5. Remote Communication

We create two Actor systems, one as the server (Node1) and the other as the client (Node2). The
client periodically sends `Ping` messages to the server, and the server responds with `Pong`
messages. Communication uses Kryo for message serialization.

``` java
private static ActorSystem startSystem(int port) {
    Config config = load(parseString(("""
            axor.network.bind {
                port = %d
                host = "localhost"
            }
            """.formatted(port)))).resolve();
    return ActorSystem.create("example", config);
}

public static class Node1 {
    public static void main(String[] args) {
        ActorSystem system = startSystem(1101);
        system.start(ServerActor::new, "Server");
    }
}

public static class Node2 {
    public static void main(String[] args) {
        ActorSystem system = startSystem(1102);
        ActorAddress serverAddress = ActorAddress.create("example@localhost:1101/serverActor");
        system.start(c -> new ClientActor(c, serverAddress), "Client");
    }
}
```

### Log Output After Execution

When running the above code, the console will display log output similar to the following:

#### node1

```plain text
ts="..." level=INFO  th="..." logger=io.axor.example._03_RemoteContactExample$ServerActor actor=example@:/serverActor msg="Receive: Ping[id=0] from ActorRef[example@localhost:1102/clientActor]"
ts="..." level=INFO  th="..." logger=io.axor.example._03_RemoteContactExample$ServerActor actor=example@:/serverActor msg="Receive: Ping[id=1] from ActorRef[example@localhost:1102/clientActor]"
ts="..." level=INFO  th="..." logger=io.axor.example._03_RemoteContactExample$ServerActor actor=example@:/serverActor msg="Receive: Ping[id=2] from ActorRef[example@localhost:1102/clientActor]"
...
```

#### node2

```plain text
ts="..." level=INFO  th="..." logger=io.axor.example._03_RemoteContactExample$ClientActor actor=example@:/clientActor msg="Receive: Pong[id=0] from ActorRef[example@localhost:1101/serverActor]"
ts="..." level=INFO  th="..." logger=io.axor.example._03_RemoteContactExample$ClientActor actor=example@:/clientActor msg="Receive: Pong[id=1] from ActorRef[example@localhost:1101/serverActor]"
ts="..." level=INFO  th="..." logger=io.axor.example._03_RemoteContactExample$ClientActor actor=example@:/clientActor msg="Receive: Pong[id=2] from ActorRef[example@localhost:1101/serverActor]"
...
```
