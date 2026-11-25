package donnees;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
/**
 *represente un paiement fictif on ne peut pas le modifier chaque changement cree donc une copie
 *on a donc fait le choix d'un record   
 */
public record Transaction(
		String id,//id de la transaction obligatoire
		String reference,//reference de la transaction obligatoire
		BigDecimal montant,//montant de la transaction obligatoire
		String devise,//devise de la transaction obligatoire
		EtatTransaction etat,//letat de la transaction obligatoire
		String motif,//motif de la transaction facultatif 
		Instant creeLe,//date de creation de la transaction obligatoire
		Instant termineLe,//date de fin de la transaction
		Double tempsAttenteFileMs,//temps d'attente dans une file d'une transaction 
		Double tempsTraitementMs//temps de traitement d'une transaction 
		) {
	public Transaction {
        Objects.requireNonNull(id, "L'id est obligatoire");
        Objects.requireNonNull(reference, "La référence est obligatoire");
        Objects.requireNonNull(montant, "Le montant est obligatoire");
        Objects.requireNonNull(devise, "La devise est obligatoire");
        Objects.requireNonNull(etat, "L'état est obligatoire");
        Objects.requireNonNull(creeLe, "La date de création est obligatoire");

        if (etat == EtatTransaction.RECUE && termineLe != null) {
            throw new IllegalArgumentException("termineLe doit être null tant que l'état est  RECUE");
        }
   
    }
	public Transaction terminer(
	        EtatTransaction etat,
	        String motif,
	        Double tempsAttenteFileMs,
	        Double tempsTraitementMs) {

	    return new Transaction(
	        this.id,
	        this.reference,
	        this.montant,
	        this.devise,
	        etat,
	        motif,
	        this.creeLe,
	        Instant.now(),
	        tempsAttenteFileMs,
	        tempsTraitementMs
	    );
	}
}
