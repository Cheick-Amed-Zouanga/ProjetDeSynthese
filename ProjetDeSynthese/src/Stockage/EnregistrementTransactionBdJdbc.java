
package Stockage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.util.Optional;

import donnees.Transaction;
import donnees.EtatTransaction;

/**
 * Gère les opérations de stockage des transactions dans H2.
 * Utilise JDBC pour communiquer avec la base de données.
 */
public class EnregistrementTransactionBdJdbc implements EnregistrementTransactionBd {

    // Enregistre une transaction dans la BD
    @Override
    public void inserer(Transaction t) {

        String sql = "INSERT INTO transaction_paiement "
                + "(id, reference, montant, devise, etat, motif, cree_le) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection connexion = ConnexionBD.connecter();
             PreparedStatement ps = connexion.prepareStatement(sql)) {

            // Remplace les ? par les valeurs de la transaction
            ps.setString(1, t.id());
            ps.setString(2, t.reference());
            ps.setBigDecimal(3, t.montant());
            ps.setString(4, t.devise());
            ps.setString(5, t.etat().name());
            ps.setString(6, t.motif());
            ps.setTimestamp(7, Timestamp.from(t.creeLe()));

            // Exécute l'insertion dans la table
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(
                "Erreur lors de l'insertion de la transaction", e
            );
        }
    }

    // Met à jour une transaction après son traitement
    @Override
    public void mettreAJour(Transaction t) {

        String sql = "UPDATE transaction_paiement "
                + "SET etat = ?, motif = ?, termine_le = ?, "
                + "temps_attente_file_ms = ?, temps_traitement_ms = ? "
                + "WHERE id = ?";

        try (Connection connexion = ConnexionBD.connecter();
             PreparedStatement ps = connexion.prepareStatement(sql)) {

            // Prépare les nouvelles valeurs
            ps.setString(1, t.etat().name());
            ps.setString(2, t.motif());

            // Date de fin, si elle existe
            ps.setTimestamp(3, t.termineLe() == null
                    ? null : Timestamp.from(t.termineLe()));

            ps.setObject(4, t.tempsAttenteFileMs());
            ps.setObject(5, t.tempsTraitementMs());

            // Identifie la transaction à modifier
            ps.setString(6, t.id());

            // Exécute la mise à jour
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(
                "Erreur lors de la mise à jour de la transaction", e
            );
        }
    }

    // Recherche une transaction par son identifiant
    @Override
    public Optional<Transaction> trouverParId(String id) {

        String sql = "SELECT * FROM transaction_paiement WHERE id = ?";

        try (Connection connexion = ConnexionBD.connecter();
             PreparedStatement ps = connexion.prepareStatement(sql)) {

            // Recherche la transaction par son identifiant
            ps.setString(1, id);

            try (ResultSet rs = ps.executeQuery()) {

                // Aucune transaction trouvée
                if (!rs.next()) {
                    return Optional.empty();
                }

                // Récupère la date de fin si elle existe
                Timestamp fin = rs.getTimestamp("termine_le");

                // Reconstruit la transaction à partir de la base
                Transaction transaction = new Transaction(
                    rs.getString("id"),
                    rs.getString("reference"),
                    rs.getBigDecimal("montant"),
                    rs.getString("devise"),
                    EtatTransaction.valueOf(rs.getString("etat")),
                    rs.getString("motif"),
                    rs.getTimestamp("cree_le").toInstant(),
                    fin == null ? null : fin.toInstant(),
                    (Double) rs.getObject("temps_attente_file_ms"),
                    (Double) rs.getObject("temps_traitement_ms")
                );

                return Optional.of(transaction);
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                "Erreur lors de la recherche de la transaction", e
            );
        }
    }
}
