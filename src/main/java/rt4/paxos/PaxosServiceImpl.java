package rt4.paxos;

import io.grpc.stub.StreamObserver;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PaxosServiceImpl extends PaxosServiceGrpc.PaxosServiceImplBase {

    // Chaque serveur génère une valeur aléatoire au démarrage
    private int localValue ;

    // Liste pour stocker les valeurs validées (R)
    private List<Integer> acceptedValues = new ArrayList<>();

    // RPC appelé par le client pour obtenir la valeur aléatoire de ce serveur
    @Override
    public void proposeValue(ValueRequest request, StreamObserver<ValueResponse> responseObserver) {
        //à chaque demande générer une valeur
        localValue = new Random().nextInt(100);
        ValueResponse response = ValueResponse.newBuilder()
                .setProposedValue(localValue)
                .setServerId("S" + PaxosProposer.PORT) // identifiant serveur
                .build();



        System.out.println("Server ID : S"+ PaxosProposer.PORT +" - Proposed Value :"+ localValue);
        responseObserver.onNext(response); // envoi de la réponse
        responseObserver.onCompleted();    // fin de la communication
    }

    // RPC appelé par le client pour stocker la valeur consensus R sur ce serveur
    @Override
    public void storeConsensusValue(ConsensusValue request, StreamObserver<StoreAck> responseObserver) {
        acceptedValues.add(request.getValue()); // stockage de R
        StoreAck ack = StoreAck.newBuilder().setSuccess(true).build();
        responseObserver.onNext(ack);   // envoi de l'accusé de réception
        responseObserver.onCompleted(); // fin de la communication
        getAcceptedValues();//afficher les valeurs acceptées par le Leader
    }

    // Getter utile pour test ou affichage
    public List<Integer> getAcceptedValues() {
        System.out.println("---> List of Accepted Values in ServerID : S"+ PaxosProposer.PORT);
        for(int i=0; i<acceptedValues.size(); i++){
            System.out.println(acceptedValues.get(i)+" ");
        }
        return acceptedValues;
    }
}
