# <u>Testing of New EMS Features</u>


## New features of EMS

- Support for **Resource-Limited (RL)** nodes, like edge devices or small VMs
- Support for **Self-Healing** monitoring topology (partially implemented)


## Definitions
We distinguish between ***Resource-Limited (RL)*** nodes and ***Normal or Non-RL*** nodes.

- **Normal nodes** are VMs have enough resources, where an EMS client will be installed, along with JRE and Netdata.
- **RL nodes** are VMs with few resources, where only Netdata will be installed.
- Currently, EMS will classify a VM as an RL node if:
    * it has 1 or 2 cores, or
    * it has 2GB of RAM or less, or
    * it has Total Disk space 1GB or less, or
    * its architecture name starts with `ARM`  (it will normally be `x86_64`).
    * Thresholds can be changed in `gr.iccs.imu.ems.baguette-client-install.properties` file.


We also distinguish between ***Monitoring Topologies***:

- **2-LEVEL Monitoring Topology**: Nodes send their metrics directly to EMS server.

    * Includes an EMS server, and any number of Normal and/or RL nodes.
    * No clustering occurs in 2-LEVEL topologies, hence Aggregator role is not used.
    * CAMEL Metric Models will only use `GLOBAL` and `PER_INSTANCE` groupings or no groupings at all (`GLOBAL` and `PER_INSTANCE` are then implied).

- **3-LEVEL Monitoring Topology**: Nodes send their metrics to cluster-wide Aggregators, then Aggregators send (composite) metrics to EMS server.

    * Includes an EMS server, Aggregators (one per cluster), and Normal and/or RL nodes.
    * Nodes are groupped into clusters. Each cluster has a node with the Aggregator role.
    * Only Normal nodes can be Aggregators.
    * There must be exactly one Aggregator per cluster.
    * Each cluster must have at least one Normal node (in order to become Aggregator).
    * CAMEL Metric Model will use `GLOBAL`, `PER_ZONE` / `PER_REGION` / `PER_CLOUD`, and `PER_INSTANCE` groupings.

  Clustering of nodes is used for faster failure detection, as well as distribution of load:
    - Only 3-LEVEL topologies are clustered.
    - 2-LEVEL topologies are not clustered.

  Currently, nodes are clustered based on their:
    - Availability Zone or Region or Cloud Service Provider, or
    - assigned to a default cluster.


------


## A) <u>Support for Resource-Limited nodes</u>
> Feature Quick Notes:
> - EMS server will NOT install EMS client and JRE in RL nodes.
> - EMS server will install Netdata in RL nodes.
> - EMS server or an Aggregator will periodically query Netdata agents of RL nodes for metrics.
> - Normal nodes will periodically query their Local Netdata agent for metrics.



### <u>Test Cases</u>

**A.1) Metrics collection from RL nodes in a 2-LEVEL topology**

> Test Case Quick Notes:
> - EMS server MUST log when it collects metrics from RL nodes.
> - EMS server MUST *NOT* log or collect metrics from Normal (Non-RL) nodes.
> - Normal nodes MUST log when they collect metrics from their Local Netdata agents. (The Log records are slightly different).

**You need a CAMEL model:**

* with two Requirement Sets:
    - for Normal nodes: 4 cores, 4GB RAM, >1 GB Disk, and
    - for RL nodes: 1-2 cores, or <2GB RAM, or <1GB Disk
* with 1-2 COMPONENTS using Requirement Set #1 (Normal nodes)
* with 1-2 COMPONENTS with Requirement Set #2 (RL nodes)
* with no Groupings in Metric Model

**After Application deployment you need to check the logs of:**

* ***EMS server***, for log messages about collecting metrics from RL-nodes' Netdata agents. E.g.

  ```
  e.m.e.c.c.netdata.NetdataCollector       : Collectors::Netdata: Collecting metrics from remote nodes (without EMS client): [192.168.32.2, 192.168.32.4]
  e.m.e.c.c.netdata.NetdataCollector       : Collectors::Netdata:   Collecting data from url: http://192.168.32.2:19999/api/v1/allmetrics?format=json
  e.m.e.c.c.netdata.NetdataCollector       : Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
  e.m.e.c.c.netdata.NetdataCollector       : Collectors::Netdata:   Collecting data from url: http://192.168.32.4:19999/api/v1/allmetrics?format=json
  e.m.e.c.c.netdata.NetdataCollector       : Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
  ```

* ***Normal nodes***, for log messages about collecting metrics from their Local Netdata agent

  ```
  Collectors::Netdata: Collecting metrics from local node...
  Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
  Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
  ```



**A.2)  Metrics collection from RL nodes in a 3-LEVEL topology**

> Test Case Quick Notes:
> - The Aggregator (it is a Normal node) MUST log each time it collects metrics from RL nodes in its cluster.
> - The Aggregator MUST *NOT* log or collect metrics from Normal (Non-RL) nodes in its cluster.
> - Normal nodes (including Aggregator) MUST log each time they collect metrics from their Local Netdata agents. (The Log records are slightly different).

**You need a CAMEL model:**

* with two Requirement Sets:
    - for Normal nodes: 4 cores, 4GB RAM, >1 GB Disk, and
    - for RL nodes: 1-2 cores, or <2GB RAM, or <1GB Disk
* with 1-2 COMPONENTS with Requirement Set #1 (Normal nodes)
* with 1-2 COMPONENTS with Requirement Set #2 (RL nodes)
* with three (3) Groupings used in the Metric Model  (`GLOBAL`, `PER_ZONE`, `PER_INSTANCE`)

**After Application deployment you need to check the logs of:**

* ***EMS server***, for NO logs related collecting metrics from any Netdata agent
* ***Aggregator node(s)***, for logs about collecting metrics from the Netdata agents of RL nodes, in the same cluster. E.g.

  ```
  Collectors::Netdata: Collecting metrics from local node...
  Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
  Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
  Collectors::Netdata: Collecting metrics from remote nodes (without EMS client): [192.168.96.2, 192.168.96.5]
  Collectors::Netdata:   Collecting data from url: http://192.168.96.2:19999/api/v1/allmetrics?format=json
  Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
  Collectors::Netdata:   Collecting data from url: http://192.168.96.5:19999/api/v1/allmetrics?format=json
  Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
  ```

* ***Normal nodes*** (including Aggregator node), for logs about collecting metrics from their Local Netdata agents. E.g.

  ```
  Collectors::Netdata: Collecting metrics from local node...
  Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
  Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
  ```



------

## B) <u>Support for Monitoring Self-Healing</u>
> Feature Quick Notes:
> - Self-Healing refers to recovering the monitoring software running at the nodes.
> - In Normal nodes, specifically refers to recovering of EMS client and/or Netdata agent.
> - In RL nodes, refers to recovering Netdata agent only.



#### Design Choices

1. Each EMS client (in a Normal node) is responsible for recovering the Local Netdata agent, collocated with it.
2. When clustering is used (i.e. in a 3-level topology), Aggregator is responsible for recovering other nodes in its cluster, both Normal and RL.
3. When clustering is not used (i.e. in a 2-level topology), EMS server is responsible for recovering nodes (both Normal and RL).



#### Self-Healing actions

We distinguish between monitoring topologies:

* **2-LEVEL Monitoring topology:** Only EMS server and nodes (Normal & RL) are used. No Aggregators or clustering.

    * EMS server will try to recover any <u>*Normal node*</u> that disconnects and not reconnects after a configured period of time.

      ***Condition:***

        * EMS client disconnects and not re-connects after X seconds

      ***Recovery steps taken by EMS server:***

        * SSH to node (assuming it is a VM)
        * Kill EMS client (if it is still running)
        * Launch EMS client
        * Close SSH connection
        * Wait for a configured period of time for recovered EMS client to reconnect to EMS server
        * After that period of time, the process is repeated (up to a configured number of retries, and then gives up).

    * EMS server will try to recovery any <u>*RL node*</u> with inaccessible Netdata agent.

      ***Condition:***

        * X consecutive connection failures to Netdata agent occur.

      ***Recovery steps taken by EMS server:***

        * SSH to node (assuming it is a VM)
        * Kill Netdata (if it is still running)
        * Launch Netdata
        * Close SSH connection
        * Reset the consecutive failures counter.


* **3-LEVEL Monitoring topology:** EMS server, Aggregators (one per cluster), and Nodes in clusters exist. Use of clustering.

    * <u>Aggregator</u> will try to recover any <u>*Normal node*</u> that leaves the cluster and not joins back in a configured period of time.

      ***Condition:***

        * EMS client leaves cluster and not joins back after X seconds

      ***Recovery steps taken by Aggregators:***

        * Contact EMS server to get node's credentials
        * SSH to node (assuming it is a VM)
        * Kill EMS client (if it is still running)
        * Launch EMS client
        * Close SSH connection
        * Wait for a configured period of time for EMS client to join back to cluster
        * After that period of time the process is repeated (up to a configured number of retries, and then it gives up and notifies EMS server)
        * When EMS client joins to cluster or in case of giving up, the node credentials are cleared from Aggregator's cache.

    * <u>Aggregator</u> will try to recover any <u>*RL node*</u> with inaccessible Netdata agent.

      ***Condition:***

        * X consecutive connection failures to Netdata agent occur.

      ***Recovery steps taken by Aggregators:***

        * Contact EMS server to get node's credentials
        * SSH to node (assuming it is a VM)
        * Kill Netdata agent (if it is still running)
        * Launch Netdata agent
        * Close SSH connection
        * Reset the consecutive failures counter
        * On successful connection to Netdata agent the node credentials are cleared from Aggregator cache.


* **2-LEVEL or 3-LEVEL Monitoring topology**

    * Any Normal node will try to recover its Local Netdata agent, if it becomes inaccessible.

      ***Condition:***

        * X consecutive connection failures to Local Netdata agent occur.

      ***Recovery steps (taken by NORMAL node):***

        * Kill Netdata agent (if it is still running)
        * Launch Netdata agent
        * Reset the consecutive failures counter



