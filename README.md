<img alt="icon.png" height="300" src="docs/images/icon.png" width="300"/>

# AXOR

## 项目简介

AXOR 是一个基于 Actor 模型的编程框架，旨在提供灵活、高效且易于使用的并发处理解决方案。它具有以下特点：

1. **可定制化的通信方式**：默认提供了 gRPC 的实现，用户可以根据需要自定义通信协议。
2. **灵活的序列化方案**：支持 Kryo、Protobuf 和自定义序列化器等多种序列化方式。
3. **高性能异步执行调度**：设计了良好性能的异步任务调度框架，确保高效率的消息传递和处理。
4. **基于 Micrometer 的监控**：内置 Micrometer 监控组件，方便地跟踪应用性能指标。
5. **类型安全且直观的 API**：提供了一套简洁易用的actor模型 API，并且提供类型安全性保证。
6. **基于 Gossip 协议的集群管理**：实现了基于 Gossip 协议的动态集群构建与管理功能，并支持集群单例模式及发布订阅机制。

## 当前主要模块

- **axor-runtime**：包含通信、序列化、异步调度等核心运行时逻辑，并对外暴露扩展点供开发者定制。
- **axor-api**：封装了一系列类型安全的基础接口，简化了 Actor 系统的操作流程。
- **axor-cluster**：实现了基于 Gossip 协议的集群功能，包括但不限于成员发现、元数据同步、发布订阅服务以及集群单例特性。

## 快速开始

### 引入依赖

maven：

```xml

<dependency>
    <groupId>io.masterkun.axor</groupId>
    <artifactId>axor-cluster</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 使用样例

- [**HelloWorld 示例**](docs/example/helloworld.md)：演示了如何使用 Axor 框架创建一个简单的 Actor
  系统，包括定义消息类型、创建 Actors 以及实现基本的 Actor 间通信。
- [**远程通信示例**](docs/example/remote_contact.md)：通过服务器-客户端模型展示了 Axor 中两个不同系统内
  Actors 之间的远程消息传递过程，涉及消息类型的定义、Actor 创建及定时消息发送机制。
- [**本地发布订阅示例**](docs/example/local_pubsub.md)：展示了如何使用 Axor 实现本地发布订阅模式，包括设置
  Actor 系统、创建订阅者以及发布消息。
- [**集群示例**](docs/example/cluster_simple.md)：介绍如何利用 Axor
  构建并管理一个多节点集群，涵盖成员状态监听、元数据更新等功能。此示例还演示了 Gossip 协议下的集群发现与管理特性。
- [**集群发布订阅示例**](docs/example/cluster_pubsub.md)：介绍了如何在 Axor
  集群中实现发布订阅模式，涉及到定义消息类型、创建发布者和订阅者 Actor，以及这些 Actor 间的通信。
- [**集群单例示例**](docs/example/cluster_singleton.md)：提供了关于如何在 Axor 集群环境中配置和使用单例
  Actor 的指导，适用于需要保证唯一性的场景。

## 未来规划

- **axor-persistence**：将引入 Actor 状态持久化功能，确保状态的一致性和可靠性。
- **axor-cp**：计划采用 Raft 共识算法（通过 Apache Ratis 实现）来支持强一致性需求。
- **axor-sharding**：为大规模分布式系统提供分片支持，提高整体系统的伸缩性。
