# AXOR 序列化说明

在使用 Actor 模型的上下文中，序列化是一个重要的组成部分。Axor 框架支持三种不同的序列化实现：自定义、Kryo
和 Protobuf。本文档将详细介绍这三种序列化方法及其用法。

## 支持的序列化类型

- **自定义序列化**：允许用户根据特定需求实现自己的序列化逻辑。
- **Kryo 序列化**：一个高效的 Java 对象序列化库，特别适合网络传输。
- **Protobuf 序列化**：由 Google 开发的一种语言中立、平台中立且可扩展的机制，用于序列化结构化数据。

## 示例

Axor 使用 `SerdeRegistry` 作为序列化注册中心，用于注册和获取序列化器。以下是三种序列化实现的示例。

### 自定义序列化示例

首先，我们定义一个 `ExampleMessage` 接口及其两个实现类，这些类将在稍后进行序列化。

```java
public sealed interface ExampleMessage {
}

public record Example1(long id, String name, int age) implements ExampleMessage {
}

public record Example2(String key, String value) implements ExampleMessage {
}
```

自定义序列化器，包括对 `ExampleMessage` 的两个实现类的具体序列化和反序列化逻辑。

```java
public static class ExampleRecordSerde implements BuiltinSerde<ExampleMessage> {

    @Override
    public void doSerialize(ExampleMessage obj, DataOutput out) throws IOException {
        switch (obj) {
            case Example1 e1 -> {
                out.writeByte(0);
                out.writeLong(e1.id());
                out.writeUTF(e1.name());
                out.writeInt(e1.age());
            }
            case Example2 e2 -> {
                out.writeByte(1);
                out.writeUTF(e2.key());
                out.writeUTF(e2.value());
            }
            case null, default -> {
                throw new IllegalArgumentException("未知的消息类型");
            }
        }
    }

    @Override
    public ExampleMessage doDeserialize(DataInput in) throws IOException {
        byte type = in.readByte();
        return switch (type) {
            case 0 -> new Example1(in.readLong(), in.readUTF(), in.readInt());
            case 1 -> new Example2(in.readUTF(), in.readUTF());
            default -> throw new IllegalArgumentException("未知的消息类型: " + type);
        };
    }

    @Override
    public MsgType<ExampleMessage> getType() {
        return MsgType.of(ExampleMessage.class);
    }
}
```

从 `SerdeRegistry` 获取 `BuiltinSerdeFactory` 并注册序列化器。

```java
ActorSystem system = ...
SerdeRegistry registry = system.getSerdeRegistry();
// 注册
registry.getFactory(BuiltinSerdeFactory .class)
        .register(ExampleMessage .class, new ExampleRecordSerde());
```

从 `SerdeRegistry` 中检索刚刚注册的序列化器。`ExampleMessage` 及其实现类都将返回最近注册的
`ExampleRecordSerde`。

```java
Serde<ExampleMessage> serde0 = registry.create(MsgType.of(ExampleMessage.class));
Serde<Example1> serde1 = registry.create(MsgType.of(Example1.class));
Serde<Example2> serde2 = registry.create(MsgType.of(Example2.class));
System.out.println(serde0.getClass().getSimpleName());
System.out.println(serde1.getClass().getSimpleName());
System.out.println(serde2.getClass().getSimpleName());
// 输出: ExampleRecordSerde
```

### Kryo 序列化示例

对于希望利用 Kryo 的高性能特性的场景，可以通过 `KryoSerdeFactory` 来快速配置 Kryo 序列化器:
首先对消息类型进行序列化注册，这个过程不是必须的，但是为消息类型分配一个唯一id可以提升序列化性能。如果没有注册，kryo
同样能支持序列化这个消息类型，并且控制台会输出一条告警消息

```java
SerdeRegistry registry = system.getSerdeRegistry();
// 注册序列化，这个步骤不是必须的，注册后可以提升消息序列化的性能，如果没有注册控制台会输出一个告警信息，
// 注意kryo序列化的注册id需要保证全局唯一，否则可能会出现问题
registry.getFactory(KryoSerdeFactory.class)
        .addInitializer(kryo ->{
            kryo.register(Example1 .class, 3001);
            kryo.register(Example2 .class, 3002);
        });
```

从序列化注册中心获取刚刚注册的序列化器，`ExampleMessage`和它的实现类都会返回刚刚注册的
`ExampleRecordSerde`

```java
Serde<Example1> serde1 = registry.create(MsgType.of(Example1.class));
Serde<Example2> serde2 = registry.create(MsgType.of(Example2.class));
System.out.println(serde1.getClass().getSimpleName());
System.out.println(serde2.getClass().getSimpleName());
// 输出：KryoSerde
```

### Protobuf 序列化示例

如果要序列化的消息类型是 `com.google.protobuf.MessageLite` 的子类，那么该类型将自动支持 Protobuf 序列化，而无需手动注册。

```java
Serde<SomeProtoMessage> serde1 = registry.create(MsgType.of(SomeProtoMessage.class));
Serde<SomeProtoMessage> serde2 = registry.create(MsgType.of(SomeProtoMessage.class));
System.out.println(serde1.getClass().getSimpleName());
        System.out.println(serde2.getClass().getSimpleName());
// 输出：ProtobufSerde
```

请注意，在上述代码中，对于 Protobuf 示例的实际输出应该反映一个特定于 Protobuf 的序列化器类，而不是 `KryoSerde`，
这表明原文可能存在不一致或错误。

## 优先级

在 Axor 框架中，当存在多种序列化方法时，系统会根据定义的优先级选择使用哪种方法。以下是序列化方法的优先级顺序（从高到低）：

1. **自定义序列化**：如果用户为特定类型注册了自定义序列化器，则首先使用该序列化器。
2. **Kryo 序列化**：如果没有找到自定义序列化器但用户通过 `KryoSerdeFactory` 注册了 Kryo 序列化器，则使用 Kryo 序列化。
3. **Protobuf 序列化**：如果消息类型是 `com.google.protobuf.MessageLite` 的子类，并且没有注册其他序列化器，则自动使用 Protobuf 序列化。

### 优先级示例

考虑以下场景：

- 为 `ExampleMessage` 类型注册了一个自定义序列化器。
- 通过 `KryoSerdeFactory` 为 `Example1` 和 `Example2` 类型注册了 Kryo 序列化器。
- `SomeProtoMessage` 类型是 `com.google.protobuf.MessageLite` 的子类，并且没有为其手动注册任何序列化器。

在这种情况下，优先级如下：

- 对于 `ExampleMessage` 类型及其实现类 `Example1` 和 `Example2`，将使用自定义序列化器。
- 如果没有为 `ExampleMessage` 类型注册自定义序列化器，但为 `Example1` 和 `Example2` 注册了 Kryo 序列化器，则使用 Kryo 序列化。
- 对于 `SomeProtoMessage` 类型，由于它是 `com.google.protobuf.MessageLite` 的子类并且没有注册其他序列化器，因此将自动使用 Protobuf 序列化。
