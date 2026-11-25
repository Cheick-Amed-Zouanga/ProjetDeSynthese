package donnees;
/**
 * on defini les different etat d'une transaction 
 */
public enum EtatTransaction {
   RECUE,//une transaction est recue quand elle est enregistre pas encore traite 
   APPROUVEE,//quand letraitement est effictif il est approuve
   REFUSEE,//quand le trantement a ete traite mais refuse 
   ECHOUEE,//qua,d il y a un echec de traitement 
}
