# Detailed Internal Principles of the Axor-Cluster Module

The `axor-cluster` module is specifically designed to build a decentralized, highly fault-tolerant,
and highly available cluster. This module adopts a series of advanced distributed system
technologies to achieve its goals, including the Gossip protocol and SWIM (Scalable
Weakly-consistent Infection-style Process Group Membership) protocol, among others. Additionally, in
terms of data consistency, `axor-cluster` has been optimized using the Vector Clock mechanism to
address potential data conflict issues.

## Cluster Member State Management: Gossip Protocol

### Overview of the Gossip Protocol

The Gossip protocol is a distributed communication pattern based on message passing, which mimics
the "rumor spreading" method in human society. In distributed systems, each node periodically
selects other nodes at random to exchange information, effectively spreading information without
needing a central control node. The Gossip protocol is known for its good scalability, robustness,
and self-healing capabilities, making it very suitable for state synchronization needs in
large-scale distributed systems.

### Combination of Push and Pull Modes

In `axor-cluster`, to improve efficiency and reliability, a combination of timed heartbeats along
with both push and pull modes is used for state updates:

- **Timed Heartbeats**: Nodes periodically send heartbeat messages to randomly selected other nodes,
  containing the latest vector clock information of all members recorded by that node.
- **Push Mode**: When a node detects a change in its local state, it proactively sends the changed
  information to other nodes.
- **Pull Mode**: When Node A receives a heartbeat or push message from Node B, it uses the vector
  clock to determine if there are any outdated states locally. If so, it sends a pull request to
  Node B to fetch the updated information.

This combined use ensures good state synchronization even under poor network conditions.

### Member State Machine

#### Cluster View State Machine

As shown in the diagram below, it illustrates the member state machine model used internally by
`axor-cluster`, defining the possible state transition logic between nodes:

![ClusterMembershipFSM.png](images/ClusterMembershipFSM.png)

##### Member States

Here's an introduction to the various states of the members:

- **UP**: Indicates that the node is in normal working condition.
- **SUSPICIOUS**: When a node does not receive heartbeat signals or other updates for a long time,
  it enters this state, indicating suspicion of potential failure.
- **DOWN**: When a node does not receive heartbeat signals or other updates for a long time but it
  is still uncertain whether the node will recover.
- **LEFT**: The node actively leaves the cluster, or it is confirmed that the node has failed and
  cannot be recovered.
- **REMOVED**: The node is removed from the cluster configuration.

##### State Transition Events

Explanation of the events triggering member state transitions:

| Transition Event | Trigger Condition                                                          | Remarks                                                  |
|------------------|----------------------------------------------------------------------------|----------------------------------------------------------|
| UPDATE           | Local state update                                                         | Mixed mode of incremental propagation and full retrieval |
| HEARTBEAT        | Sent periodically                                                          |                                                          |
| SUSPECT          | First detection of communication anomaly, such as timeout, connection loss | SWIM indirect confirmation mechanism                     |
| STRONG_SUSPECT   | Multiple failures detected by several nodes reaching a certain threshold   | SWIM indirect confirmation mechanism                     |
| FAIL             | Maximum threshold of multiple failures reached by several nodes            | SWIM indirect confirmation mechanism                     |
| LEAVE            | Node voluntarily leaves                                                    |                                                          |
| REMOVE           | Tombstone exceeds TTL (default 72 hours) garbage collection                | Lazy deletion strategy based on a time window            |

### Local View State Machine

As shown in the following diagram, this illustrates the state changes and transition logic seen by
the local application after a local node joins the cluster, where `+M` indicates adding a member and
`-M` indicates removing a member:

![LocalStateFSM.png](images/LocalStateFSM.png)

##### Local States

Introduction to the local states:

- **JOINING**: Indicates that the node is attempting to join the cluster.
- **HEALTHY**: The node has successfully joined the cluster, and the number of healthy members in
  the cluster meets the minimum requirement.
- **UNHEALTHY**: The node has successfully joined the cluster, but the number of healthy members in
  the cluster does not meet the minimum requirement, possibly due to network partitioning.
- **ORPHANED**: There is only this node in the cluster, possibly due to network partitioning.
- **LEAVING**: The node is attempting to leave the cluster.
- **LEFT**: The node officially leaves the cluster after receiving acknowledgment from other nodes.

### Fault Detection: SWIM Protocol

To quickly and accurately detect faulty nodes in the network, `axor-cluster` introduces the SWIM
protocol. SWIM is a lightweight and efficient fault detection algorithm that improves the accuracy
of fault detection through periodic heartbeat checks and multi-round verification of suspected
faulty nodes.

#### Working Principle of the SWIM Protocol

1. **Heartbeat Detection**:
    - Each node periodically sends heartbeat messages to other nodes.
    - If a node does not receive a heartbeat message from another node within a period, that node is
      marked as suspicious (SUSPICIOUS).

2. **Indirect Confirmation Mechanism**:
    - When a node is marked as suspicious, other nodes attempt to confirm the status of that node
      indirectly.
    - Specifically, Node A asks other nodes B if they have received messages from the suspicious
      node C. If multiple nodes report that they have not received messages from C, then C is
      further marked as strongly suspicious (STRONG_SUSPECT).

3. **Multi-Round Verification**:
    - For nodes in the strongly suspicious state, the system performs multiple rounds of
      verification to ensure the accuracy of fault detection.
    - If the node still cannot be reached after multiple verifications, it is finally marked as a
      failed node (FAIL).

4. **Self-Healing Mechanism**:
    - Once a failed node recovers and rejoins the cluster, it notifies other nodes through heartbeat
      messages.
    - Upon receiving the heartbeat message, other nodes update the node's status to normal working
      condition (UP).

## Data Consistency Management: Vector Clocks

### Introduction to Vector Clocks

A vector clock is a method used in distributed systems to maintain causal relationships. Each event
is represented by a globally unique vector that records the version numbers of all participants.
This allows the system not only to determine the order of two events but also to identify
concurrently occurring events.

### Optimized Vector Clocks

In `axor-cluster`, we have optimized the traditional vector clock:

- Under no-failure conditions, each member only updates its own state and broadcasts these changes
  to other nodes. At this point, the length of the vector clock is 1, automatically degenerating
  into a single version number, reducing network and memory overhead.
- When a failure is detected and multiple members attempt to modify the same faulty node's state,
  the full vector clock mechanism is restored to resolve potential data conflicts.

This flexible design ensures efficient operation with low latency while maintaining data consistency
in abnormal situations.
