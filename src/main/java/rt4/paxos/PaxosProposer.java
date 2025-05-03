package rt4.paxos;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class PaxosProposer {
    public static int PORT;

    public static void main(String[] args) throws IOException, InterruptedException {
        // Récupération du port passé en argument (ex: 50051, 50052, etc.)
        PORT = Integer.parseInt(args[0]);

        // Lancement du serveur gRPC avec notre service Paxos
        Server server = ServerBuilder.forPort(PORT)
                .addService(new PaxosServiceImpl())
                .build()
                .start();

        System.out.println("Server started on port " + PORT);

        server.awaitTermination(); // Attente infinie (le serveur reste actif)
    }
}
