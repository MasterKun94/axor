# Axor 远程通信示例

[完整可执行代码在这里](../../axor-examples/src/main/java/io/masterkun/axor/example/_03_RemoteContactExample.java)

## 简介

本示例展示了如何使用 Axor 框架实现两个 Actor 之间的远程通信。通过这个示例，你可以了解如何创建和配置
Actor 系统、定义消息类型以及在不同系统中的 Actor 之间进行消息传递。我们将创建一个简单的服务器-客户端模型，其中客户端定期向服务器发送
`Ping` 消息，服务器则回复 `Pong` 消息。

## 1. 导入必要的包

```java
import com.typesafe.config.Config;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.runtime.MsgType;
import io.axor.runtime.serde.kryo.KryoSerdeFactory;
```

## 2. 定义消息类型

为了在 Actors 之间传递消息，定义了两种消息类型 `Ping` 和 `Pong`。

```java
public record Ping(int id) {
}

public record Pong(int id) {
}
```

## 3. 创建 ServerActor

`ServerActor` 是接收 `Ping` 类型消息的 Actor，并回复 `Pong` 类型的消息给发送者。

```java
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

## 4. 创建 ClientActor

`ClientActor` 是接收 `Pong` 类型消息的 Actor，并简单地记录接收到的消息。

```java
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
        // 定时发送 Ping 消息
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

## 5. 远程通信

创建两个 Actor 系统，一个作为服务器（Node1），另一个作为客户端（Node2）。客户端定期向服务器发送 `Ping`
消息，服务器响应 `Pong` 消息。通信使用 Kryo 进行消息序列化。

```java
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

### 执行后日志输出

当运行上述代码时，控制台将显示类似以下的日志输出：

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
