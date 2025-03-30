# Axor 集群发布订阅示例

[完整可执行代码在这里](../../axor-examples/src/main/java/io/masterkun/axor/example/_05_ClusterPubsubExample.java)

本示例展示了如何使用 Axor 框架在集群中实现发布订阅模式。通过定义消息类型、创建发布者和订阅者
Actor，以及在这些 Actor 之间进行通信。

## 1. 导入必要的包

```java
import com.typesafe.config.Config;
import io.axor.runtime.MsgType;

import static com.typesafe.config.ConfigFactory.load;
import static com.typesafe.config.ConfigFactory.parseString;
```

## 2. 定义消息类型

为了在 Actors 之间传递消息，首先定义了两种消息类型 `PublishMessage` 和 `SendToOneMessage`。

```java
public sealed interface TopicMessage {
}

public record PublishMessage(int id, String content) implements TopicMessage {
}

public record SendToOneMessage(int id, String content) implements TopicMessage {
}
```

## 3. 创建 Subscriber

`Subscriber` 是接收 `TopicMessage` 类型消息的 Actor，并简单地记录接收到的消息。

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

## 4. 创建 Publisher

`Publisher` 是一个发布者 Actor，它定期发送 `PublishMessage` 和 `SendToOneMessage` 类型的消息。

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
        // 启动时设置定时任务
        future = context().dispatcher().scheduleAtFixedRate(() -> {
            pubsub.publishToAll(new PublishMessage(
                    id++,
                    UUID.randomUUID().toString()
            ), self());
        }, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    public void preStop() {
        // 停止时取消定时任务
        if (future != null) {
            future.cancel(false);
        }
    }

    @Override
    public void onReceive(String s) {
        // 处理接收到的字符串消息
    }

    @Override
    public MsgType<String> msgType() {
        return MsgType.of(String.class);
    }
}
```

## 5. 启动系统

创建一个 Actor 系统并启动 `Subscriber` 和 `Publisher` 实例。

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
    // 启动Publisher，publisher会定时发送PublishToAll消息
    var publisher = system.start(Publisher::new, "publisher");

    // 启动定时任务定时发送SendToOne消息
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

## 6. 节点实现

为集群中的每个节点提供主方法实现，以便可以独立启动各个节点。。

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

```plain text
ts="..." level=INFO  th="..." logger=io.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: SendToOneMessage[id=4, content=153e3d82-ade3-45e1-bd49-92467f9caec8] from ActorRef[example@localhost:1101/publisher]"
ts="..." level=INFO  th="..." logger=io.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: PublishMessage[id=0, content=267e951a-cb9c-4e36-8950-7b8610b958ac] from ActorRef[example@localhost:1101/publisher]"
ts="..." level=INFO  th="..." logger=io.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: SendToOneMessage[id=2, content=3420da9e-d4e5-4a70-bdac-62769d54d484] from ActorRef[example@localhost:1102/publisher]"
ts="..." level=INFO  th="..." logger=io.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: SendToOneMessage[id=0, content=4435d6f3-222a-4f57-9e0b-79a06fc7e103] from ActorRef[example@:/publisher]"
ts="..." level=INFO  th="..." logger=io.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: PublishMessage[id=0, content=dc5b97e0-85b3-44c5-a45e-100a7980715f] from ActorRef[example@localhost:1102/publisher]"
ts="..." level=INFO  th="..." logger=io.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: SendToOneMessage[id=7, content=2e9d9b82-5def-4665-be83-14617cb7c4f1] from ActorRef[example@localhost:1101/publisher]"
ts="..." level=INFO  th="..." logger=io.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: SendToOneMessage[id=5, content=9f1a5073-8324-4edf-a47b-61726b415e30] from ActorRef[example@localhost:1102/publisher]"
ts="..." level=INFO  th="..." logger=io.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: SendToOneMessage[id=3, content=6a7aa2f6-af6f-42d2-b525-9c12daa214e7] from ActorRef[example@:/publisher]"
ts="..." level=INFO  th="..." logger=io.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: PublishMessage[id=0, content=d5454d56-161d-4ad7-832a-39a7585d5761] from ActorRef[example@:/publisher]"
ts="..." level=INFO  th="..." logger=io.axor.example._05_ClusterPubsubExample$Subscriber actor=example@:/subscriber msg="Receive: PublishMessage[id=1, content=605990dc-ac40-44d5-89e8-0330af7bcecd] from ActorRef[example@localhost:1101/publisher]"
...
```
