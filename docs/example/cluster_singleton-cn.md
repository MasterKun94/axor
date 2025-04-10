# Axor 集群单例示例

[完整可执行代码在这里](../../axor-examples/src/main/java/io/masterkun/axor/example/_06_ClusterSingletonExample.java)

## 1. 导入必要的包

```java
import com.typesafe.config.Config;
import io.axor.cluster.Cluster;
import io.axor.cluster.singleton.SingletonSystem;
import io.axor.runtime.MsgType;
import io.axor.runtime.serde.kryo.KryoSerdeFactory;

import static com.typesafe.config.ConfigFactory.load;
import static com.typesafe.config.ConfigFactory.parseString;
```

## 2. 定义消息类型

为了在 Actors 之间传递消息，首先定义了两种消息类型 `Hello` 和 `HelloReply`。

```java
public record Hello(String msg) {
}

public record HelloReply(String msg) {
}
```

## 3. 创建 HelloWorldActor (Singleton Actor)

`HelloWorldActor` 是作为单例存在的 Actor，负责接收 `Hello` 类型的消息，并返回 `HelloReply` 类型的消息。

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

## 4. 创建 HelloBot

`HelloBot` 是定期向 `HelloWorldActor` 发送 `Hello` 消息的 Actor，并处理从 `HelloWorldActor` 接收到的
`HelloReply` 消息。

```java
public static class HelloBot extends Actor<HelloReply> {
    private static final Logger LOG = LoggerFactory.getLogger(HelloBot.class);

    protected HelloBot(ActorContext<HelloReply> context) {
        super(context);
    }

    @Override
    public void onReceive(HelloReply HelloReply) {
        LOG.info("Receive: {} from {}", HelloReply, sender());
    }

    @Override
    public MsgType<HelloReply> msgType() {
        return MsgType.of(HelloReply.class);
    }
}
```

## 5. 启动系统

此方法用于启动每个节点上的 Actor 系统，并尝试启动或获取 Singleton Actor 的代理。此外，还将在该系统中启动一个
`HelloBot` 实例，定期通过单例的代理向单例发送消息。

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

## 6. 节点实现

为集群中的每个节点提供主方法实现，以便可以独立启动各个节点。

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

当运行上述代码时，控制台将显示类似以下的日志输出：

#### 非单例节点

```plain text
ts="..." level=INFO  th="..." logger=io.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@localhost:1101/cluster/singleton/HelloSingleton/instance]"
ts="..." level=INFO  th="..." logger=io.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@localhost:1101/cluster/singleton/HelloSingleton/instance]"
ts="..." level=INFO  th="..." logger=io.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@localhost:1101/cluster/singleton/HelloSingleton/instance]"
ts="..." level=INFO  th="..." logger=io.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@localhost:1101/cluster/singleton/HelloSingleton/instance]"
ts="..." level=INFO  th="..." logger=io.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@localhost:1101/cluster/singleton/HelloSingleton/instance]"
ts="..." level=INFO  th="..." logger=io.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@localhost:1101/cluster/singleton/HelloSingleton/instance]"
...
```

### 单例节点

```plain text
ts="..." level=INFO  th="..." logger=io.axor.example._06_ClusterSingletonExample$HelloWorldActor actor=example@:/cluster/singleton/HelloSingleton/instance msg="Receive: Hello[msg=Hello] from ActorRef[example@localhost:1102/HelloBot]"
ts="..." level=INFO  th="..." logger=io.axor.example._06_ClusterSingletonExample$HelloWorldActor actor=example@:/cluster/singleton/HelloSingleton/instance msg="Receive: Hello[msg=Hello] from ActorRef[example@localhost:1103/HelloBot]"
ts="..." level=INFO  th="..." logger=io.axor.example._06_ClusterSingletonExample$HelloWorldActor actor=example@:/cluster/singleton/HelloSingleton/instance msg="Receive: Hello[msg=Hello] from ActorRef[example@:/HelloBot]"
ts="..." level=INFO  th="..." logger=io.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@:/cluster/singleton/HelloSingleton/instance]"
ts="..." level=INFO  th="..." logger=io.axor.example._06_ClusterSingletonExample$HelloWorldActor actor=example@:/cluster/singleton/HelloSingleton/instance msg="Receive: Hello[msg=Hello] from ActorRef[example@localhost:1102/HelloBot]"
ts="..." level=INFO  th="..." logger=io.axor.example._06_ClusterSingletonExample$HelloWorldActor actor=example@:/cluster/singleton/HelloSingleton/instance msg="Receive: Hello[msg=Hello] from ActorRef[example@localhost:1103/HelloBot]"
ts="..." level=INFO  th="..." logger=io.axor.example._06_ClusterSingletonExample$HelloWorldActor actor=example@:/cluster/singleton/HelloSingleton/instance msg="Receive: Hello[msg=Hello] from ActorRef[example@:/HelloBot]"
ts="..." level=INFO  th="..." logger=io.axor.example._06_ClusterSingletonExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloBack[msg=Hello] from ActorRef[example@:/cluster/singleton/HelloSingleton/instance]"
ts="..." level=INFO  th="..." logger=io.axor.example._06_ClusterSingletonExample$HelloWorldActor actor=example@:/cluster/singleton/HelloSingleton/instance msg="Receive: Hello[msg=Hello] from ActorRef[example@localhost:1102/HelloBot]"
```