### <u>Test Cases for 2-LEVEL topology</u>

> ***PREREQUISITE:***
>
> You need a CAMEL model with a 2-LEVEL monitoring topology:
>
> * with two Requirement Sets:
>   - for Normal nodes: 4 cores, 4GB RAM, >1 GB Disk, and
>   - for RL nodes: 1-2 cores, or <2GB RAM, or <1GB Disk
> * with 1-2 components with Requirement Set #1 (Normal nodes)
> * with 1-2 components with Requirement Set #2 (RL nodes)
> * with no Groupings used in Metric Model.
>
> This CAMEL model is ***common*** to the following test cases, unless another CAMEL model is specified.
>
> CAMEL model MUST be re-deployed after each test case execution.



**B.1.a)  Successful recovery of an EMS client in a Normal node**

> Test Case Quick Notes:
> - Kill EMS client of any Normal node.
> - The EMS server will recover the killed EMS client after a configured period of time.
> - Check EMS server logs for disconnection, recovery actions and re-connection messages.

**After Application deployment...**

  * Connect to a Normal node and ***kill*** EMS client

**Next, check the logs of:**

  * ***EMS server***, for messages reporting an EMS client disconnection, the recovery attempt(s) and EMS client re-connection.

    *<p align="center">EMS server log: An EMS client disconnected</p>*
    ```
    e.m.e.b.server.ClientShellCommand        : #00000==> Signaling client to exit
    e.m.e.b.server.ClientShellCommand        : #00000--> Thread stops
    e.m.e.b.s.coordinator.NoopCoordinator    : TwoLevelCoordinator: unregister(): Method invoked. CSC: ClientShellCommand_#00000
    e.m.e.b.s.c.TwoLevelCoordinator          : TwoLevelCoordinator: --------------------------------------------------
    e.m.e.b.s.c.TwoLevelCoordinator          : TwoLevelCoordinator: Client unregistered: #00000 @ 172.29.0.3
    e.m.e.b.c.s.ClientRecoveryPlugin         : ClientRecoveryPlugin: processExitEvent(): client-id=#00000, client-address=172.29.0.3
    ```
    *<p align="center">EMS server log: EMS client recovery actions</p>*
    ```
    e.m.e.b.c.s.ClientRecoveryPlugin         : ClientRecoveryPlugin: runClientRecovery(): Starting client recovery: node-info=NodeRegistryEntry(ipAddress=172.29.0.3, clientId=VM-UBUNTU-vm1-vm1-AWS-vm1-85499eeb-14bc-481d-9c42-eac879845450, baguetteServer=eu.melodi
    o.a.s.c.k.AcceptAllServerKeyVerifier     : Server at /172.29.0.3:22 presented unverified EC key: SHA256:gNU4ScwysUpv050SaorPj7zlZrkiyGq4YSsOGBl+DCk
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Task #0: Session will be recorded in file: /logs/172.29.0.3-22-2022.02.16.09.33.31.121-0.txt
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Connected to remote host: task #0: host: 172.29.0.3:22
    e.m.e.b.c.install.SshClientInstaller     :
      ----------------------------------------------------------------------
      Task #0 :  Instruction Set: Restarting Baguette agent at VM node
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Task #0: Executing installation instructions set: Restarting Baguette agent at VM node
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Task #0: Executing instruction 1/2: Killing previous EMS client process
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Task #0: EXEC: /opt/baguette-client/bin/kill.sh
    o.a.s.c.session.ClientConnectionService  : globalRequest(ClientConnectionService[ClientSessionImpl[ubuntu@/172.29.0.3:22]])[hostkeys-00@openssh.com, want-reply=false] failed (SshException) to process: EdDSA provider not supported
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Task #0: EXEC: exit-status=0
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Task #0: Executing instruction 2/2: Starting new EMS client process
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Task #0: EXEC: /opt/baguette-client/bin/run.sh
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Task #0: EXEC: exit-status=0
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Task #0: Installation Instructions set succeeded: Restarting Baguette agent at VM node
    e.m.e.b.c.install.SshClientInstaller     :
      -------------------------------------------------------------------------
      Task #0 :  Instruction sets processed: successful=1, failed=0, exit-result=SUCCESS
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Disconnected from remote host: task #0: host: 172.29.0.3:22
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Task completed successfully #0
    e.m.e.b.c.s.ClientRecoveryPlugin         : ClientRecoveryPlugin: runClientRecovery(): Client recovery completed: result=true, node-info=NodeRegistryEntry(ipAddress=172.29.0.3, clientId=VM-UBUNTU-vm1-vm1-AWS-vm1-85499eeb-14bc-481d-9c42-eac879845450, baguetteSe
    ```
    *<p align="center">EMS server log: EMS client reconnected</p>*
    ```
    o.a.s.s.session.ServerUserAuthService    : Session user-bbb5b809-3296-485c-a605-cc8bae646bbb@/172.29.0.3:39696 authenticated
    e.m.e.b.server.ClientShellCommand        : #00001--> Got session : ServerSessionImpl[user-bbb5b809-3296-485c-a605-cc8bae646bbb@/172.29.0.3:39696]
    e.m.e.b.server.ClientShellCommand        : #00001==> Thread started
    e.m.e.b.server.ClientShellCommand        : #00001--> Client Id: VM-UBUNTU-vm1-vm1-AWS-vm1-85499eeb-14bc-481d-9c42-eac879845450
    e.m.e.b.server.ClientShellCommand        : #00001--> Broker URL: ssl://172.29.0.3:61617?daemon=true&trace=false&useInactivityMonitor=false&connectionTimeout=0&keepAlive=true
    e.m.e.b.server.ClientShellCommand        : #00001--> Broker Username: user-local-Q1mnKfNgzM
    e.m.e.b.server.ClientShellCommand        : #00001--> Broker Password: xityAHGDhIiVeAxJdfax
    e.m.e.b.server.ClientShellCommand        : #00001--> Broker Cert.: -----BEGIN CERTIFICATE-----
    .........................
    -----END CERTIFICATE-----
    e.m.e.b.server.ClientShellCommand        : #00001--> Adding/Replacing client certificate in Truststore: alias=172.29.0.3
    e.m.e.b.server.ClientShellCommand        : #00001--> Added/Replaced client certificate in Truststore: alias=172.29.0.3, CN=C=GR, ST=Attika, L=Athens, O=Institute of Communication and Computer Systems (ICCS), OU=Information Management Unit (IMU), CN=172.29.0.3, certificate-na
    e.m.e.b.s.coordinator.NoopCoordinator    : TwoLevelCoordinator: register(): Method invoked. CSC: ClientShellCommand_#00001
    e.m.e.b.s.c.TwoLevelCoordinator          : TwoLevelCoordinator: --------------------------------------------------
    e.m.e.b.s.c.TwoLevelCoordinator          : TwoLevelCoordinator: Sending grouping configurations to client #00001...
    .........................
    e.m.e.b.server.ClientShellCommand        : sendGroupingConfiguration: Serialization of Grouping configuration for PER_INSTANCE: rO0ABXNyACt.........................
    e.m.e.b.server.ClientShellCommand        : #00001==> PUSH : SET-GROUPING-CONFIG rO0ABXNyACt.........................
    e.m.e.b.s.c.TwoLevelCoordinator          : TwoLevelCoordinator: Sending grouping configurations to client #00001... done
    e.m.e.b.s.c.TwoLevelCoordinator          : TwoLevelCoordinator: --------------------------------------------------
    e.m.e.b.s.c.TwoLevelCoordinator          : TwoLevelCoordinator: Setting active grouping of client #00001: PER_INSTANCE
    e.m.e.b.server.ClientShellCommand        : #00001==> PUSH : SET-ACTIVE-GROUPING PER_INSTANCE
    e.m.e.b.s.c.TwoLevelCoordinator          : TwoLevelCoordinator: --------------------------------------------------
    e.m.e.b.server.ClientShellCommand        : #00001--> Client grouping changed: null --> PER_INSTANCE
    ```
  * ***Normal node where EMS client killed***, for EMS client's logs indicating its restart.
    *<p align="center">Normal node: EMS client restarts</p>*
    ```
    Starting baguette client...
    EMS_CONFIG_DIR=/opt/baguette-client/conf
    LOG_FILE=/opt/baguette-client/logs/output.txt
      ____                         _   _          _____ _ _            _
     |  _ \                       | | | |        / ____| (_)          | |
     | |_) | __ _  __ _ _   _  ___| |_| |_ ___  | |    | |_  ___ _ __ | |_
     |  _ < / _` |/ _` | | | |/ _ \ __| __/ _ \ | |    | | |/ _ \ '_ \| __|
     | |_) | (_| | (_| | |_| |  __/ |_| ||  __/ | |____| | |  __/ | | | |_
     |____/ \__,_|\__, |\__,_|\___|\__|\__\___|  \_____|_|_|\___|_| |_|\__|
                   __/ |
                  |___/
    Starting BaguetteClient v4.5.0-SNAPSHOT on 21845bcaf772 with PID 779 (/opt/baguette-client/jars/baguette-client-4.5.0-SNAPSHOT.jar started by ubuntu in /opt/baguette-client)
    No active profile set, falling back to default profiles: default
    loadCachedClientId: Used cached Client Id: null
    Password encoder class name is empty. Default instance of PasswordEncoder will be created
    .........................
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
    .........................
    ```
  * ***Other Normal nodes***, for NO logs indicating failure or recovery attempts.



**B.1.b)  Failed recovery of EMS client in a Normal node**

> Test Case Quick Notes:
> - Kill the VM of any Normal node.
> - The EMS server will try to connect to the affected VM but fail.
> - After a configured number of retries EMS server will give up.

**After Application deployment...**

  * Terminate the VM of a Normal node

