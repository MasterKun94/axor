# AXOR Serialization Explanation

In the context of using the actor model, serialization is an important component. The Axor framework
supports three different serialization implementations: custom, Kryo, and Protobuf. This document
will provide a detailed introduction to these three serialization methods and their usage.

## Supported Serialization Types

- **Custom Serialization**: Allows users to implement their own serialization logic according to
  specific requirements.
- **Kryo Serialization**: An efficient Java object serialization library, particularly suitable for
  network transmission.
- **Protobuf Serialization**: A language-neutral, platform-neutral, extensible mechanism developed
  by Google for serializing structured data.

## Examples

Axor uses the `SerdeRegistry` as the serialization registration center for registering and acquiring
serializers. Below are examples of the three serialization implementations.

### Custom Serialization Example

First, we define an `ExampleMessage` interface along with its two implementation classes, which will
be serialized later.

```java
public sealed interface ExampleMessage {
}

public record Example1(long id, String name, int age) implements ExampleMessage {
}

public record Example2(String key, String value) implements ExampleMessage {
}
```

Custom serializer, which includes the specific serialization and deserialization logic for the two
implementation classes of `ExampleMessage`.

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
                throw new IllegalArgumentException("Unknown message type");
            }
        }
    }

    @Override
    public ExampleMessage doDeserialize(DataInput in) throws IOException {
        byte type = in.readByte();
        return switch (type) {
            case 0 -> new Example1(in.readLong(), in.readUTF(), in.readInt());
            case 1 -> new Example2(in.readUTF(), in.readUTF());
            default -> throw new IllegalArgumentException("Unknown message type: " + type);
        };
    }

    @Override
    public MsgType<ExampleMessage> getType() {
        return MsgType.of(ExampleMessage.class);
    }
}
```

Obtain the `BuiltinSerdeFactory` from the `SerdeRegistry` and register the serializer.

```java
ActorSystem system = ...
SerdeRegistry registry = system.getSerdeRegistry();
// 注册
registry.getFactory(BuiltinSerdeFactory.class)
        .register(ExampleMessage .class, new ExampleRecordSerde());
```

Retrieve the just-registered serializer from the `SerdeRegistry`. Both `ExampleMessage` and its
implementation classes will return the recently registered `ExampleRecordSerde`.

```java
Serde<ExampleMessage> serde0 = registry.create(MsgType.of(ExampleMessage.class));
Serde<Example1> serde1 = registry.create(MsgType.of(Example1.class));
Serde<Example2> serde2 = registry.create(MsgType.of(Example2.class));
System.out.println(serde0.getClass().getSimpleName());
System.out.println(serde1.getClass().getSimpleName());
System.out.println(serde2.getClass().getSimpleName());
// Output: ExampleRecordSerde
```

### Kryo Serialization Example

For scenarios where high performance from Kryo is desired, the Kryo serializer can be quickly
configured via `KryoSerdeFactory`. First, register the message types for serialization. Although
this step is not mandatory, assigning a unique ID to the message type can improve serialization
performance. If not registered, Kryo will still support serializing the message type, but a warning
message will be output to the console.

```java
SerdeRegistry registry = system.getSerdeRegistry();
// Register serialization, this step is not required. Registration can enhance the performance of message serialization. If not registered, a warning message will be displayed on the console. Note that the registration ID for Kryo serialization needs to be globally unique; otherwise, issues may arise.
registry.getFactory(KryoSerdeFactory .class)
        .addInitializer(kryo ->{
        kryo.register(Example1 .class, 3001);
            kryo.register(Example2 .class, 3002);
        });
```

Retrieve the just-registered serializer from the `SerdeRegistry`. Both `ExampleMessage` and its
implementation classes will return the recently registered `ExampleRecordSerde`.

```java
Serde<Example1> serde1 = registry.create(MsgType.of(Example1.class));
Serde<Example2> serde2 = registry.create(MsgType.of(Example2.class));
System.out.println(serde1.getClass().getSimpleName());
System.out.println(serde2.getClass().getSimpleName());
// Output: KryoSerde
```

### Protobuf Serialization Example

If the message type to be serialized is a subclass of `com.google.protobuf.MessageLite`, then the
type automatically supports Protobuf serialization without the need for manual registration.

```java
Serde<SomeProtoMessage> serde1 = registry.create(MsgType.of(SomeProtoMessage.class));
Serde<SomeProtoMessage> serde2 = registry.create(MsgType.of(SomeProtoMessage.class));
System.out.println(serde1.getClass().getSimpleName());
        System.out.println(serde2.getClass().getSimpleName());
// Output： ProtobufSerde
```

Please note, in the above code, the actual outputs for the Protobuf example should reflect a
Protobuf-specific serializer class instead of `KryoSerde`, indicating a possible inconsistency or
mistake in the original text.

## Priority

In the Axor framework, when multiple serialization methods are available, the system selects which
method to use based on a defined priority. The following is the order of priority for serialization
methods (from highest to lowest):

1. **Custom Serialization**: If a user has registered a custom serializer for a specific type, that
   serializer will be used first.
2. **Kryo Serialization**: If no custom serializer is found but the user has registered a Kryo
   serializer via `KryoSerdeFactory`, then Kryo serialization will be used.
3. **Protobuf Serialization**: If the message type is a subclass of
   `com.google.protobuf.MessageLite` and no other serializers have been registered, Protobuf
   serialization will be used automatically.

### Priority Example

Consider the following scenario:

- A custom serializer is registered for the `ExampleMessage` type.
- Kryo serializers are registered for the `Example1` and `Example2` types via `KryoSerdeFactory`.
- The `SomeProtoMessage` type is a subclass of `com.google.protobuf.MessageLite` and no serializers
  have been manually registered for it.

In this case, the priorities would be as follows:

- For the `ExampleMessage` type and its implementation classes `Example1` and `Example2`, the custom
  serializer will be used.
- If no custom serializer is registered for the `ExampleMessage` type but Kryo serializers are
  registered for `Example1` and `Example2`, Kryo serialization will be used.
- For the `SomeProtoMessage` type, since it is a subclass of `com.google.protobuf.MessageLite` and
  no other serializers have been registered, Protobuf serialization will be used automatically.
