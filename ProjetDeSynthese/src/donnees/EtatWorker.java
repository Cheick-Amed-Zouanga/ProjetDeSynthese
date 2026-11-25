package donnees;
/**
 * un worker traverse plusieurs etat au cours de sa vie 
 */
public enum EtatWorker {
  EN_DEMARRAGE, //le worker est sollicite et paye mais n'est pas encore utile et ne transporte pas de transactions
  ACTIF,//prend des transaction dans la file et les traite
  EN_VIDANGE,//termine sa transaction puis s'arrete il est quand mm toujours paye 
  ARRETE// n'existe plsu n'est plus sollicite c'est a ce moment qu'il n'est plus paye 
}