**Next, check the logs of:**

  * ***EMS server***, for messages reporting an EMS client disconnection, failed recovery attempts and giving up recovery

    *<p align="center">EMS server log: An EMS client disconnected</p>*
    ```
    e.m.e.b.server.ClientShellCommand        : #00001==> Signaling client to exit
    e.m.e.b.server.ClientShellCommand        : #00001--> Thread stops
    e.m.e.b.s.coordinator.NoopCoordinator    : TwoLevelCoordinator: unregister(): Method invoked. CSC: ClientShellCommand_#00001
    e.m.e.b.s.c.TwoLevelCoordinator          : TwoLevelCoordinator: --------------------------------------------------
    e.m.e.b.s.c.TwoLevelCoordinator          : TwoLevelCoordinator: Client unregistered: #00001 @ 172.29.0.3
    e.m.e.b.c.s.ClientRecoveryPlugin         : ClientRecoveryPlugin: processExitEvent(): client-id=#00001, client-address=172.29.0.3
    ```
    *<p align="center">EMS server log: EMS client recovery actions and give up message</p>*
    ```
    e.m.e.b.c.s.ClientRecoveryPlugin         : ClientRecoveryPlugin: runClientRecovery(): Starting client recovery: node-info=NodeRegistryEntry(ipAddress=172.29.0.3, clientId=VM-UBUNTU-vm1-vm1-AWS-vm1-85499eeb-14bc-481d-9c42-eac879845450, baguetteServer=eu.melodi
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Error while connecting to remote host: task #0:
    java.net.NoRouteToHostException: No route to host
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.checkConnect(Native Method)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finishConnect(UnixAsynchronousSocketChannelImpl.java:252)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finish(UnixAsynchronousSocketChannelImpl.java:198)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.onEvent(UnixAsynchronousSocketChannelImpl.java:213)
            at sun.nio.ch.EPollPort$EventHandlerTask.run(EPollPort.java:293)
            at java.lang.Thread.run(Thread.java:748)
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Failed executing task #0, Exception:
    java.net.NoRouteToHostException: No route to host
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.checkConnect(Native Method)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finishConnect(UnixAsynchronousSocketChannelImpl.java:252)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finish(UnixAsynchronousSocketChannelImpl.java:198)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.onEvent(UnixAsynchronousSocketChannelImpl.java:213)
            at sun.nio.ch.EPollPort$EventHandlerTask.run(EPollPort.java:293)
            at java.lang.Thread.run(Thread.java:748)
    .........................
    .........................
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Retry 5/5 executing task #0
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Error while connecting to remote host: task #0:
    java.net.NoRouteToHostException: No route to host
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.checkConnect(Native Method)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finishConnect(UnixAsynchronousSocketChannelImpl.java:252)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finish(UnixAsynchronousSocketChannelImpl.java:198)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.onEvent(UnixAsynchronousSocketChannelImpl.java:213)
            at sun.nio.ch.EPollPort$EventHandlerTask.run(EPollPort.java:293)
            at java.lang.Thread.run(Thread.java:748)
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Failed executing task #0, Exception:
    java.net.NoRouteToHostException: No route to host
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.checkConnect(Native Method)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finishConnect(UnixAsynchronousSocketChannelImpl.java:252)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finish(UnixAsynchronousSocketChannelImpl.java:198)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.onEvent(UnixAsynchronousSocketChannelImpl.java:213)
            at sun.nio.ch.EPollPort$EventHandlerTask.run(EPollPort.java:293)
            at java.lang.Thread.run(Thread.java:748)
    
    e.m.e.b.c.install.SshClientInstaller     : SshClientInstaller: Giving up executing task #0 after 5 retries
    e.m.e.b.c.s.ClientRecoveryPlugin         : ClientRecoveryPlugin: runClientRecovery(): Client recovery completed: result=false, node-info=NodeRegistryEntry(ipAddress=172.29.0.3, clientId=VM-UBUNTU-vm1-vm1-AWS-vm1-85499eeb-14bc-481d-9c42-eac879845450, baguetteS
    ```
  * ***Normal nodes that operate***, for NO logs indicating any failure or recovery attempts



**B.2.a)  Successful recovery of a Netdata agent in a RL node**

> Test Case Quick Notes:
> - Kill Netdata agent of any RL node.
> - The EMS server will recover the killed Netdata agent after a configured period of time.
> - Check EMS server log messages reporting failures to collect metrics, recovery actions, and successful metrics collection.

**After Application deployment...**

  * Connect to a RL node and kill Netdata agent.
    
    *<p align="center">EMS server log: Failed metric collection attempts from a Netdata agent</p>*
    ```
    ......................... Not yet implemented
    ```

**Next, check the logs of:**

  * ***EMS server***, for logs reporting connection failure to a Netdata agent, and recovery actions.

    *<p align="center">EMS server log: Netdata agent recovery actions</p>*
    ```
    ......................... Not yet implemented
    ```
  * ***RL node with killed Netdata***, check if the Netdata processes have started again.
    *<p align="center">RL node shell: Recovered Netdata agent process</p>*
    ```
    ......................... Not yet implemented
    ```
  * ***Normal nodes (that operate)***, for NO Logs indicating failure or recovery attempts.



**B.2.b)  Failed recovery of a Netdata agent in a RL node**

> Test Case Quick Notes:
> - Kill the VM of any RL node.
> - The EMS server will try to connect to the affected VM but fail.
> - After a configured number of retries EMS server will give up.

**After Application deployment...**

  * Terminate the VM of a RL node

**You need to check the logs of:**

  * ***EMS server***, for logs reporting connection failure to a Netdata agent, and then a number of failed attempts to connect to VM.

    *<p align="center">EMS server log: Failed metric collection attempts from a Netdata agent</p>*
    ```
    ......................... Not yet implemented
    ```
    *<p align="center">EMS server log: Failed Netdata agent recovery actions and give up message</p>*
    ```
    ......................... Not yet implemented
    ```
  * ***Normal nodes (that operate)***, for NO logs indicating connection failures or recovery actions.



**B.3)  Successful recovery of a Netdata agent in a Normal node**

> Test Case Quick Notes:
> - Kill Netdata agent of any Normal node.
> - The EMS client of the node will recover the killed Netdata agent after a configured period of time.
> - Check EMS client's logs for messages reporting failures to collect metrics, recovery actions, and successful metrics collection.

**After Application deployment...**

  * Connect to a Normal node and kill Netdata agent.

**Next, check the logs of:**

  * ***EMS server***, for No log messages indicating connection failures to Netdata, or recovery actions.
  * ***Normal node with killed Netdata***, check if the Netdata processes have started again. Also check EMS client's log messages reporting failed metric collections, recovery actions, and successful metric collection.

    *<p align="center">Normal node - EMS client log: Failed attempts to collect metrics from <u><b>Local</b></u> Netdata agent</p>*
    ```
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Exception while collecting metrics from node: , #errors=1, exception: org.springframework.web.client.ResourceAccessException: I/O error on GET request for "http://127.0.0.1:19999/api/v1/allmetrics": Connection refused (Connection refused); nested exception is java.net.ConnectException: Connection refused (Connection refused) -> java.net.ConnectException: Connection refused (Connection refused)
    
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Exception while collecting metrics from node: , #errors=2, exception: org.springframework.web.client.ResourceAccessException: I/O error on GET request for "http://127.0.0.1:19999/api/v1/allmetrics": Connection refused (Connection refused); nested exception is java.net.ConnectException: Connection refused (Connection refused) -> java.net.ConnectException: Connection refused (Connection refused)
    
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Exception while collecting metrics from node: , #errors=3, exception: org.springframework.web.client.ResourceAccessException: I/O error on GET request for "http://127.0.0.1:19999/api/v1/allmetrics": Connection refused (Connection refused); nested exception is java.net.ConnectException: Connection refused (Connection refused) -> java.net.ConnectException: Connection refused (Connection refused)
    Collectors::Netdata: Too many consecutive errors occurred while attempting to collect metrics from node: , num-of-errors=3
    Collectors::Netdata: Will pause metrics collection from node for 60 seconds:
    SelfHealingPlugin: createRecoveryTask(): Created recovery task for Node: id=null, address=
    ```
    *<p align="center">Normal node - EMS client log: <u><b>Local</b></u> Netdata agent recovery actions</p>*
    ```
    SelfHealingPlugin: Retry #0: Recovering node: id=null, address=
    ShellRecoveryTask: runNodeRecovery(): Executing 3 recovery commands
    ##############  Initial wait......
    ##############  Waiting for 5000ms after Initial wait......
    ##############  Sending Netdata agent kill command......
    ##############  Waiting for 2000ms after Sending Netdata agent kill command......
    ##############  Sending Netdata agent start command......
    ##############  Waiting for 10000ms after Sending Netdata agent start command......
    ShellRecoveryTask: runNodeRecovery(): Executed 3 recovery commands
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Node is in ignore list:
     OUT> /opt/baguette-client
     ERR> -U: 1: -U: Syntax error: Unterminated quoted string
     ERR> 2022-02-16 10:23:29: netdata INFO  : MAIN : CONFIG: cannot load cloud config '/var/lib/netdata/cloud.d/cloud.conf'. Running with internal defaults.
    ```
    *<p align="center">Normal node - EMS client log: Successful metrics collection from <u><b>Local</b></u> Netdata agent</p>*
    ```
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Node is in ignore list:
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Node is in ignore list:
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Node is in ignore list:
    
    Collectors::Netdata: Resumed metrics collection from node:
    SelfHealingPlugin: cancelRecoveryTask(): Cancelled recovery task for Node: id=null, address=
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
    ```
  * ***Normal nodes (that operate)***, for NO logs indicating connection failures or recovery actions.



### <u>Test Cases for 3-LEVEL topology</u>

