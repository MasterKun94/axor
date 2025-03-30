# Axor HelloWorld Example

[Full executable code is here](../../axor-examples/src/main/java/io/masterkun/axor/example/_01_HelloWorldExample.java)

This example demonstrates how to use the Axor framework to create a simple Actor system. It involves
defining message types, creating an Actor class, and facilitating communication between these
Actors.

## 1. Importing Necessary Packages

```java

import io.axor.runtime.MsgType;
```

## 2. Defining Message Types

To pass messages between Actors, two message types `Hello` and `HelloReply` are defined first.

```java
public record Hello(String msg) {
}

public record HelloReply(String msg) {
}
```

## 3. Creating the HelloWorldActor

`HelloWorldActor` is an Actor that receives messages of type `Hello` and replies with a message of
type `HelloReply` to the sender.

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

## 4. Creating the HelloBot

`HelloBot` is an Actor that receives messages of type `HelloReply` and simply logs the received
message.

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

## 5. Actor Communication

An Actor system is created, and two Actor instances, `HelloWorldActor` and `HelloBot`, are started.

```java
ActorSystem system = ActorSystem.create("example");
ActorRef<Hello> actor = system.start(HelloWorldActor::new, "HelloWorld");

ActorRef<HelloReply> bot = system.start(HelloBot::new, "HelloBot");
actor.

tell(new Hello("Hello"),bot); // Sending a message to HelloWorldActor
```

After execution, the log output is as follows:

```plain text
ts="..." level=INFO  th="..." logger=io.axor.example._01_HelloWorldExample$HelloWorldActor actor=example@:/HelloWorld msg="Receive: Hello[msg=Hello] from ActorRef[example@:/HelloBot]"
ts="..." level=INFO  th="..." logger=io.axor.example._01_HelloWorldExample$HelloBot actor=example@:/HelloBot msg="Receive: HelloReply[msg=Hello] from ActorRef[example@:/HelloWorld]"
```

## 6. Using Ask to Get a Response

Additionally, you can send a request to `HelloWorldActor` and get a response using the `ask`
pattern.

```java
var reply = ActorPatterns.ask(actor, new Hello("Greeting"),
                MsgType.of(HelloReply.class),
                Duration.ofSeconds(1), system)
        .toFuture()
        .get();
LOG.

info("Receive reply: {} from {} by ask pattern",reply, actor);
```

After execution, the log output is as follows:

```plain text
ts="..." level=INFO  th="..." logger=io.axor.example._01_HelloWorldExample$HelloWorldActor actor=example@:/HelloWorld msg="Receive: Hello[msg=Greeting] from ActorRef[example@:/sys/ask-pattern_0]"
ts="..." level=INFO  th="..." logger=io.axor.example._01_HelloWorldExample actor= msg="Receive reply: HelloReply[msg=Greeting] from ActorRef[example@:/HelloWorld] by ask pattern"
```
