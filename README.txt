Projet Java Serveur Multi Thread pour le téléchargement de fichiers
Groupe : Corine Tomeyum, Justin Mouaromba, Mamoudou ..., Noël Mariaratnam

Pour compiler le projet, il faut utiliser la commande suivante : javac *.java
Pour exécuter Test, il faut utiliser la commande suivante : java Test --clients=<nb_clients> --DC=<nb_blocs> --P=<probabilite déconnexion> --file=<idfile> 
Pour lancer un serveur : 
- sans argument, il faut utiliser la commande suivante : java Server
- avec la probabilité de déconnexion, il faut utiliser la commande suivante : java Server --P=<probabilite déconnexion>
Pour lancer un client :
- sans argument, il faut utiliser la commande suivante : java Client
- avec l'index du fichier à télécharger et/ou le nombre concurrent de téléchargements de bloc, il faut utiliser la commande suivante : java Client --file=<index> --DC=<nb_blocs>

