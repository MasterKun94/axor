# Axor Cluster Example

[Full executable code is here](../../axor-examples/src/main/java/io/masterkun/axor/example/_04_ClusterSimpleExample.java)

This example demonstrates how to set up and manage a cluster with multiple nodes using the Axor
framework. Each node periodically updates its metadata and monitors and logs changes in the cluster
state and metadata through listeners.

## 1. Importing Necessary Packages

```java
import com.typesafe.config.Config;
import io.axor.runtime.MsgType;

import static com.typesafe.config.ConfigFactory.load;
import static com.typesafe.config.ConfigFactory.parseString;
```

## 2. Defining Metadata Keys

To store and retrieve custom metadata within the cluster, two metadata keys `MEMBER_VERSION` and
`CUSTOM_META` are first defined.

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

## 3. Creating MemberListener

`MemberListener` is an Actor that receives messages of type `ClusterEvent` and simply logs the
received messages.

```java
public static class MemberListener extends Actor<ClusterEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(MemberListener.class);

    public MemberListener(ActorContext<ClusterEvent> context) {
        super(context);
    }

    @Override
    public void onReceive(ClusterEvent event) {
        if (event instanceof LocalStateChange(var state)) {
            // Local state change
            LOG.info("Local member state changed to: {}", state);
        } else if (event instanceof MemberStateChanged(var member, var from, var to)) {
            // Member state change
            LOG.info("{} state changed from {} to {}", member, from, to);
        } else if (event instanceof MemberMetaInfoChanged(var member, var prevMeta)) {
            // Member metadata update
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

## 5. Node Startup Logic

Create an actor system and start a `MemberListener` instance to listen for cluster events, and
periodically update its metadata.

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
    // Listen to cluster events
    cluster.addListener(system.start(MemberListener::new, "memberListener"));
    // Periodically update metadata MEMBER_VERSION
    system.getDispatcherGroup().nextDispatcher().scheduleAtFixedRate(() -> {
        cluster.updateMetaInfo(MEMBER_VERSION.update(i -> i + 1));
    }, 3, 3, TimeUnit.SECONDS);
    // Periodically update metadata CUSTOM_META
    system.getDispatcherGroup().nextDispatcher().scheduleAtFixedRate(() -> {
        cluster.updateMetaInfo(CUSTOM_META.upsert(CustomMessage.newBuilder()
                .setId(ThreadLocalRandom.current().nextInt())
                .setContent(UUID.randomUUID().toString())
                .build()));
    }, 5, 5, TimeUnit.SECONDS);
}
```

## 4. Node Implementation

Provide main method implementations for each node in the cluster so that each node can be started
independently.

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

When running the above code, the console will display log output similar to the following:

```plain text
ts="..." level=INFO  th="..." logger=io.axor.example._04_ClusterSimpleExample$MemberListener actor=example@:/memberListener msg="ClusterMember[uid=694299120956080129, address=example@localhost:1101] meta info changed, version updated to 3"
ts="..." level=INFO  th="..." logger=io.axor.example._04_ClusterSimpleExample$MemberListener actor=example@:/memberListener msg="ClusterMember[uid=694299135824871425, address=example@localhost:1102] meta info changed, version updated to 2"
ts="..." level=INFO  th="..." logger=io.axor.example._04_ClusterSimpleExample$MemberListener actor=example@:/memberListener msg="ClusterMember[uid=694299120956080129, address=example@localhost:1101] meta info changed, custom message updated to [id: 156469909 content: "8cafeda0-ed6e-41a0-8d9a-d9c2eff0ba2a"]"
ts="..." level=INFO  th="..." logger=io.axor.example._04_ClusterSimpleExample$MemberListener actor=example@:/memberListener msg="ClusterMember[uid=694299140287594497, address=example@localhost:1103] meta info changed, version updated to 2"
ts="..." level=INFO  th="..." logger=io.axor.example._04_ClusterSimpleExample$MemberListener actor=example@:/memberListener msg="ClusterMember[uid=694299120956080129, address=example@localhost:1101] meta info changed, version updated to 4"...
...
```
