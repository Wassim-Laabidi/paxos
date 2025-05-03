
# TP Paxos avec gRPC et Maven

## Lancer les serveurs Paxos
Dans 3 terminaux séparés :


java rt4.paxos.PaxosProposer 50051 11
java rt4.paxos.PaxosProposer 50052 22
java rt4.paxos.PaxosProposer 50053 33

& "C:\Users\laabi\.jdks\corretto-22.0.2\bin\java.exe" -jar paxos-1.0-SNAPSHOT.jar 50051
& "C:\Users\laabi\.jdks\corretto-22.0.2\bin\java.exe" -jar paxos-1.0-SNAPSHOT.jar 50052
& "C:\Users\laabi\.jdks\corretto-22.0.2\bin\java.exe" -jar paxos-1.0-SNAPSHOT.jar 50053

Lancer le client :

java rt4.paxos.PaxosAcceptor

Valeur reçue du noeud sur port 50051: 11
Valeur reçue du noeud sur port 50052: 22
Valeur reçue du noeud sur port 50053: 33
>>> Résultat final : d = a + b + c = 66



cd c