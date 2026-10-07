package fr.minenorth.etat.api;

import fr.minenorth.etat.Etat;
import fr.minenorth.etat.EtatConfig;
import fr.minenorth.etat.EtatData;
import fr.minenorth.etat.ElectionService;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * API pour les autres mods MineNorth (le panneau Admin l'appelle par réflexion : ne pas changer les noms ni les
 * paramètres). Thread serveur uniquement.
 */
public final class EtatApi {
    private EtatApi() {}

    /** Solde dépensable du trésor, en centimes. */
    public static long balance(MinecraftServer s) { return EtatData.get(s).balance; }

    /** Impôt en cours, en % de chaque achat. */
    public static double taxPercent(MinecraftServer s) { return Etat.taxPercent(s); }

    /** Plafond de la config pour le maire. */
    public static double taxMaxPercent() { return EtatConfig.get().impot_max_pourcent; }

    /** Fixe l'impôt (admin : pas de plafond, 0..100). Renvoie le message de résultat. */
    public static String setTaxPercent(MinecraftServer s, double percent) {
        if (percent < 0 || percent > 100) return "L'impôt doit être entre 0 et 100 %.";
        EtatData d = EtatData.get(s);
        d.taxOverride = percent;
        d.setDirty();
        d.log("Impôt fixé à " + Etat.percent(percent) + " (admin)");
        return "Impôt fixé à " + Etat.percent(percent) + ".";
    }

    /** Revient à la valeur de la config. */
    public static String resetTaxPercent(MinecraftServer s) {
        EtatData d = EtatData.get(s);
        d.taxOverride = -1;
        d.setDirty();
        d.log("Impôt remis à la valeur de la config (admin)");
        return "Impôt remis à " + Etat.percent(Etat.taxPercent(s)) + " (config).";
    }

    /** Uuid du maire, ou null. */
    public static UUID mayor(MinecraftServer s) { return EtatData.get(s).mayor; }

    public static String mayorName(MinecraftServer s) { return EtatData.get(s).mayorName; }

    /** Nomme (ou, avec null, retire) le maire. Ses agents sont révoqués. */
    public static String setMayor(MinecraftServer s, UUID id, String name) {
        ElectionService.setMayor(s, id, name);
        EtatData.get(s).log(id == null ? "Maire retiré (admin)" : name + " nommé maire (admin)");
        return id == null ? "Il n'y a plus de maire." : name + " est maintenant maire.";
    }

    public static boolean electionOpen(MinecraftServer s) { return EtatData.get(s).electionOpen; }

    public static int agentCount(MinecraftServer s) { return EtatData.get(s).agents.size(); }

    /** Ouvre une élection (minutes <= 0 : durée de la config). Renvoie un message d'erreur, ou null. */
    public static String openElection(MinecraftServer s, int minutes) {
        return ElectionService.open(s, minutes > 0 ? minutes : EtatConfig.get().election_duree_minutes);
    }

    public static String closeElection(MinecraftServer s) { return ElectionService.close(s); }

    public static String cancelElection(MinecraftServer s) { return ElectionService.cancel(s); }
}
