package rt4.paxos;


import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;


import java.util.*;

public class PaxosAcceptor {
    public static void main(String[] args) throws Exception {

        // Liste des ports où les serveurs sont accessibles
        List<String> targetPorts = List.of("50051", "50052", "50053");

        // Dictionnaire pour stocker les valeurs proposées par chaque serveur
        Map<String, Integer> values = new HashMap<>();

        // Étape 1 : Demande à chaque serveur sa valeur aléatoire
        for (String port : targetPorts) {
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(port))
                    .usePlaintext() // pas de SSL
                    .build();

            PaxosServiceGrpc.PaxosServiceBlockingStub stub = PaxosServiceGrpc.newBlockingStub(channel);

            // Envoi de la requête et réception de la réponse
            ValueResponse response = stub.proposeValue(ValueRequest.newBuilder().setRequester("client").build());
            values.put(port, response.getProposedValue());

            channel.shutdown(); // fermeture du canal
        }

        // Étape 2 : Choix de la valeur maximale (R) comme valeur de consensus
        int consensusValue = Collections.max(values.values());

        System.out.println("Valeurs proposées : " + values);
        System.out.println("=> Valeur choisie par consensus : " + consensusValue);

        // Étape 3 : Envoi de cette valeur R à tous les serveurs pour qu'ils la stockent
        for (String port : targetPorts) {
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", Integer.parseInt(port))
                    .usePlaintext()
                    .build();

            PaxosServiceGrpc.PaxosServiceBlockingStub stub = PaxosServiceGrpc.newBlockingStub(channel);
            stub.storeConsensusValue(ConsensusValue.newBuilder().setValue(consensusValue).build());
            channel.shutdown();
        }

        System.out.println("=> La valeur de consensus a été envoyée à tous les serveurs.");
    }
}
