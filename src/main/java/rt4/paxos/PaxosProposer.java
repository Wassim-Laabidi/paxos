package rt4.paxos;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;


public class PaxosProposer {
    private static final Logger logger = Logger.getLogger(PaxosProposer.class.getName());

    public static int PORT;
    private Server server;

     //start the gRPC server
    public static void main(String[] args) throws IOException, InterruptedException {
        // Get port from command line arguments
        if (args.length < 1) {
            System.err.println("Usage: PaxosProposer <port>");
            System.exit(1);
        }

        // Parse port
        PORT = Integer.parseInt(args[0]);

        // Create and start server
        final PaxosProposer proposer = new PaxosProposer();
        proposer.start();
        proposer.blockUntilShutdown();
    }

     // Start the server with PaxosServiceImpl

    private void start() throws IOException {
        // Create service implementation
        PaxosServiceImpl serviceImpl = new PaxosServiceImpl();

        // Build and start server
        server = ServerBuilder.forPort(PORT)
                .addService(serviceImpl)
                .build()
                .start();

        logger.info("Server started on port " + PORT);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down server due to JVM shutdown");
            try {
                PaxosProposer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
        }));
    }

     // Stop the server

    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }


    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
}