> ***PREREQUISITE:***
>
> You need a CAMEL model for 3-LEVEL topology:
>
> * with two Requirement Sets:
>   - for Normal nodes: 4 cores, 4GB RAM, >1 GB Disk, and
>   - for RL nodes: 1-2 cores, or <2GB RAM, or <1GB Disk,
> * with 1-2 COMPONENTS with Requirement Set #1 (Normal nodes)
> * with 1-2 COMPONENTS with Requirement Set #2 (RL nodes)
> * with three (3) Groupings used in the Metric Model  (`GLOBAL`, `PER_ZONE`, `PER_INSTANCE`).
>
> This CAMEL model is ***common*** to the following test cases, unless another CAMEL model is specified.
>
> CAMEL model MUST be re-deployed after each test case execution.



**B.4.a)  Successful recovery of an EMS client in a clustered Normal node**

> Test Case Quick Notes:
> - Kill EMS client of any Normal node except the Aggregator.
> - The Aggregator will recover the killed EMS client after a configured period of time.
> - Check Aggregator log messages for node leaving cluster, recovery actions, and node joining back.

**After Application deployment...**

  * Connect to a Normal node, except Aggregator, and ***kill*** EMS client

**Next, check the logs of:**

  * ***EMS server***, for Aggregator's query for node credentials.
    *<p align="center">EMS server log: Aggregator queries for node's credentials</p>*
    ```
    e.m.e.b.server.ClientShellCommand        : #00000==> PUSH : {"random":"cecab3d4-4c09-43b1-b6fa-3534d37bbc8f","zone-id":"IMU-ZONE","address":"192.168.16.4","provider":"AWS","name":"vm2","ssh.port":"22","ssh.username":"ubuntu","ssh.password":"ubuntu","id":"vm2","type":"VM","operatingSystem":"UBUNTU","CLIENT_ID":"VM-UBUNTU-vm2-vm2-AWS-vm2-cecab3d4-4c09-43b1-b6fa-3534d37bbc8f",.........................
    ```
    Note: EMS client disconnection from EMS server will also be logged in EMS server logs, but no recovery action will be taken by EMS server.
    
  * ***Aggregator***, for log messages about, (i) EMS client leaving cluster, (ii) recovery actions, and (iii) EMS client joining back to the cluster.
    *<p align="center">Aggregator log: An EMS client left cluster</p>*
    ```
    CLM: MEMBER_REMOVED: node=node_3866738cb0f4_2002
    BRU: Brokers after cluster change: [Member{id=node_581d745be52c_2001, address=192.168.16.3:2001, properties={aggregator-connection-configuration=eyJncm91cGluZyI6I.........................
    SEND: SERVER-GET-NODE-SSH-CREDENTIALS 192.168.16.4
    SelfHealingPlugin: createRecoveryTask(): Created recovery task for Node: id=node_3866738cb0f4_2002, address=192.168.16.4
    ```
    *<p align="center">Aggregator log: EMS client recovery actions</p>*
    ```
    SelfHealingPlugin: Retry #0: Recovering node: id=node_3866738cb0f4_2002, address=192.168.16.4
    VmNodeRecoveryTask: connectToNode(): Connecting to node using SSH: address=192.168.16.4, port=22, username=ubuntu
    Connecting to server...
    SSH client is ready
    VmNodeRecoveryTask: runNodeRecovery(): Executing 3 recovery commands
    ##############  Initial wait......
    ##############  Waiting for 5000ms after Initial wait......
    ##############  Sending baguette client kill command......
    ##############  Waiting for 2000ms after Sending baguette client kill command......
    ##############  Sending baguette client start command......
    ##############  Waiting for 10000ms after Sending baguette client start command......
    SET-CLIENT-CONFIG rO0ABXNyAClldS5tZWxvZGljLmV2ZW50LnV0aWwuQ2xpZW50Q29uZmlndXJhdGlvbiAe4raCjfZzAgABTAASbm9kZXNXaXRob3V0Q2xpZW50dAAPTGphdmEvdXRpbC9TZXQ7eHBzcgARamF2YS51dGlsLkhhc2hTZXS6RIWVlri3NAMAAHhwdwwAAAAQP0AAAAAAAAB4
    New client config.: ClientConfiguration(nodesWithoutClient=[])
    VmNodeRecoveryTask: runNodeRecovery(): Executed 3 recovery commands
    VmNodeRecoveryTask: disconnectFromNode(): Disconnecting from node: address=192.168.16.4, port=22, username=ubuntu
    Stopping SSH client...
    SSH client stopped
     OUT> Last login: Sat Feb 12 10:40:09 2022 from 172.29.0.4
     OUT>
     OUT> pwd
     OUT> ubuntu@3866738cb0f4:~$ pwd
     OUT> /home/ubuntu
     OUT> ubuntu@3866738cb0f4:~$ /opt/baguette-client/bin/kill.sh
     OUT> Baguette client is not running
     OUT> ubuntu@3866738cb0f4:~$ /opt/baguette-client/bin/run.sh
     OUT> Starting baguette client...
     OUT> EMS_CONFIG_DIR=/opt/baguette-client/conf
     OUT> LOG_FILE=/opt/baguette-client/logs/output.txt
     OUT> Baguette client PID:   973
    VmNodeRecoveryTask: redirectSshOutput(): Connection closed: id=OUT
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
    ```
    *<p align="center">Aggregator log: EMS client joined back to cluster</p>*
    ```
    CLM: MEMBER_ADDED: node=node_3866738cb0f4_2002
    BRU: Brokers after cluster change: [Member{id=node_581d745be52c_2001, address=192.168.16.3:2001, properties={aggregator-connection-configuration=eyJncm91cGluZyI6I.........................
    SelfHealingPlugin: cancelRecoveryTask(): Cancelled recovery task for Node: id=node_3866738cb0f4_2002, address=192.168.16.4
    ```
  * ***Normal node whose EMS client killed***, for EMS client's logs indicating its restart.
    *<p align="center">Normal node: EMS client restarts</p>*
    ```
    Starting baguette client...
    EMS_CONFIG_DIR=/opt/baguette-client/conf
    LOG_FILE=/opt/baguette-client/logs/output.txt
      ____                         _   _          _____ _ _            _
     |  _ \                       | | | |        / ____| (_)          | |
     | |_) | __ _  __ _ _   _  ___| |_| |_ ___  | |    | |_  ___ _ __ | |_
     |  _ < / _` |/ _` | | | |/ _ \ __| __/ _ \ | |    | | |/ _ \ '_ \| __|
     | |_) | (_| | (_| | |_| |  __/ |_| ||  __/ | |____| | |  __/ | | | |_
     |____/ \__,_|\__, |\__,_|\___|\__|\__\___|  \_____|_|_|\___|_| |_|\__|
                   __/ |
                  |___/
    Starting BaguetteClient v4.5.0-SNAPSHOT on 3866738cb0f4 with PID 973 (/opt/baguette-client/jars/baguette-client-4.5.0-SNAPSHOT.jar started by ubuntu in /opt/baguette-client)
    No active profile set, falling back to default profiles: default
    loadCachedClientId: Used cached Client Id: null
    Password encoder class name is empty. Default instance of PasswordEncoder will be created
    PasswordUtil.setPasswordEncoder(): PasswordEncoder set to: password.gr.iccs.imu.ems.util.AsterisksPasswordEncoder
    PasswordUtil: Initialized default Password Encoder: password.gr.iccs.imu.ems.util.AsterisksPasswordEncoder
    BrokerConfig.initializeKeyAndCert(): Initializing keystore, truststore and certificate for Broker-SSL...
    KeystoreUtil.initializeKeystoresAndCertificate(): Initializing keystores and certificate
    BrokerConfig.initializeKeyAndCert(): Initializing keystore, truststore and certificate for Broker-SSL... done
    BrokerConfig: Creating new Broker Service instance: url=ssl://0.0.0.0:61617
    .........................
    .........................
    CLUSTER-JOIN IMU-ZONE  GLOBAL:PER_ZONE:PER_INSTANCE  start-election=true  192.168.16.4:2002  192.168.16.3:2001
    CLUSTER-JOIN ARGS: cluster-id=IMU-ZONE, groupings=GLOBAL:PER_ZONE:PER_INSTANCE, local-node=192.168.16.4:2002, other-nodes=[192.168.16.3:2001]
    CLUSTER-JOIN ARGS: Groupings: global=GLOBAL, aggregator=PER_ZONE, node=PER_INSTANCE
    CLM: Local address used for building Atomix: 192.168.16.4:2002
    CLM: Building Atomix: Other members: [Node{id=node_3866738cb0f4_2001, address=192.168.16.3:2001}]
    .........................
    .........................
    CLUSTER-EXEC broker list
    Cluster executes command: broker list
    CLI: Node status and scores:
    CLI:    node_581d745be52c_2001  [AGGREGATOR, 0.6640625, 9e790362-704c-4d9e-aa74-77f76e297816]
    CLI:    node_3866738cb0f4_2002  [CANDIDATE, 0.6640625, 44a5afb7-890a-4090-9f80-c65f046aeddd]
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
    ```
  * ***Other Normal nodes***, for logs about, (i) EMS client leaving cluster, (ii) EMS client joining to cluster, but NO logs about recovery actions.



**B.4.b)  Failed recovery of an EMS client in a clustered Normal node**

> Test Case Quick Notes:
> - Kill the VM of any Normal node, except Aggregator.
> - The Aggregator will try to connect to the affected VM but fail.
> - After a configured number of retries Aggregator will give up.

**After Application deployment...**

  * Terminate the VM of a Normal node, except the Aggregator's

