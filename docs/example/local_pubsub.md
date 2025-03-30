# Axor 本地发布订阅示例

[完整可执行代码在这里](../../axor-examples/src/main/java/io/masterkun/axor/example/_02_LocalPubsubExample.java)

本示例展示了如何使用 Axor 框架实现本地发布订阅模式。通过这个示例，你可以学习到如何设置 Actor
系统、创建订阅者以及发布消息。我们将创建多个订阅者来监听同一个 pubsub
主题，并向该主题发布消息。此外，我们还将单独发送一些消息给其中一个订阅者。

## 1. 导入必要的包

```java
import io.masterkun.axor.api.*;
import io.masterkun.axor.runtime.MsgType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

## 2. 创建 Subscriber

`Subscriber` 是一个 Actor，它订阅了指定的 pubsub 主题，并在接收到消息时记录这些消息。

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

## 3. 发布和订阅

创建了一个 Actor 系统，并启动了多个 `Subscriber` 实例。每个实例都监听同一个 pubsub 主题
`example-pubsub`。然后向该主题发布多条消息，并单独发送一些消息给其中一个订阅者。

```java
public static void main(String[] args) throws Exception {
    ActorSystem system = ActorSystem.create("example");
    String pubsubName = "example-pubsub";
    // 启动多个订阅者
    system.<String>start(c -> new Subscriber(c, pubsubName), "Subscriber1");
    system.<String>start(c -> new Subscriber(c, pubsubName), "Subscriber2");
    system.<String>start(c -> new Subscriber(c, pubsubName), "Subscriber3");
    // 获取pubsub实例
    Pubsub<String> pubsub = Pubsub.get(pubsubName, MsgType.of(String.class), system);
    // 等待订阅者启动
    Thread.sleep(1000);
    // 向所有订阅者发布消息
    for (int i = 0; i < 10; i++) {
        pubsub.publishToAll("Message Publish To All : " + i);
    }
    // 单独发送消息给某个订阅者
    for (int i = 0; i < 10; i++) {
        pubsub.sendToOne("Message Send To One : " + i);
    }
    // 等待一段时间确保所有消息处理完毕
    Thread.sleep(1000);
    // 关闭系统
    system.shutdownAsync().join();
}
```

### 执行后日志输出

当运行上述代码时，控制台将显示类似以下的日志输出：

```plain text
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._02_LocalPubsubExample$Subscriber actor=example@:/Subscriber1 msg="Receive: Message Publish To All : 0 from ActorRef[no_sender@:/no_sender]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._02_LocalPubsubExample$Subscriber actor=example@:/Subscriber2 msg="Receive: Message Publish To All : 0 from ActorRef[no_sender@:/no_sender]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._02_LocalPubsubExample$Subscriber actor=example@:/Subscriber3 msg="Receive: Message Publish To All : 0 from ActorRef[no_sender@:/no_sender]"
...
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._02_LocalPubsubExample$Subscriber actor=example@:/Subscriber1 msg="Receive: Message Send To One : 0 from ActorRef[no_sender@:/no_sender]"
...
```
