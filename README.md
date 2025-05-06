# Optimized Paxos Consensus with Visualization

This project implements an optimized Paxos consensus algorithm with a graphical user interface to visualize the entire process in real-time. The implementation follows the three distinct phases of the Paxos protocol:

1. **Election Phase**: Leader selection among participating nodes
2. **Bill Phase**: Value proposal and acknowledgment collection
3. **Law Phase**: Final value commitment across all nodes

## Features

- Complete Paxos consensus protocol implementation
- Real-time visualization of the consensus process
- Detailed event logging for debugging and analysis
- Support for multiple nodes (configurable 3 or 5 nodes)
- Interactive GUI to start, monitor, and stop the consensus process
- gRPC-based communication between nodes

## System Requirements

- Java 22
- Maven (for building)
- Swing-compatible display for the GUI

## Project Structure

- `rt4.paxos` - Core Paxos implementation
    - `PaxosProposer` - Server implementation
    - `PaxosServiceImpl` - Paxos protocol logic
    - Protocol Buffers definitions
- `rt4.paxos.gui` - Visualization components
    - `PaxosVisualizer` - Main GUI and visualization interface
    - `PaxosController` - Controls the Paxos process from the GUI
- `PaxosLauncher` - Main entry point that starts both servers and GUI

## Building the Project

1. Make sure you have Maven and Java 22 installed
2. Build the project with Maven:

```bash
mvn clean package
```

This will create a JAR file with all dependencies included.

## Running the Application

You can run the application in two ways:

### Option 1: Using the Launcher (Recommended)

The launcher starts both the GUI and the required server processes:

```bash
java -cp target/paxos-1.0-SNAPSHOT-jar-with-dependencies.jar rt4.paxos.PaxosLauncher
```

### Option 2: Starting Components Separately

1. Start multiple server processes (at least 3):

```bash
java -cp target/paxos-1.0-SNAPSHOT.jar rt4.paxos.PaxosProposer 50051
java -cp target/paxos-1.0-SNAPSHOT.jar rt4.paxos.PaxosProposer 50052
java -cp target/paxos-1.0-SNAPSHOT.jar rt4.paxos.PaxosProposer 50053
java -cp target/paxos-1.0-SNAPSHOT.jar rt4.paxos.PaxosProposer 50054
java -cp target/paxos-1.0-SNAPSHOT.jar rt4.paxos.PaxosProposer 50055
```

2. Start the visualization interface:

```bash
java -cp target/paxos-1.0-SNAPSHOT-jar-with-dependencies.jar rt4.paxos.gui.PaxosVisualizer
```

## Using the Interface

1. When the application starts, you'll see the visualization interface
2. Choose the configuration from the dropdown (3 or 5 nodes)
3. Click "Start Paxos Process" to begin the consensus algorithm
4. Watch as the visualization shows:
    - Leader election process
    - Value proposals and acknowledgments
    - Final consensus commitment
5. The event log at the bottom shows detailed information about each step
6. You can stop the process at any time with the "Stop" button

## Understanding the Visualization

- **Node Colors**:
    - Grey: Inactive node
    - Blue: Leader node
    - Yellow: Proposer
    - Green: Acceptor
- **Messages**:
    - Red dots: Proposal messages
    - Green dots: Acknowledgment messages
- **Information Panels**:
    - Top: Current phase and consensus value
    - Bottom: Event log with timestamps

## Extending the Project

You can extend this project in several ways:

1. Add fault tolerance by handling node failures
2. Implement multi-round Paxos for better reliability
3. Add more complex value types (beyond integers)
4. Implement a persistent log for recovery

## Troubleshooting

- If servers fail to start, check if the ports are already in use
- If the GUI doesn't show node connections, restart the application
- Check the console output for any error messages

## Implementation Notes

This implementation focuses on visualizing the Paxos process rather than production-level reliability. In a real-world scenario, you would need to add:

1. Persistent storage for recovery
2. Better fault tolerance mechanisms
3. Timeouts and retries for message losses
4. Security features
