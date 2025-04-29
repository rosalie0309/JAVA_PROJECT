#!/bin/bash

# Nettoyer ancien résultat
rm -f resultats.txt

# Nombre de répétitions
for i in {1..10}
do
    echo "[INFO] Lancement du test numéro $i" | tee -a resultats.txt
    java Test --clients=5 --DC=2 --file=FileA.txt | tee -a resultats.txt
    echo "[INFO] Fin du test numéro $i" | tee -a resultats.txt
    echo "--------------------" | tee -a resultats.txt
    sleep 2  # Petite pause pour être réaliste
done