**Next, check the logs of:**

  * ***EMS server***, for a recovery Give up message from Aggregator
    *<p align="center">EMS server log: Aggregator queries for node's credentials</p>*
    ```
    e.m.e.b.server.ClientShellCommand        : #00000==> PUSH : {"random":"cecab3d4-4c09-43b1-b6fa-3534d37bbc8f","zone-id":"IMU-ZONE","address":"192.168.16.4","provider":"AWS","name":"vm2","ssh.port":"22","ssh.username":"ubuntu","ssh.password":"ubuntu","id":"vm2","type":"VM","operatingSystem":"UBUNTU","CLIENT_ID":"VM-UBUNTU-vm2-vm2-AWS-vm2-cecab3d4-4c09-43b1-b6fa-3534d37bbc8f",.........................
    ```
    *<p align="center">EMS server log: Aggregator give up message</p>*
    ```
    e.m.e.b.server.ClientShellCommand        : #00000--> Client notification: CMD=RECOVERY, ARGS=GIVE_UP node_3866738cb0f4_2002 @ 192.168.16.4
    e.m.e.b.server.ClientShellCommand        : #00000--> Client Recovery Notification: GIVE_UP: node_3866738cb0f4_2002 @ 192.168.16.4
    ```
    Note: EMS client disconnection from EMS server will also be logged in EMS server logs, but no recovery action will be taken by EMS server.

  * ***Aggregator***, for messages reporting, (i) an EMS client left cluster, (ii) a number of failed connection attempts to the VM, and (iii) a recovery give up message.
    *<p align="center">Aggregator log: An EMS client left cluster</p>*
    ```
    CLM: MEMBER_REMOVED: node=node_3866738cb0f4_2002
    BRU: Brokers after cluster change: [Member{id=node_581d745be52c_2001, address=192.168.16.3:2001, properties={aggregator-connection-configuration=eyJncm91cGluZyI6I.........................
    SEND: SERVER-GET-NODE-SSH-CREDENTIALS 192.168.16.4
    SelfHealingPlugin: createRecoveryTask(): Created recovery task for Node: id=node_3866738cb0f4_2002, address=192.168.16.4
    ```
    *<p align="center">Aggregator log: EMS client recovery actions and give up message</p>*
    ```
    SelfHealingPlugin: Retry #0: Recovering node: id=node_3866738cb0f4_2002, address=192.168.16.4
    VmNodeRecoveryTask: connectToNode(): Connecting to node using SSH: address=192.168.16.4, port=22, username=ubuntu
    Connecting to server...
    SelfHealingPlugin: EXCEPTION while recovering node: node-address=192.168.16.4 -- Exception:
    java.net.NoRouteToHostException: No route to host
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.checkConnect(Native Method)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finishConnect(UnixAsynchronousSocketChannelImpl.java:252)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finish(UnixAsynchronousSocketChannelImpl.java:198)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.onEvent(UnixAsynchronousSocketChannelImpl.java:213)
            at sun.nio.ch.EPollPort$EventHandlerTask.run(EPollPort.java:293)
            at java.lang.Thread.run(Thread.java:748)
    .........................
    .........................
    SelfHealingPlugin: Retry #3: Recovering node: id=node_3866738cb0f4_2002, address=192.168.16.4
    VmNodeRecoveryTask: connectToNode(): Connecting to node using SSH: address=192.168.16.4, port=22, username=ubuntu
    Connecting to server...
    SelfHealingPlugin: EXCEPTION while recovering node: node-address=192.168.16.4 -- Exception:
    java.net.NoRouteToHostException: No route to host
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.checkConnect(Native Method)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finishConnect(UnixAsynchronousSocketChannelImpl.java:252)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finish(UnixAsynchronousSocketChannelImpl.java:198)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.onEvent(UnixAsynchronousSocketChannelImpl.java:213)
            at sun.nio.ch.EPollPort$EventHandlerTask.run(EPollPort.java:293)
            at java.lang.Thread.run(Thread.java:748)
    ```
    ```
    SelfHealingPlugin: Max retries reached. No more recovery retries for node: id=node_3866738cb0f4_2002, address=192.168.16.4
    SelfHealingPlugin: cancelRecoveryTask(): Cancelled recovery task for Node: id=node_3866738cb0f4_2002, address=192.168.16.4
    NOTIFY-X: RECOVERY GIVE_UP node_3866738cb0f4_2002 @ 192.168.16.4
    ```
  * ***Normal nodes that operate***, for logs about EMS client leaving cluster, and NO logs about recovery actions or EMS client joining back.



**B.5.a)  Successful recovery of EMS client of the cluster Aggregator**

> Test Case Quick Notes:
> - Kill EMS client of the Aggregator.
> - The cluster nodes will elect a new Aggregator. Check logs of any cluster node.
> - The new Aggregator will recover the killed EMS client after a configured period of time.
> - Check new Aggregator log messages for node leaving cluster, being elected as Aggregator, recovery actions, and node joining back.
> - Old Aggregator will join back as a Normal node.

**After Application deployment...**

  * Connect to the Aggregator node, and ***kill*** EMS client.

**Next, check the logs of:**

  * ***EMS server***, for message about Aggregator change.
    *<p align="center">EMS server log: A new Aggregator initialized</p>*
    ```
    e.m.e.b.server.ClientShellCommand        : #00003--> Client status changed: CANDIDATE --> INITIALIZING
    e.m.e.b.server.ClientShellCommand        : #00003--> Client grouping changed: PER_INSTANCE --> PER_ZONE
    e.m.e.b.s.c.c.ClusteringCoordinator      : Updated aggregator of zone: IMU-ZONE -- New aggregator: #00003 @ 192.168.16.4 (VM-UBUNTU-vm2-vm2-AWS-vm2-cecab3d4-4c09-43b1-b6fa-3534d37bbc8f)
    e.m.e.b.server.ClientShellCommand        : #00003--> Client status changed: INITIALIZING --> AGGREGATOR
    ```
    *<p align="center">EMS server log: Aggregator queries for node's credentials</p>*
    ```
    e.m.e.b.server.ClientShellCommand        : #00003==> PUSH : {"random":"8a20f11c-eaf2-4b6e-b827-d8a25a57cb0a","zone-id":"IMU-ZONE","address":"192.168.16.3","provider":"AWS",.........................
    ```
    Note: Aggregator disconnection from EMS server will also be logged in EMS server logs, but no recovery action will be taken by EMS server.

  * ***New Aggregator***, for log messages about, (i) EMS client leaving cluster, (ii) being elected as Aggregator, (iii) recovery actions, and (iv) EMS client joining to cluster.
    *<p align="center">New Aggregator log: Old Aggregator left cluster - New Aggregator election</p>*
    ```
    CLM: MEMBER_REMOVED: node=node_581d745be52c_2001
    BRU: Brokers after cluster change: []
    
    BRU: Broker election requested: broadcasting election message...
    BRU: **** Broker message received: election
    BRU: **** BROKER: Starting Broker election:
    BRU: Member-Score: node_3866738cb0f4_2002 => 0.6640625  d4f2eb55-c355-4715-8a27-9f7c12c32924
    BRU: Broker: node_3866738cb0f4_2002
    ```
    *<p align="center">New Aggregator log: Initializing to become the new Aggregator</p>*
    ```
    BRU: Node will become Broker. Initializing...
    NOTIFY-STATUS-CHANGE: INITIALIZING
    initialize(): Node starts initializing as Aggregator...
    .........................
    .........................
    Notifying Baguette Server i am the new aggregator
    .........................
    .........................
    BRU: Node is ready to act as Aggregator. Ready
    BRU: **** Broker message received: ready node_3866738cb0f4_2002 New config: eyJncm91cGluZyI6IlBFUl9aT05FIiwidXJsIjoic3NsOi8vMTkyLjE2OC4xNi40OjYxNjE3P2RhZW1vbj10cn.........................
    BRU: **** BROKER: New Broker is ready: node_3866738cb0f4_2002, New config: eyJncm91cGluZyI6IlBFUl9aT05FIiwidXJsIjoic3NsOi8vMTkyLjE2OC4xNi40OjYxNjE3P2RhZW1vbj10cn.........................
    BRU: Node configuration updated: eyJncm91cGluZyI6IlBFUl9aT05FIiwidXJsIjoic3NsOi8vMTkyLjE2OC4xNi40OjYxNjE3P2RhZW1vbj10cn.........................
    ```
    *<p align="center">New Aggregator log: Requesting old Aggregator node's credentials</p>*
    ```
    SEND: SERVER-GET-NODE-SSH-CREDENTIALS 192.168.16.3
    SelfHealingPlugin: createRecoveryTask(): Created recovery task for Node: id=node_581d745be52c_2001, address=192.168.16.3
    ```
    *<p align="center">New Aggregator log: Recovery actions of old Aggregator</p>*
    ```
    SelfHealingPlugin: Retry #0: Recovering node: id=node_581d745be52c_2001, address=192.168.16.3
    VmNodeRecoveryTask: connectToNode(): Connecting to node using SSH: address=192.168.16.3, port=22, username=ubuntu
    Connecting to server...
    SSH client is ready
    VmNodeRecoveryTask: runNodeRecovery(): Executing 3 recovery commands
    ##############  Initial wait......
    ##############  Waiting for 5000ms after Initial wait......
    ##############  Sending baguette client kill command......
    ##############  Waiting for 2000ms after Sending baguette client kill command......
    ##############  Sending baguette client start command......
    ##############  Waiting for 10000ms after Sending baguette client start command......
    SET-CLIENT-CONFIG rO0ABXNyAClldS5tZWxvZGljLmV2ZW50LnV0aWwuQ2xpZW50Q29uZmlndXJhdGlvbiAe4raCjfZzAgABTAASbm9kZXNXaXRob3V0Q2xpZW50dAAPTGphdmEvdXRpbC9TZXQ7eHBzcgARamF2YS51dGlsLkhhc2hTZXS6RIWVlri3NAMAAHhwdwwAAAAQP0AAAAAAAAB4
    New client config.: ClientConfiguration(nodesWithoutClient=[])
    VmNodeRecoveryTask: runNodeRecovery(): Executed 3 recovery commands
    VmNodeRecoveryTask: disconnectFromNode(): Disconnecting from node: address=192.168.16.3, port=22, username=ubuntu
    Stopping SSH client...
    SSH client stopped
     OUT> Last login: Sat Feb 12 10:40:09 2022 from 172.29.0.4
     OUT>
     OUT> pwd
     OUT> ubuntu@581d745be52c:~$ pwd
     OUT> /home/ubuntu
     OUT> ubuntu@581d745be52c:~$ /opt/baguette-client/bin/kill.sh
     OUT> Baguette client is not running
     OUT> ubuntu@581d745be52c:~$ /opt/baguette-client/bin/run.sh
     OUT> Starting baguette client...
     OUT> EMS_CONFIG_DIR=/opt/baguette-client/conf
     OUT> LOG_FILE=/opt/baguette-client/logs/output.txt
     OUT> Baguette client PID:  1242
    VmNodeRecoveryTask: redirectSshOutput(): Connection closed: id=OUT
    ```
    *<p align="center">New Aggregator log: Old Aggregator joins back to cluster as plain node</p>*
    ```
    CLM: MEMBER_ADDED: node=node_581d745be52c_2001
    BRU: Brokers after cluster change: [Member{id=node_581d745be52c_2001, address=192.168.16.3:2001, properties={aggregator-connection-configuration=eyJncm91cGluZyI6I.........................
    SelfHealingPlugin: cancelRecoveryTask(): Cancelled recovery task for Node: id=node_581d745be52c_2001, address=192.168.16.3
    ```
  * ***Old Aggregator node whose EMS client killed***, for EMS client's logs indicating its restart (as a `PER_INSTANCE` node).
    *<p align="center">Normal node: Old Aggregator restarts as a plain Normal node</p>*
    ```
    Starting baguette client...
    EMS_CONFIG_DIR=/opt/baguette-client/conf
    LOG_FILE=/opt/baguette-client/logs/output.txt
      ____                         _   _          _____ _ _            _
     |  _ \                       | | | |        / ____| (_)          | |
     | |_) | __ _  __ _ _   _  ___| |_| |_ ___  | |    | |_  ___ _ __ | |_
     |  _ < / _` |/ _` | | | |/ _ \ __| __/ _ \ | |    | | |/ _ \ '_ \| __|
     | |_) | (_| | (_| | |_| |  __/ |_| ||  __/ | |____| | |  __/ | | | |_
     |____/ \__,_|\__, |\__,_|\___|\__|\__\___|  \_____|_|_|\___|_| |_|\__|
                   __/ |
                  |___/
    Starting BaguetteClient v4.5.0-SNAPSHOT on 581d745be52c with PID 1242 (/opt/baguette-client/jars/baguette-client-4.5.0-SNAPSHOT.jar started by ubuntu in /opt/baguette-client)
    No active profile set, falling back to default profiles: default
    loadCachedClientId: Used cached Client Id: null
    Password encoder class name is empty. Default instance of PasswordEncoder will be created
    PasswordUtil.setPasswordEncoder(): PasswordEncoder set to: password.gr.iccs.imu.ems.util.AsterisksPasswordEncoder
    PasswordUtil: Initialized default Password Encoder: password.gr.iccs.imu.ems.util.AsterisksPasswordEncoder
    BrokerConfig.initializeKeyAndCert(): Initializing keystore, truststore and certificate for Broker-SSL...
    KeystoreUtil.initializeKeystoresAndCertificate(): Initializing keystores and certificate
    BrokerConfig.initializeKeyAndCert(): Initializing keystore, truststore and certificate for Broker-SSL... done
    .........................
    .........................
    CLM: Joining cluster...
    NOTIFY-STATUS-CHANGE: CANDIDATE
    .........................
    .........................
    Joined to cluster
    .........................
    .........................
    CLUSTER-EXEC broker list
    Cluster executes command: broker list
    CLI: Node status and scores:
    CLI:    node_3866738cb0f4_2002  [AGGREGATOR, 0.6640625, d4f2eb55-c355-4715-8a27-9f7c12c32924]
    CLI:    node_581d745be52c_2001  [CANDIDATE, 0.6640625, e974ebcd-e11e-4baa-b3cb-fa34242705ff]
    ```
  * ***Other Normal nodes***, for log messages about, (i) EMS client leaving cluster, (ii) Aggregator election, (iii) EMS client joining to cluster, but NO logs about recovery actions.



