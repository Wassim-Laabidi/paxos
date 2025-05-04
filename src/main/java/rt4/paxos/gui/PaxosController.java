package rt4.paxos.gui;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import rt4.paxos.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller class that manages the Paxos process and communicates with the GUI
 */
public class PaxosController {
    private final PaxosVisualizer visualizer;
    private List<ManagedChannel> activeChannels = new ArrayList<>();
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Random random = new Random();
    private Map<String, Boolean> serverAvailability = new HashMap<>();

    public PaxosController(PaxosVisualizer visualizer) {
        this.visualizer = visualizer;
    }

    /**
     * Starts the Paxos consensus process with the specified servers
     */
    public void startPaxosProcess(List<String> targetPorts) {
        if (isRunning.getAndSet(true)) {
            return;  // Already running
        }

        try {
            activeChannels.clear();
            serverAvailability.clear();

            // First check which servers are available
            checkServerAvailability(targetPorts);

            // Filter out unavailable servers
            List<String> availablePorts = new ArrayList<>();
            for (String port : targetPorts) {
                if (serverAvailability.getOrDefault(port, false)) {
                    availablePorts.add(port);
                } else {
                    visualizer.addLogMessage("WARNING", "Server on port " + port + " is not available");
                }
            }

            if (availablePorts.isEmpty()) {
                visualizer.addLogMessage("ERROR", "No servers available. Make sure servers are started.");
                isRunning.set(false);
                return;
            }

            // Phase 1: Election
            runElectionPhase(availablePorts);

            if (!visualizer.isRunning()) return;
            sleepWithAnimation(1000);

            // Phase 2: Bill (Propose values and collect ACKs)
            runProposalPhase(availablePorts);

            if (!visualizer.isRunning()) return;
            sleepWithAnimation(1000);

            // Phase 3: Law (Commit consensus value)
            runCommitPhase(availablePorts);

            // Clean up
            cleanupChannels();
            isRunning.set(false);

        } catch (Exception e) {
            visualizer.addLogMessage("ERROR", "Process error: " + e.getMessage());
            e.printStackTrace();
            cleanupChannels();
            isRunning.set(false);
        }
    }

