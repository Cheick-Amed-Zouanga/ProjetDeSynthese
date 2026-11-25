package Stockage;
/**
 * interface d'enregistrement de donne dans la base de donnee H2
 */

import java.util.Optional;

import donnees.Transaction;

public interface EnregistrementTransactionBd {
      void inserer(Transaction transaction);
      void mettreAJour(Transaction transaction);
      Optional<Transaction>  trouverParId(String id);
     
}