**B.5.b)  Failed recovery of EMS client of the cluster Aggregator**

> Test Case Quick Notes:
> - Kill the VM of the Aggregator.
> - The cluster nodes will elect a new Aggregator. Check logs of any cluster node.
> - The new Aggregator will try to connect to the affected VM but fail.
> - After a configured number of retries new Aggregator will give up.

**After Application deployment...**

  * Terminate the VM of the Aggregator

**Next, check the logs of:**

  * ***EMS server***, for one message about Aggregator change, and one about new Aggregator giving up recovery.
    *<p align="center">EMS server log: A new Aggregator initialized</p>*
    ```
    e.m.e.b.server.ClientShellCommand        : #00004--> Client status changed: CANDIDATE --> INITIALIZING
    e.m.e.b.server.ClientShellCommand        : #00004--> Client grouping changed: PER_INSTANCE --> PER_ZONE
    e.m.e.b.s.c.c.ClusteringCoordinator      : Updated aggregator of zone: IMU-ZONE -- New aggregator: #00004 @ 192.168.16.3 (VM-UBUNTU-vm1-vm1-AWS-vm1-8a20f11c-eaf2-4b6e-b827-d8a25a57cb0a)
    e.m.e.b.server.ClientShellCommand        : #00004--> Client status changed: INITIALIZING --> AGGREGATOR
    ```
    *<p align="center">EMS server log: New Aggregator queries for node's credentials</p>*
    ```
    e.m.e.b.server.ClientShellCommand        : #00004==> PUSH : {"random":"4abf9ae2-b7fc-4e8c-b6d9-464623d1b05f","zone-id":"IMU-ZONE","address":"192.168.16.4",.........................
    ```
    *<p align="center">EMS server log: New Aggregator give up message</p>*
    ```
    e.m.e.b.server.ClientShellCommand        : #00004--> Client notification: CMD=RECOVERY, ARGS=GIVE_UP node_3866738cb0f4_2002 @ 192.168.16.4
    e.m.e.b.server.ClientShellCommand        : #00004--> Client Recovery Notification: GIVE_UP: node_3866738cb0f4_2002 @ 192.168.16.4
    ```
    Note: Aggregator disconnection from EMS server will also be logged in EMS server logs, but no recovery action will be taken by EMS server.

  * ***New Aggregator***, for messages reporting, (i) an EMS client left cluster, (ii) being elected as Aggregator, (iii) a number of failed connection attempts to the VM, and (iv) a recovery give up message.
    *<p align="center">New Aggregator log: Old Aggregator left cluster - New Aggregator election</p>*
    ```
    CLM: MEMBER_REMOVED: node=node_3866738cb0f4_2002
    BRU: Brokers after cluster change: []
    BRU: Broker election requested: broadcasting election message...
    BRU: **** Broker message received: election
    BRU: **** BROKER: Starting Broker election:
    BRU: Member-Score: node_581d745be52c_2001 => 0.6640625  e974ebcd-e11e-4baa-b3cb-fa34242705ff
    BRU: Broker: node_581d745be52c_2001
    ```
    *<p align="center">New Aggregator log: Initializing to become the new Aggregator</p>*
    ```
    CLM: MEMBER_REMOVED: node=node_3866738cb0f4_2002
    BRU: Brokers after cluster change: []
    BRU: Broker election requested: broadcasting election message...
    BRU: **** Broker message received: election
    BRU: **** BROKER: Starting Broker election:
    BRU: Member-Score: node_581d745be52c_2001 => 0.6640625  e974ebcd-e11e-4baa-b3cb-fa34242705ff
    BRU: Broker: node_581d745be52c_2001
    
    BRU: Node will become Broker. Initializing...
    2022-02-16 12:01:34.448 [INFO ] NOTIFY-STATUS-CHANGE: INITIALIZING
    initialize(): Node starts initializing as Aggregator...
    .........................
    .........................
    Notifying Baguette Server i am the new aggregator
    .........................
    .........................
    BRU: Node is ready to act as Aggregator. Ready
    BRU: **** Broker message received: ready node_581d745be52c_2001 New config: eyJncm91cGluZyI6IlBFUl9aT05FIiwidXJsIjoic3NsOi8vMTkyLjE2OC4xNi4zOjYxNjE3P2RhZW1vbj10cn.........................
    BRU: **** BROKER: New Broker is ready: node_581d745be52c_2001, New config: eyJncm91cGluZyI6IlBFUl9aT05FIiwidXJsIjoic3NsOi8vMTkyLjE2OC4xNi4zOjYxNjE3P2RhZW1vbj10cn.........................
    BRU: Node configuration updated: eyJncm91cGluZyI6IlBFUl9aT05FIiwidXJsIjoic3NsOi8vMTkyLjE2OC4xNi4zOjYxNjE3P2RhZW1vbj10cn.........................
    ```
    *<p align="center">New Aggregator log: Requesting old Aggregator node's credentials</p>*
    ```
    SEND: SERVER-GET-NODE-SSH-CREDENTIALS 192.168.16.4
    SelfHealingPlugin: createRecoveryTask(): Created recovery task for Node: id=node_3866738cb0f4_2002, address=192.168.16.4
    ```
    *<p align="center">New Aggregator log: Failing recovery actions of old Aggregator</p>*
    ```
    SelfHealingPlugin: Retry #0: Recovering node: id=node_3866738cb0f4_2002, address=192.168.16.4
    VmNodeRecoveryTask: connectToNode(): Connecting to node using SSH: address=192.168.16.4, port=22, username=ubuntu
    Connecting to server...
    SelfHealingPlugin: EXCEPTION while recovering node: node-address=192.168.16.4 -- Exception:
    java.net.NoRouteToHostException: No route to host
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.checkConnect(Native Method)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finishConnect(UnixAsynchronousSocketChannelImpl.java:252)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finish(UnixAsynchronousSocketChannelImpl.java:198)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.onEvent(UnixAsynchronousSocketChannelImpl.java:213)
            at sun.nio.ch.EPollPort$EventHandlerTask.run(EPollPort.java:293)
            at java.lang.Thread.run(Thread.java:748)
    .........................
    .........................
    SelfHealingPlugin: Retry #3: Recovering node: id=node_3866738cb0f4_2002, address=192.168.16.4
    VmNodeRecoveryTask: connectToNode(): Connecting to node using SSH: address=192.168.16.4, port=22, username=ubuntu
    Connecting to server...
    SelfHealingPlugin: EXCEPTION while recovering node: node-address=192.168.16.4 -- Exception:
    java.net.NoRouteToHostException: No route to host
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.checkConnect(Native Method)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finishConnect(UnixAsynchronousSocketChannelImpl.java:252)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finish(UnixAsynchronousSocketChannelImpl.java:198)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.onEvent(UnixAsynchronousSocketChannelImpl.java:213)
            at sun.nio.ch.EPollPort$EventHandlerTask.run(EPollPort.java:293)
            at java.lang.Thread.run(Thread.java:748)
    ```
    *<p align="center">New Aggregator log: Recovery actions Give Up message</p>*
    ```
    SelfHealingPlugin: Max retries reached. No more recovery retries for node: id=node_3866738cb0f4_2002, address=192.168.16.4
    SelfHealingPlugin: cancelRecoveryTask(): Cancelled recovery task for Node: id=node_3866738cb0f4_2002, address=192.168.16.4
    NOTIFY-X: RECOVERY GIVE_UP node_3866738cb0f4_2002 @ 192.168.16.4
    ```
  * ***Normal nodes that operate***, for log messages about, (i) EMS client leaving cluster, (ii) Aggregator election, but NO logs about recovery actions, or EMS client joining back to cluster.



