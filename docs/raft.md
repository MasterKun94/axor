## 状态
* LEADER: 
* CANDIDATE:
* FOLLOWER: 有三个子状态
  * SYNCHRONIZER: 表示follower能和leader保持同步。表示健康的follower状态
  * CHASER: 表示follower和leader有较小延迟，预期可段时间内追赶上。如果peer短时间暂停或重启，可能处于此状态；
  * STRAGGLER: 表示follower和leader有较大延迟，需要较长时间追赶。如果peer出现长时间停止服务，可能处于此状态；


## 发送消息

* LEADER:
  * LogEntry: 当收到客户端事务请求后将事务请求封装为LogEntry下发到follower
  * 
