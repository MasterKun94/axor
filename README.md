<div align="center">
<img alt="icon.png" src="docs/images/icon.png"/>
</div>

# AXOR
[切换语言：中文](README-cn)

## Project Overview

AXOR is a programming framework based on the Actor model, designed to provide a flexible, efficient,
and easy-to-use solution for concurrent processing, data communication, and cluster building. It
features the following:

1. **Customizable Communication Methods**: Provides a default implementation of gRPC, allowing users
   to customize communication protocols as needed.
2. **Flexible Serialization Solutions**: Supports multiple serialization methods including Kryo,
   Protobuf, and custom serializers.
3. **High-Performance Asynchronous Execution Scheduling**: Features a well-designed asynchronous
   task scheduling framework that ensures efficient message passing and handling.
4. **Monitoring Based on Micrometer**: Comes with built-in Micrometer monitoring components, making
   it easy to track application performance metrics.
5. **Type-Safe and Intuitive API**: Offers a set of simple and easy-to-use actor model APIs,
   ensuring type safety.
6. **Cluster Management Based on Gossip Protocol**: Implements dynamic cluster construction and
   management functions based on the Gossip protocol, supporting cluster singleton patterns and
   publish-subscribe mechanisms.

## Current Main Modules

- **axor-runtime**: Contains core runtime logic such as communication, serialization, and
  asynchronous scheduling, and exposes extension points for developers to customize.
- **axor-api**: Encapsulates a series of type-safe basic interfaces, simplifying the operation
  process of the Actor system.
- **axor-cluster**: Implements cluster functionality based on the Gossip protocol, including but not
  limited to member discovery, metadata synchronization, publish-subscribe services, and cluster
  singleton characteristics.

## Getting Started

### Adding Dependencies

maven:

```xml

<dependency>
    <groupId>io.axor</groupId>
    <artifactId>axor-cluster</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Usage Examples

- [**HelloWorld Example**](docs/example/helloworld-en.md): Demonstrates how to use the Axor
  framework
  to create a simple Actor system, including defining message types, creating Actors, and
  implementing basic inter-Actor communication.
- [**Remote Communication Example**](docs/example/remote_contact-en.md): Shows the remote message
  passing process between two different systems' Actors in Axor through a server-client model,
  involving the definition of message types, Actor creation, and scheduled message sending
  mechanisms.
- [**Local Publish-Subscribe Example**](docs/example/local_pubsub-en.md): Illustrates how to
  implement
  a local publish-subscribe pattern using Axor, including setting up the Actor system, creating
  subscribers, and publishing messages.
- [**Cluster Example**](docs/example/cluster_simple-en.md): Introduces how to build and manage a
  multi-node cluster using Axor, covering member state listening, metadata updates, and other
  functionalities. This example also demonstrates cluster discovery and management characteristics
  under the Gossip protocol.
- [**Cluster Publish-Subscribe Example**](docs/example/cluster_pubsub-en.md): Explains how to
  implement
  a publish-subscribe pattern within an Axor cluster, involving defining message types, creating
  publisher and subscriber Actors, and their communications.
- [**Cluster Singleton Example**](docs/example/cluster_singleton-en.md): Provides guidance on
  configuring and using singleton Actors in an Axor cluster environment, suitable for scenarios
  requiring uniqueness.

## Detailed Content
- [**Detailed Internal Principles of the Axor-Cluster Module**](docs/cluster-membership-en.md)
- [**Serialization Explanation**](docs/serialization-en.md)
- TODO

## Future Plans

- **axor-persistence**: Will introduce Actor state persistence to ensure consistency and reliability
  of states.
- **axor-cp**: Plans to adopt the Raft consensus algorithm (implemented via Apache Ratis) to support
  strong consistency requirements.
- **axor-sharding**: Aims to provide sharding support for large-scale distributed systems, enhancing
  the scalability of the entire system.