**B.6.a)  Successful recovery of Netdata agent in a clustered RL node**

> Test Case Quick Notes:
> - Kill Netdata agent of any RL node.
> - The Aggregator will recover the killed Netdata agent after a configured period of time.
> - Check Aggregator log messages reporting failures to collect metrics, recovery actions, and successful metrics collection.

**After Application deployment...**

  * Connect to a RL node and ***kill*** Netdata agent.

**Next, check the logs of:**

  * ***EMS server***, for NO logs indicating a Netdata failure and recovery.
    *<p align="center">EMS server log: Aggregator queries for RL node's credentials</p>*
    ```
    e.m.e.b.server.ClientShellCommand        : #00000==> PUSH : {"random":"4b676a58-e00e-4ddf-a21e-b1c0d1382cd6","zone-id":"IMU-ZONE","address":"192.168.96.2","provider":"AWS",.........................
    ```
  * ***Aggregator***, for logs reporting, (i) connection failures to a Netdata agent, (ii) recovery actions, and (iii) successful connection to Netdata agent and collection of metrics.
    *<p align="center">Aggregator log: Failed metric collection attempts from a RL node's Netdata agent</p>*
    ```
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
    Collectors::Netdata: Collecting metrics from remote nodes (without EMS client): [192.168.96.2]
    Collectors::Netdata:   Collecting data from url: http://192.168.96.2:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Exception while collecting metrics from node: 192.168.96.2, #errors=1, exception: org.springframework.web.client.ResourceAccessException: I/O error on GET request for "http://192.168.96.2:19999/api/v1/allmetrics": Connection refused (Connection refused); nested exception is java.net.ConnectException: Connection refused (Connection refused) -> java.net.ConnectException: Connection refused (Connection refused)
    
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
    Collectors::Netdata: Collecting metrics from remote nodes (without EMS client): [192.168.96.2]
    Collectors::Netdata:   Collecting data from url: http://192.168.96.2:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Exception while collecting metrics from node: 192.168.96.2, #errors=2, exception: org.springframework.web.client.ResourceAccessException: I/O error on GET request for "http://192.168.96.2:19999/api/v1/allmetrics": Connection refused (Connection refused); nested exception is java.net.ConnectException: Connection refused (Connection refused) -> java.net.ConnectException: Connection refused (Connection refused)
    
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
    Collectors::Netdata: Collecting metrics from remote nodes (without EMS client): [192.168.96.2]
    Collectors::Netdata:   Collecting data from url: http://192.168.96.2:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Exception while collecting metrics from node: 192.168.96.2, #errors=3, exception: org.springframework.web.client.ResourceAccessException: I/O error on GET request for "http://192.168.96.2:19999/api/v1/allmetrics": Connection refused (Connection refused); nested exception is java.net.ConnectException: Connection refused (Connection refused) -> java.net.ConnectException: Connection refused (Connection refused)
    Collectors::Netdata: Too many consecutive errors occurred while attempting to collect metrics from node: 192.168.96.2, num-of-errors=3
    Collectors::Netdata: Pausing collection from Node: 192.168.96.2
    ```
    *<p align="center">Aggregator log: Requesting RL node's credentials</p>*
    ```
    SEND: SERVER-GET-NODE-SSH-CREDENTIALS 192.168.96.2
    SelfHealingPlugin: createRecoveryTask(): Created recovery task for Node: id=null, address=192.168.96.2
    ```
    *<p align="center">Aggregator log: Netdata agent recovery actions</p>*
    ```
    SelfHealingPlugin: Retry #0: Recovering node: id=null, address=192.168.96.2
    VmNodeRecoveryTask: connectToNode(): Connecting to node using SSH: address=192.168.96.2, port=22, username=ubuntu
    Connecting to server...
    SSH client is ready
    VmNodeRecoveryTask: runNodeRecovery(): Executing 3 recovery commands
    ##############  Initial wait......
    ##############  Waiting for 5000ms after Initial wait......
    ##############  Sending Netdata agent kill command......
    ##############  Waiting for 2000ms after Sending Netdata agent kill command......
    ##############  Sending Netdata agent start command......
    ##############  Waiting for 10000ms after Sending Netdata agent start command......
    VmNodeRecoveryTask: runNodeRecovery(): Executed 3 recovery commands
    VmNodeRecoveryTask: disconnectFromNode(): Disconnecting from node: address=192.168.96.2, port=22, username=ubuntu
    Stopping SSH client...
    SSH client stopped
    Collectors::Netdata: Resuming collection from Node: 192.168.96.2
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
    Collectors::Netdata: Collecting metrics from remote nodes (without EMS client): [192.168.96.2]
    Collectors::Netdata:   Collecting data from url: http://192.168.96.2:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
    SelfHealingPlugin: cancelRecoveryTask(): Cancelled recovery task for Node: id=null, address=192.168.96.2
     OUT> Last login: Sat Feb 12 10:40:09 2022 from 172.29.0.4
     OUT>
     OUT> pwd
     OUT> ubuntu@ec17d3e87fb4:~$ pwd
     OUT> /home/ubuntu
     OUT> ubuntu@ec17d3e87fb4:~$
     OUT> < -U netdata -o "pid" --no-headers | xargs kill -9'
     OUT>
     OUT> Usage:
     OUT>  kill [options] <pid> [...]
     OUT>
     OUT> Options:
     OUT>  <pid> [...]            send signal to every <pid> listed
     OUT>  -<signal>, -s, --signal <signal>
     OUT>                         specify the <signal> to be sent
     OUT>  -l, --list=[<signal>]  list all signal names, or convert one to a name
     OUT>  -L, --table            list all signal names in a nice table
     OUT>
     OUT>  -h, --help     display this help and exit
     OUT>  -V, --version  output version information and exit
     OUT>
     OUT> For more details see kill(1).
     OUT> ubuntu@ec17d3e87fb4:~$ sudo netdata
     OUT> 2022-02-16 12:27:55: netdata INFO  : MAIN : CONFIG: cannot load cloud config '/var/lib/netdata/cloud.d/cloud.conf'. Running with internal defaults.
    VmNodeRecoveryTask: redirectSshOutput(): Connection closed: id=OUT
    ```
    *<p align="center">Aggregator log: Successful metrics collection from RL node's Netdata agent</p>*
    ```
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
    Collectors::Netdata: Collecting metrics from remote nodes (without EMS client): [192.168.96.2]
    Collectors::Netdata:   Collecting data from url: http://192.168.96.2:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
    ```
  * ***RL node with killed Netdata***, check if the Netdata processes have started again.
    *<p align="center">RL node shell: Recovered Netdata agent process</p>*
    ```sh
    # ps -ef |grep netdata
    root       610    29  0 12:27 pts/0    00:00:00 grep --color=auto netd
    .........................
    .........................
    # ps -ef |grep netdata
    netdata    623     1  5 12:27 ?        00:00:51 netdata
    netdata    625   623  0 12:27 ?        00:00:02 /usr/sbin/netdata --special-spawn-server
    root       894   623  0 12:28 ?        00:00:05 /usr/libexec/netdata/plugins.d/apps.plugin 1
    netdata   1050   623  0 12:28 ?        00:00:04 /usr/libexec/netdata/plugins.d/go.d.plugin 1
    root      1105    29  0 12:45 pts/0    00:00:00 grep --color=auto netd
    ```
  * ***Normal nodes (that operate)***, for NO logs indicating connection failures or recovery action.



**B.6.b)  Failed recovery of Netdata agent in a clustered RL node**

> Test Case Quick Notes:
> - Kill the VM of any RL node.
> - The EMS server will try to connect to the affected VM but fail.
> - After a configured number of retries EMS server will give up.

**After Application deployment...**

  * Terminate the VM of a RL node

