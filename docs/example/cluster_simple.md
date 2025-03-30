# Axor 集群示例

[完整可执行代码在这里](../../axor-examples/src/main/java/io/masterkun/axor/example/_04_ClusterSimpleExample.java)

本示例展示了如何使用 Axor 框架设置和管理一个包含多个节点的集群。每个节点定期更新其元数据，并通过监听器监控和记录集群状态及元数据的变化。

## 1. 导入必要的包

```java
import com.typesafe.config.Config;
import io.axor.runtime.MsgType;

import static com.typesafe.config.ConfigFactory.load;
import static com.typesafe.config.ConfigFactory.parseString;
```

## 2. 定义元数据键

为了在集群中存储和检索自定义元数据，首先定义了两个元数据键 `MEMBER_VERSION` 和 `CUSTOM_META`。

```java
public static final MetaKeys.IntMetaKey MEMBER_VERSION = MetaKey.builder(101)
        .name("member_version")
        .description("Member Version")
        .build(0);

public static final MetaKey<CustomMessage> CUSTOM_META = MetaKey.builder(102)
        .name("some_custom_meta")
        .description("Some Custom Meta")
        .build(CustomMessage.getDefaultInstance());
```

## 3. 创建 MemberListener

`MemberListener` 是接收 `ClusterEvent` 类型消息的 Actor，并简单地记录接收到的消息。

```java
public static class MemberListener extends Actor<ClusterEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(MemberListener.class);

    public MemberListener(ActorContext<ClusterEvent> context) {
        super(context);
    }

    @Override
    public void onReceive(ClusterEvent event) {
        if (event instanceof LocalStateChange(var state)) {
            // 本地状态切换
            LOG.info("Local member state changed to: {}", state);
        } else if (event instanceof MemberStateChanged(var member, var from, var to)) {
            // 成员状态切换
            LOG.info("{} state changed from {} to {}", member, from, to);
        } else if (event instanceof MemberMetaInfoChanged(var member, var prevMeta)) {
            // 成员元数据更新
            int nowVersion = MEMBER_VERSION.getValue(member.metaInfo());
            int prevVersion = MEMBER_VERSION.getValue(prevMeta);
            CustomMessage nowMsg = CUSTOM_META.get(member.metaInfo());
            CustomMessage prevMsg = CUSTOM_META.get(prevMeta);
            if (nowVersion != prevVersion) {
                LOG.info("{} meta info changed, version updated to {}", member, nowVersion);
            } else if (!nowMsg.equals(prevMsg)) {
                LOG.info("{} meta info changed, custom message updated to [{}]", member,
                        MessageUtils.loggable(nowMsg));
            } else {
                LOG.info("{} meta info changed", member);
            }
        }
    }

    @Override
    public MsgType<ClusterEvent> msgType() {
        return MsgType.of(ClusterEvent.class);
    }
}
```

## 5. 节点启动逻辑

创建一个 Actor 系统并启动 `MemberListener` 实例来监听集群事件，并且周期性地更新其元数据。

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
    Cluster cluster = Cluster.get(system);
    // 监听集群事件
    cluster.addListener(system.start(MemberListener::new, "memberListener"));
    // 定时更新元数据 MEMBER_VERSION
    system.getDispatcherGroup().nextDispatcher().scheduleAtFixedRate(() -> {
        cluster.updateMetaInfo(MEMBER_VERSION.update(i -> i + 1));
    }, 3, 3, TimeUnit.SECONDS);
    // 定时更新元数据 CUSTOM_META
    system.getDispatcherGroup().nextDispatcher().scheduleAtFixedRate(() -> {
        cluster.updateMetaInfo(CUSTOM_META.upsert(CustomMessage.newBuilder()
                .setId(ThreadLocalRandom.current().nextInt())
                .setContent(UUID.randomUUID().toString())
                .build()));
    }, 5, 5, TimeUnit.SECONDS);
}
```

## 4. 节点实现

为集群中的每个节点提供主方法实现，以便可以独立启动各个节点。

```java
public static class Node1 {
    public static void main(String[] args) {
        startNode(1101);
    }
}

public static class Node2 {
    public static void main(String[] args) {
        startNode(1102);
    }
}

public static class Node3 {
    public static void main(String[] args) {
        startNode(1103);
    }
}
```

### 执行后日志输出

当运行上述代码时，控制台将显示类似以下的日志输出：

```plain text
ts="..." level=INFO  th="..." logger=io.axor.example._04_ClusterSimpleExample$MemberListener actor=example@:/memberListener msg="ClusterMember[uid=694299120956080129, address=example@localhost:1101] meta info changed, version updated to 3"
ts="..." level=INFO  th="..." logger=io.axor.example._04_ClusterSimpleExample$MemberListener actor=example@:/memberListener msg="ClusterMember[uid=694299135824871425, address=example@localhost:1102] meta info changed, version updated to 2"
ts="..." level=INFO  th="..." logger=io.axor.example._04_ClusterSimpleExample$MemberListener actor=example@:/memberListener msg="ClusterMember[uid=694299120956080129, address=example@localhost:1101] meta info changed, custom message updated to [id: 156469909 content: "8cafeda0-ed6e-41a0-8d9a-d9c2eff0ba2a"]"
ts="..." level=INFO  th="..." logger=io.axor.example._04_ClusterSimpleExample$MemberListener actor=example@:/memberListener msg="ClusterMember[uid=694299140287594497, address=example@localhost:1103] meta info changed, version updated to 2"
ts="..." level=INFO  th="..." logger=io.axor.example._04_ClusterSimpleExample$MemberListener actor=example@:/memberListener msg="ClusterMember[uid=694299120956080129, address=example@localhost:1101] meta info changed, version updated to 4"...
...
```
