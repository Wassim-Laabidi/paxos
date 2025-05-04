package rt4.paxos;

import io.grpc.stub.StreamObserver;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class PaxosServiceImpl extends PaxosServiceGrpc.PaxosServiceImplBase {
    private static final Logger logger = Logger.getLogger(PaxosServiceImpl.class.getName());

    // Server state
    private int highestSeenProposal = 0;
    private int acceptedProposalNumber = 0;
    private int currentValue = -1;
    private boolean isLeader = false;
    private final Random random = new Random();

    // Logging (custom entries for internal use)
    private final List<LocalLogEntry> eventLog = new CopyOnWriteArrayList<>();

    @Override
    public void proposeLeader(LeaderProposal request, StreamObserver<LeaderResponse> responseObserver) {
        int proposalNumber = request.getProposalNumber();
        String serverId = request.getServerId();

        logEvent("ELECTION", "Received leader proposal " + proposalNumber + " from " + serverId);

        boolean accepted = proposalNumber > highestSeenProposal;
        if (accepted) {
            highestSeenProposal = proposalNumber;
        }

        LeaderResponse response = LeaderResponse.newBuilder()
                .setAccepted(accepted)
                .setHighestSeen(highestSeenProposal)
                .setAcceptorId("S" + PaxosProposer.PORT)
                .build();

        logEvent("ELECTION", "Responded to leader proposal: " +
                (accepted ? "ACCEPTED" : "REJECTED") + " (highest=" + highestSeenProposal + ")");

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void proposeValue(ValueProposal request, StreamObserver<ValueResponse> responseObserver) {
        int proposalNumber = request.getProposalNumber();
        int proposedValue = request.getProposedValue();
        String leaderId = request.getLeaderId();

        logEvent("PROPOSAL", "Received value proposal " + proposedValue +
                " (prop #" + proposalNumber + ") from leader " + leaderId);

        boolean accepted = proposalNumber >= highestSeenProposal;

        if (accepted) {
            acceptedProposalNumber = proposalNumber;
            currentValue = proposedValue;
            logEvent("PROPOSAL", "Accepted value " + proposedValue);
        } else {
            logEvent("PROPOSAL", "Rejected value (proposal number too low)");
        }

        ValueResponse response = ValueResponse.newBuilder()
                .setAccepted(accepted)
                .setProposalNumber(acceptedProposalNumber)
                .setAcceptorId("S" + PaxosProposer.PORT)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void acknowledgeProposal(ProposalAck request, StreamObserver<AckResponse> responseObserver) {
        int proposalNumber = request.getProposalNumber();
        boolean accepted = request.getAccepted();
        String acceptorId = request.getAcceptorId();

        logEvent("ACK", "Received " + (accepted ? "positive" : "negative") +
                " acknowledgment for proposal " + proposalNumber + " from " + acceptorId);

        AckResponse response = AckResponse.newBuilder()
                .setReceived(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void commitValue(ConsensusValue request, StreamObserver<CommitAck> responseObserver) {
        int proposalNumber = request.getProposalNumber();
        int value = request.getValue();
        String leaderId = request.getLeaderId();

        logEvent("COMMIT", "Received commit for value " + value +
                " (prop #" + proposalNumber + ") from leader " + leaderId);

        currentValue = value;

        CommitAck ack = CommitAck.newBuilder()
                .setSuccess(true)
                .setServerId("S" + PaxosProposer.PORT)
                .build();

        logEvent("COMMIT", "Committed value " + value + " to local state");

        responseObserver.onNext(ack);
        responseObserver.onCompleted();
    }

    @Override
    public void getServerStatus(StatusRequest request, StreamObserver<ServerStatus> responseObserver) {
        if (currentValue == -1) {
            currentValue = random.nextInt(100);
        }

        ServerStatus.Builder statusBuilder = ServerStatus.newBuilder()
                .setServerId("S" + PaxosProposer.PORT)
                .setCurrentProposal(acceptedProposalNumber)
                .setIsLeader(isLeader)
                .setCurrentValue(currentValue);

        // Convert LocalLogEntry to protobuf LogEntry
        for (LocalLogEntry entry : eventLog) {
            rt4.paxos.LogEntry proto = rt4.paxos.LogEntry.newBuilder()
                    .setTimestamp(entry.getTimestamp())
                    .setEventType(entry.getEventType())
                    .setDescription(entry.getDescription())
                    .setProposalNumber(entry.getProposalNumber())
                    .setValue(entry.getValue())
                    .build();

            statusBuilder.addLogEntries(proto);

            if (statusBuilder.getLogEntriesCount() >= 20) break;
        }

        responseObserver.onNext(statusBuilder.build());
        responseObserver.onCompleted();
    }

    private void logEvent(String eventType, String description) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        LocalLogEntry logEntry = new LocalLogEntry(
                timestamp, eventType, description, acceptedProposalNumber, currentValue);
        eventLog.add(0, logEntry);

        while (eventLog.size() > 100) {
            eventLog.remove(eventLog.size() - 1);
        }

        logger.info("[" + eventType + "] " + description);
    }

    // ✅ Renamed to avoid conflict with Protobuf LogEntry
    static class LocalLogEntry {
        private final String timestamp;
        private final String eventType;
        private final String description;
        private final int proposalNumber;
        private final int value;

        public LocalLogEntry(String timestamp, String eventType, String description,
                             int proposalNumber, int value) {
            this.timestamp = timestamp;
            this.eventType = eventType;
            this.description = description;
            this.proposalNumber = proposalNumber;
            this.value = value;
        }

        public String getTimestamp() { return timestamp; }
        public String getEventType() { return eventType; }
        public String getDescription() { return description; }
        public int getProposalNumber() { return proposalNumber; }
        public int getValue() { return value; }
    }

    public void setAsLeader(boolean isLeader) {
        this.isLeader = isLeader;
        if (isLeader) {
            logEvent("LEADER", "This server is now the leader");
        }
    }
}