**You need to check the logs of:**

  * ***EMS server***, for NO logs indicating a Netdata failure and recovery, BUT reporting a recovery give up from Aggregator.
    *<p align="center">EMS server log: Aggregator queries for RL node's credentials</p>*
    ```
    e.m.e.b.server.ClientShellCommand        : #00000==> PUSH : {"random":"4b676a58-e00e-4ddf-a21e-b1c0d1382cd6","zone-id":"IMU-ZONE","address":"192.168.96.2","provider":"AWS",.........................
    ```
    *<p align="center">EMS server log: Aggregator give up message</p>*
    ```
    e.m.e.b.server.ClientShellCommand        : #00000--> Client notification: CMD=RECOVERY, ARGS=GIVE_UP null @ 192.168.96.2
    e.m.e.b.server.ClientShellCommand        : #00000--> Client Recovery Notification: GIVE_UP: null @ 192.168.96.2
    e.m.e.baguette.server.BaguetteServer     : BaguetteServer.onMessage: Marked Node as Failed: 192.168.96.2
    ```
  * ***Aggregator***, for logs reporting (i) connection failures to a Netdata agent, (ii) a number of failed attempts to connect to VM, and (iii) a recovery give up message.
    *<p align="center">Aggregator log: Failed metric collection attempts from a RL node's Netdata agent</p>*
    ```
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
    Collectors::Netdata: Collecting metrics from remote nodes (without EMS client): [192.168.96.2]
    Collectors::Netdata:   Collecting data from url: http://192.168.96.2:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Exception while collecting metrics from node: 192.168.96.2, #errors=1, exception: org.springframework.web.client.ResourceAccessException: I/O error on GET request for "http://192.168.96.2:19999/api/v1/allmetrics": connect timed out; nested exception is java.net.SocketTimeoutException: connect timed out -> java.net.SocketTimeoutException: connect timed out
    
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
    Collectors::Netdata: Collecting metrics from remote nodes (without EMS client): [192.168.96.2]
    Collectors::Netdata:   Collecting data from url: http://192.168.96.2:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Exception while collecting metrics from node: 192.168.96.2, #errors=2, exception: org.springframework.web.client.ResourceAccessException: I/O error on GET request for "http://192.168.96.2:19999/api/v1/allmetrics": connect timed out; nested exception is java.net.SocketTimeoutException: connect timed out -> java.net.SocketTimeoutException: connect timed out
    
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
    Collectors::Netdata: Collecting metrics from remote nodes (without EMS client): [192.168.96.2]
    Collectors::Netdata:   Collecting data from url: http://192.168.96.2:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Exception while collecting metrics from node: 192.168.96.2, #errors=3, exception: org.springframework.web.client.ResourceAccessException: I/O error on GET request for "http://192.168.96.2:19999/api/v1/allmetrics": connect timed out; nested exception is java.net.SocketTimeoutException: connect timed out -> java.net.SocketTimeoutException: connect timed out
    Collectors::Netdata: Too many consecutive errors occurred while attempting to collect metrics from node: 192.168.96.2, num-of-errors=3
    Collectors::Netdata: Pausing collection from Node: 192.168.96.2
    ```
    *<p align="center">Aggregator log: Requesting RL node's credentials</p>*
    ```
    SEND: SERVER-GET-NODE-SSH-CREDENTIALS 192.168.96.2
    SelfHealingPlugin: createRecoveryTask(): Created recovery task for Node: id=null, address=192.168.96.2
    ```
    *<p align="center">Aggregator log: Netdata agent (failing) recovery actions</p>*
    ```
    SelfHealingPlugin: Retry #0: Recovering node: id=null, address=192.168.96.2
    VmNodeRecoveryTask: connectToNode(): Connecting to node using SSH: address=192.168.96.2, port=22, username=ubuntu
    Connecting to server...
    SelfHealingPlugin: EXCEPTION while recovering node: node-address=192.168.96.2 -- Exception:
    java.net.NoRouteToHostException: No route to host
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.checkConnect(Native Method)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finishConnect(UnixAsynchronousSocketChannelImpl.java:252)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finish(UnixAsynchronousSocketChannelImpl.java:198)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.onEvent(UnixAsynchronousSocketChannelImpl.java:213)
            at sun.nio.ch.EPollPort$EventHandlerTask.run(EPollPort.java:293)
            at java.lang.Thread.run(Thread.java:748)
    
    Collecting metrics from local node...
      Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
        Metrics: extracted=0, published=0, failed=0
    Collecting metrics from remote nodes (without EMS client): [192.168.96.2]
      Node is in ignore list: 192.168.96.2
    .........................
    .........................
    SelfHealingPlugin: Retry #3: Recovering node: id=null, address=192.168.96.2
    VmNodeRecoveryTask: connectToNode(): Connecting to node using SSH: address=192.168.96.2, port=22, username=ubuntu
    Connecting to server...
    SelfHealingPlugin: EXCEPTION while recovering node: node-address=192.168.96.2 -- Exception:
    java.net.NoRouteToHostException: No route to host
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.checkConnect(Native Method)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finishConnect(UnixAsynchronousSocketChannelImpl.java:252)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.finish(UnixAsynchronousSocketChannelImpl.java:198)
            at sun.nio.ch.UnixAsynchronousSocketChannelImpl.onEvent(UnixAsynchronousSocketChannelImpl.java:213)
            at sun.nio.ch.EPollPort$EventHandlerTask.run(EPollPort.java:293)
            at java.lang.Thread.run(Thread.java:748)
    ```
    *<p align="center">Aggregator log: Netdata agent recovery Give Up message</p>*
    ```
    SelfHealingPlugin: Max retries reached. No more recovery retries for node: id=null, address=192.168.96.2
    SelfHealingPlugin: cancelRecoveryTask(): Cancelled recovery task for Node: id=null, address=192.168.96.2
    Collectors::Netdata: Giving up collection from Node: 192.168.96.2
    NOTIFY-X: RECOVERY GIVE_UP null @ 192.168.96.2
    ```
  * ***Normal nodes (that operate)***, for NO logs indicating connection failures or recovery actions.



**B.7)  Successful recovery of local Netdata agent, in a clustered Normal node (including Aggregator)**

> Test Case Quick Notes:
> - Kill Netdata agent of any Normal node.
> - The EMS client of the affected node will recover the killed Netdata agent after a configured period of time.
> - Check EMS client's log for messages reporting failures to collect metrics, recovery actions, and successful metrics collection.

**After Application deployment...**

  * Connect to a Normal node and ***kill*** Netdata agent.

**Next, check the logs of:**

  * ***EMS server***, for No log messages indicating connection failures to a Netdata agent or recovery actions.
  * ***Aggregator***, for No log messages indicating connection failures to a Netdata agent or recovery actions.
  * ***Normal node with killed Netdata***, check if the Netdata processes have started again. Also check EMS client's log messages reporting failed metric collection attempts, recovery actions, and successful metric collection.
    *<p align="center">Normal node - EMS client log: Failed attempts to collect metrics from <u><b>Local</b></u> Netdata agent</p>*
    ```
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Exception while collecting metrics from node: , #errors=1, exception: org.springframework.web.client.ResourceAccessException: I/O error on GET request for "http://127.0.0.1:19999/api/v1/allmetrics": Connection refused (Connection refused); nested exception is java.net.ConnectException: Connection refused (Connection refused) -> java.net.ConnectException: Connection refused (Connection refused)
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Exception while collecting metrics from node: , #errors=2, exception: org.springframework.web.client.ResourceAccessException: I/O error on GET request for "http://127.0.0.1:19999/api/v1/allmetrics": Connection refused (Connection refused); nested exception is java.net.ConnectException: Connection refused (Connection refused) -> java.net.ConnectException: Connection refused (Connection refused)
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Exception while collecting metrics from node: , #errors=3, exception: org.springframework.web.client.ResourceAccessException: I/O error on GET request for "http://127.0.0.1:19999/api/v1/allmetrics": Connection refused (Connection refused); nested exception is java.net.ConnectException: Connection refused (Connection refused) -> java.net.ConnectException: Connection refused (Connection refused)
    Collectors::Netdata: Too many consecutive errors occurred while attempting to collect metrics from node: , num-of-errors=3
    Collectors::Netdata: Will pause metrics collection from node for 60 seconds:
    SelfHealingPlugin: createRecoveryTask(): Created recovery task for Node: id=null, address=
    ```
    *<p align="center">Normal node - EMS client log: <u><b>Local</b></u> Netdata agent recovery actions</p>*
    ```
    SelfHealingPlugin: Retry #0: Recovering node: id=null, address=
    ShellRecoveryTask: runNodeRecovery(): Executing 3 recovery commands
    ##############  Initial wait......
    ##############  Waiting for 5000ms after Initial wait......
    ##############  Sending Netdata agent kill command......
    ##############  Waiting for 2000ms after Sending Netdata agent kill command......
    ##############  Sending Netdata agent start command......
    ##############  Waiting for 10000ms after Sending Netdata agent start command......
    ShellRecoveryTask: runNodeRecovery(): Executed 3 recovery commands
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Node is in ignore list:
     OUT> /opt/baguette-client
     ERR> -U: 1: -U: Syntax error: Unterminated quoted string
     ERR> 2022-02-16 13:21:52: netdata INFO  : MAIN : CONFIG: cannot load cloud config '/var/lib/netdata/cloud.d/cloud.conf'. Running with internal defaults.
    ```
    *<p align="center">Normal node - EMS client log: Successful metrics collection from <u><b>Local</b></u> Netdata agent</p>*
    ```
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Node is in ignore list:
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Node is in ignore list:
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Node is in ignore list:
    
    Collectors::Netdata: Resumed metrics collection from node:
    SelfHealingPlugin: cancelRecoveryTask(): Cancelled recovery task for Node: id=null, address=
    
    Collectors::Netdata: Collecting metrics from local node...
    Collectors::Netdata:   Collecting data from url: http://127.0.0.1:19999/api/v1/allmetrics?format=json
    Collectors::Netdata:     Metrics: extracted=0, published=0, failed=0
    ```
  * ***Other Normal nodes (that operate)***, for NO logs indicating connection failures or recovery actions.



------

## Limitations

* Clustering is never used for 2-level monitoring topologies.
* When no Normal nodes (and hence no Aggregator) exist in a cluster, no one will collect metrics from the (orphan) RL nodes.
* When no Normal nodes (and hence no Aggregator) exist in a cluster, no one will recover the (orphan) RL nodes.
* If EMS server fails no one will recover it.
* Metric messages are not cached/redirected, if the next node has failed.