    /**
     * Verify which servers are actually available
     */
    private void checkServerAvailability(List<String> targetPorts) {
        for (String port : targetPorts) {
            try {
                ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(port))
                        .usePlaintext()
                        .build();

                PaxosServiceGrpc.PaxosServiceBlockingStub stub = PaxosServiceGrpc.newBlockingStub(channel);

                // Try with a short timeout
                stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                        .getServerStatus(StatusRequest.newBuilder().setRequester("gui").build());

                // If we reach here, server is available
                serverAvailability.put(port, true);
                activeChannels.add(channel);
                visualizer.addLogMessage("INFO", "Server on port " + port + " is available");

            } catch (Exception e) {
                serverAvailability.put(port, false);
                visualizer.addLogMessage("WARNING", "Server on port " + port + " is not responding");
            }
        }
    }

    /**
     * Phase 1: Leader Election
     */
    private void runElectionPhase(List<String> targetPorts) throws InterruptedException {
        visualizer.setPhase("1 - Election");
        visualizer.addLogMessage("PHASE", "Starting ELECTION phase");

        // Initialize leader election variables
        Map<String, Integer> proposalNumbers = new HashMap<>();
        String leaderId = null;
        int highestProposal = 0;

        // Each server proposes itself as a leader with a random proposal number
        for (String port : targetPorts) {
            // Generate a random proposal number for this server
            int proposalNum = random.nextInt(100) + 1;
            proposalNumbers.put(port, proposalNum);

            // Update node status in visualization
            visualizer.updateNodeStatus(port, false, "Proposer", proposalNum, -1);

            // Log the proposal
            visualizer.addLogMessage("PROPOSE_LEADER",
                    "Server " + port + " proposes itself as leader with proposal number " + proposalNum);

            // Track highest proposal
            if (proposalNum > highestProposal) {
                highestProposal = proposalNum;
                leaderId = port;
            }

            sleepWithAnimation(500);
        }

        // Simulate leader acceptance by other nodes
        if (leaderId != null) {
            for (String port : targetPorts) {
                if (!port.equals(leaderId)) {
                    // This server acknowledges the leader
                    boolean accepts = proposalNumbers.get(port) <= highestProposal;

                    // Visualize message passing
                    visualizer.addMessageAnimation(leaderId, port, "ELECT", accepts);
                    visualizer.addMessageAnimation(port, leaderId, "ACK", accepts);

                    visualizer.addLogMessage("LEADER_ACK",
                            "Server " + port + (accepts ? " accepts " : " rejects ") +
                                    leaderId + " as leader");

                    sleepWithAnimation(300);
                }
            }

            // Update leader status
            visualizer.updateNodeStatus(leaderId, true, "Leader", highestProposal, -1);
            visualizer.addLogMessage("LEADER_ELECTED",
                    "Server " + leaderId + " elected as leader with proposal " + highestProposal);

            // Try to communicate with actual server to set leader status
            try {
                ManagedChannel channel = getChannelForPort(leaderId);
                if (channel != null) {
                    PaxosServiceGrpc.PaxosServiceBlockingStub stub = PaxosServiceGrpc.newBlockingStub(channel);
                    stub.getServerStatus(StatusRequest.newBuilder().setRequester("gui").build());
                }
            } catch (Exception e) {
                // Ignore errors, this is just for visualization
            }
        } else {
            visualizer.addLogMessage("ERROR", "No leader could be elected");
        }
    }

    /**
     * Phase 2: Proposal (Bill)
     */
    private void runProposalPhase(List<String> targetPorts) throws InterruptedException {
        visualizer.setPhase("2 - Bill (Proposal)");
        visualizer.addLogMessage("PHASE", "Starting BILL phase (value proposal)");

        // Identify the leader
        String leaderId = null;
        for (String port : targetPorts) {
            try {
                ManagedChannel channel = getChannelForPort(port);
                if (channel == null) continue;

                PaxosServiceGrpc.PaxosServiceBlockingStub stub = PaxosServiceGrpc.newBlockingStub(channel);

                ServerStatus status = stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                        .getServerStatus(StatusRequest.newBuilder().setRequester("gui").build());

                if (status.getIsLeader()) {
                    leaderId = port;
                    break;
                }
            } catch (StatusRuntimeException e) {
                // Skip this server
                continue;
            }
        }

        // If no leader found from real servers, use the one from election phase
        if (leaderId == null) {
            // Find the node currently marked as leader in UI
            for (String port : targetPorts) {
                if (isNodeLeader(port)) {
                    leaderId = port;
                    visualizer.addLogMessage("INFO", "Using simulated leader " + leaderId + " for proposal phase");
                    break;
                }
            }
        }

        if (leaderId == null) {
            // Last resort - pick the first available server
            if (!targetPorts.isEmpty()) {
                leaderId = targetPorts.get(0);
                visualizer.updateNodeStatus(leaderId, true, "Leader", 0, -1);
                visualizer.addLogMessage("INFO", "Selecting " + leaderId + " as fallback leader");
            } else {
                visualizer.addLogMessage("ERROR", "No leader found for proposal phase");
                return;
            }
        }

        // Leader proposes a random value
        int proposedValue = random.nextInt(100);
        visualizer.updateNodeStatus(leaderId, true, "Leader", 0, proposedValue);
        visualizer.addLogMessage("PROPOSE_VALUE",
                "Leader " + leaderId + " proposes value: " + proposedValue);

        // Send proposal to all acceptors
        Map<String, Boolean> acceptances = new HashMap<>();
        for (String port : targetPorts) {
            if (!port.equals(leaderId)) {
                // Simulate acceptors receiving the proposal
                visualizer.addMessageAnimation(leaderId, port, "PROP:" + proposedValue, true);
                visualizer.updateNodeStatus(port, false, "Acceptor", 0, proposedValue);

                // Random acceptance (but mostly yes)
                boolean accepts = random.nextInt(10) < 9; // 90% chance of acceptance
                acceptances.put(port, accepts);

                sleepWithAnimation(300);

                // Acceptor sends ACK back to leader
                visualizer.addMessageAnimation(port, leaderId, "ACK", accepts);
                visualizer.addLogMessage("VALUE_ACK",
                        "Server " + port + (accepts ? " accepts" : " rejects") +
                                " value " + proposedValue);

                sleepWithAnimation(300);
            }
        }

        // Check if we have a majority of acceptances
        int accepted = 0;
        for (boolean accepts : acceptances.values()) {
            if (accepts) accepted++;
        }

        boolean majorityAccepted = accepted >= targetPorts.size() / 2;

        if (majorityAccepted) {
            visualizer.addLogMessage("PROPOSAL_SUCCESS",
                    "Value " + proposedValue + " accepted by majority (" + accepted + "/" + acceptances.size() + ")");
            visualizer.setConsensusValue(proposedValue);
        } else {
            visualizer.addLogMessage("PROPOSAL_FAILED",
                    "Value " + proposedValue + " rejected (only " + accepted + "/" + acceptances.size() + " accepted)");
            // In a real implementation, we would retry with a new proposal
        }
    }

    /**
     * Helper method to check if a node is currently marked as leader in the UI
     */
    private boolean isNodeLeader(String port) {
        try {
            Field nodesField = visualizer.getClass().getDeclaredField("nodes");
            nodesField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<String, PaxosVisualizer.ServerNode> nodes =
                    (Map<String, PaxosVisualizer.ServerNode>) nodesField.get(visualizer);

            PaxosVisualizer.ServerNode node = nodes.get(port);
            return node != null && node.isLeader;
        } catch (Exception e) {
            // Fallback if reflection fails
            return false;
        }
    }

    /**
     * Phase 3: Commit (Law)
     */
    private void runCommitPhase(List<String> targetPorts) throws InterruptedException {
        visualizer.setPhase("3 - Law (Commit)");
        visualizer.addLogMessage("PHASE", "Starting LAW phase (value commit)");

        // Find leader and consensus value
        String leaderId = null;
        int consensusValue = -1;

        // Try to find leader from server stats
        for (String port : targetPorts) {
            try {
                ManagedChannel channel = getChannelForPort(port);
                if (channel == null) continue;

                PaxosServiceGrpc.PaxosServiceBlockingStub stub = PaxosServiceGrpc.newBlockingStub(channel);
                ServerStatus status = stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                        .getServerStatus(StatusRequest.newBuilder().setRequester("gui").build());

                if (status.getIsLeader()) {
                    leaderId = port;
                    consensusValue = status.getCurrentValue();
                    break;
                }
            } catch (Exception e) {
                // Skip this server
                continue;
            }
        }

        // If no leader found, use visualization data
        if (leaderId == null) {
            // Try to find leader from UI
            for (String port : targetPorts) {
                if (isNodeLeader(port)) {
                    leaderId = port;

                    // Get the current value from field if possible
                    try {
                        Field consensusValueField = visualizer.getClass().getDeclaredField("consensusValue");
                        consensusValueField.setAccessible(true);
                        consensusValue = (int) consensusValueField.get(visualizer);
                    } catch (Exception e) {
                        // Fallback to random value if we can't get it
                        consensusValue = random.nextInt(100);
                    }

                    visualizer.addLogMessage("INFO", "Using simulated leader for commit phase");
                    break;
                }
            }
        }

        if (leaderId == null || consensusValue == -1) {
            // Last fallback - take the first server and a random value
            if (!targetPorts.isEmpty()) {
                leaderId = targetPorts.get(0);
                consensusValue = random.nextInt(100);
                visualizer.addLogMessage("INFO", "Using " + leaderId + " as fallback leader with value " + consensusValue);
            } else {
                visualizer.addLogMessage("ERROR", "No leader or consensus value found for commit phase");
                return;
            }
        }

        // Update UI with final consensus value if not already set
        visualizer.setConsensusValue(consensusValue);

        // Leader sends commit to all servers
        for (String port : targetPorts) {
            if (!port.equals(leaderId)) {
                // Visualize commit message
                visualizer.addMessageAnimation(leaderId, port, "COMMIT:" + consensusValue, true);
                visualizer.addLogMessage("COMMIT",
                        "Leader " + leaderId + " commits value " + consensusValue + " to server " + port);

                // Update acceptor with final value
                visualizer.updateNodeStatus(port, false, "Acceptor", 0, consensusValue);

                sleepWithAnimation(500);

                // Acceptor confirms
                visualizer.addMessageAnimation(port, leaderId, "COMMIT_ACK", true);
            }
        }

        visualizer.addLogMessage("CONSENSUS_REACHED",
                "Consensus reached with value: " + consensusValue);
        visualizer.setPhase("Complete");
    }

    /**
     * Helper method to get an existing channel for a port or null
     */
    private ManagedChannel getChannelForPort(String port) {
        for (ManagedChannel channel : activeChannels) {
            String authority = channel.authority();
            if (authority != null && authority.contains(port)) {
                return channel;
            }
        }

        // If no existing channel, try to create one for available servers
        if (serverAvailability.getOrDefault(port, false)) {
            try {
                ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(port))
                        .usePlaintext()
                        .build();
                activeChannels.add(channel);
                return channel;
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    /**
     * Sleep with animation to show process visually
     */
    private void sleepWithAnimation(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    /**
     * Stop the Paxos process
     */
    public void stopPaxosProcess() {
        isRunning.set(false);
        cleanupChannels();
    }

    /**
     * Clean up all gRPC channels
     */
    private void cleanupChannels() {
        for (ManagedChannel channel : activeChannels) {
            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        activeChannels.clear();
    }
}