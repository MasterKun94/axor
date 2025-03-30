# Axor HelloWorld 示例

[完整可执行代码在这里](../../axor-examples/src/main/java/io/masterkun/axor/example/_01_HelloWorldExample.java)

本示例展示了如何使用 Axor 框架创建一个简单的 Actor 系统。通过定义消息类型、创建 Actor 类以及在这些Actor
之间进行通信

## 1. 导入必要的包

```java
import io.masterkun.axor.api.*;
import io.masterkun.axor.runtime.MsgType;
```

## 2. 定义消息类型

为了在 Actors 之间传递消息，首先定义了两种消息类型 `Hello` 和 `HelloReply`。

```java
public record Hello(String msg) {
}

public record HelloReply(String msg) {
}
```

## 3. 创建 HelloWorldActor

`HelloWorldActor` 是接收 `Hello` 类型消息的 Actor，并回复 `HelloReply` 类型的消息给发送者。

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

`HelloBot` 是接收 `HelloReply` 类型消息的 Actor，并简单地记录接收到的消息。

```java
public static class HelloBot extends Actor<HelloReply> {
    private static final Logger LOG = LoggerFactory.getLogger(HelloBot.class);

    protected HelloBot(ActorContext<HelloReply> context) {
        super(context);
    }

    @Override
    public void onReceive(HelloReply helloBack) {
        LOG.info("Receive: {} from {}", helloBack, sender());
    }

    @Override
    public MsgType<HelloReply> msgType() {
        return MsgType.of(HelloReply.class);
    }
}
```

## 5. actor通信

创建了一个 Actor 系统，并启动了两个 Actor 实例：`HelloWorldActor` 和 `HelloBot`。

```java
ActorSystem system = ActorSystem.create("example");
ActorRef<Hello> actor = system.start(HelloWorldActor::new, "HelloWorld");

ActorRef<HelloReply> bot = system.start(HelloBot::new, "HelloBot");
actor.

tell(new Hello("Hello"),bot); // 向 HelloWorldActor 发送消息
```

执行后日志输出：

```text
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._01_HelloWorldExample$HelloWorldActor actor=example@:/HelloWorld msg="Receive: Hello[msg=Hello] from ActorRef[example@:/HelloBot]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._01_HelloWorldExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloReply[msg=Hello] from ActorRef[example@:/HelloWorld]"
```

## 6. 使用ask获取响应

此外，还可以通过 `ask` 模式向 `HelloWorldActor` 发送请求并获取响应。

```java
var reply = ActorPatterns.ask(actor, new Hello("Greeting"),
                MsgType.of(HelloReply.class),
                Duration.ofSeconds(1), system)
        .toFuture()
        .get();
LOG.

info("Receive reply: {} from {} by ask pattern",reply, actor);

```

执行后日志输出：

```text
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._01_HelloWorldExample$HelloWorldActor actor=example@:/HelloWorld msg="Receive: Hello[msg=Greeting] from ActorRef[example@:/sys/ask-pattern_0]"
ts="..." level=INFO  th="..." logger=io.masterkun.axor.example._01_HelloWorldExample actor= msg="Receive reply: HelloReply[msg=Greeting] from ActorRef[example@:/HelloWorld] by ask pattern"
```

