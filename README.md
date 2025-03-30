<img alt="icon.png" height="300" src="docs/images/icon.png" width="300"/>

# AXOR 项目简介

AXOR 是一个基于 Actor 模型的编程框架，旨在提供灵活、高效且易于使用的并发处理解决方案。它具有以下特点：

1. **可定制化的通信方式**：默认提供了 gRPC 的实现，用户可以根据需要自定义通信协议。
2. **灵活的序列化方案**：支持 Kryo、Protobuf 和自定义序列化器等多种序列化方式。
3. **高性能异步执行调度**：设计了良好性能的异步任务调度框架，确保高效率的消息传递和处理。
4. **基于 Micrometer 的监控**：内置 Micrometer 监控组件，方便地跟踪应用性能指标。
5. **类型安全且直观的 API**：提供了一套简洁易用的核心 API，保证开发过程中的类型安全性。
6. **基于 Gossip 协议的集群管理**：实现了基于 Gossip 协议的动态集群构建与管理功能，并支持集群单例模式及发布订阅机制。

## 当前主要模块

- **axor-runtime**：包含通信、序列化、异步调度等核心运行时逻辑，并对外暴露扩展点供开发者定制。
- **axor-api**：封装了一系列类型安全的基础接口，简化了 Actor 系统的操作流程。
- **axor-cluster**：实现了基于 Gossip 协议的集群功能，包括但不限于成员发现、元数据同步、发布订阅服务以及集群单例特性。

## 未来规划

- **axor-persistence**：将引入 Actor 状态持久化功能，确保状态的一致性和可靠性。
- **axor-cp**：计划采用 Raft 共识算法（通过 Apache Ratis 实现）来支持强一致性需求。
- **axor-sharding**：为大规模分布式系统提供分片支持，提高整体系统的伸缩性。

## 示例代码概览

### HelloWorld 示例
此示例展示了如何使用 AXOR 创建简单的 Actor 并进行基本的消息传递。通过 `ActorSystem` 启动两个 Actor（HelloWorldActor 和 HelloBot），并演示了如何利用 `ask` 模式请求响应。

```java
// 简化的 HelloWorld 示例代码
public class _01_HelloWorldExample {
    public static void main(String[] args) throws Exception {
        ActorSystem system = ActorSystem.create("example");
        ActorRef<Hello> actor = system.start(HelloWorldActor::new, "HelloWorld");
        // ... 更多代码 ...
    }
}
```

### 本地 PubSub 示例
该例子说明了如何在单一节点上实现发布订阅模式。创建多个订阅者监听特定主题的消息，并展示消息广播给所有订阅者或单独发送给某个订阅者的不同场景。

```java
// 本地 PubSub 示例代码片段
public class _02_LocalPubsubExample {
    public static void main(String[] args) throws Exception {
        ActorSystem system = ActorSystem.create("example");
        String pubsubName = "example-pubsub";
        // 初始化订阅者...
        // 发布消息...
    }
}
```

更多详细的示例代码请参阅项目源码目录下的相应文件。每个示例都针对 AXOR 不同方面进行了深入讲解，帮助开发者快速上手这一强大的并发处理工具。
