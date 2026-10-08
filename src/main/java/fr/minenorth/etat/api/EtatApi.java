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

    // ------------------------------------------------------------------ supervision admin

    /** Pouvoirs du maire suspendus ? */
    public static boolean mayorLocked(MinecraftServer s) { return EtatData.get(s).mayorLocked; }

    public static String setMayorLocked(MinecraftServer s, boolean locked) {
        EtatData d = EtatData.get(s);
        d.mayorLocked = locked;
        d.setDirty();
        d.log(locked ? "Pouvoirs du maire suspendus (admin)" : "Pouvoirs du maire rétablis (admin)");
        return locked ? "Pouvoirs du maire suspendus." : "Pouvoirs du maire rétablis.";
    }

    /** Salaires : une ligne par poste, "clé|libellé|euros|défini en jeu (1/0)". */
    public static String[] salaryRows(MinecraftServer s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        EtatData d = EtatData.get(s);
        java.util.List<String> pg = fr.minenorth.api.MineNorth.police().grades();
        for (int i = 0; i < pg.size(); i++) out.add(row(s, d, "police", i, "Police · " + pg.get(i)));
        String[] sg = fr.minenorth.etat.SalaryService.secoursGrades();
        for (int i = 0; i < sg.length; i++) out.add(row(s, d, "pompier", i, "Pompiers · " + sg[i]));
        out.add(row(s, d, "agent", -1, "Agents municipaux"));
        return out.toArray(new String[0]);
    }

    private static String row(MinecraftServer s, EtatData d, String kind, int grade, String label) {
        String key = Etat.salaryKey(kind, grade);
        return key + "|" + label + "|" + Etat.salaryEuros(s, kind, grade) + "|" + (d.salaryOverride.containsKey(key) ? 1 : 0);
    }

    /** Fixe un salaire (admin : pas de plafond). key : "police:0", "pompier:1", "agent". */
    public static String setSalary(MinecraftServer s, String key, double euros) {
        if (euros < 0) return "Salaire invalide.";
        EtatData d = EtatData.get(s);
        d.salaryOverride.put(key, euros);
        d.log("Salaire " + key + " fixé à " + Etat.money(Etat.cents(euros)) + " (admin)");
        d.setDirty();
        return "Salaire fixé à " + Etat.money(Etat.cents(euros)) + " par paie.";
    }

    /** Revient au salaire de la config. */
    public static String resetSalary(MinecraftServer s, String key) {
        EtatData d = EtatData.get(s);
        d.salaryOverride.remove(key);
        d.log("Salaire " + key + " remis à la config (admin)");
        d.setDirty();
        return "Salaire remis à la valeur de la config.";
    }

    /** Agents municipaux : "uuid|nom|titre". */
    public static String[] agentRows(MinecraftServer s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (java.util.Map.Entry<UUID, String> en : EtatData.get(s).agents.entrySet())
            out.add(en.getKey() + "|" + fr.minenorth.api.MineNorth.displayName(s, en.getKey()) + "|" + en.getValue());
        return out.toArray(new String[0]);
    }

    public static String revokeAgent(MinecraftServer s, UUID id) {
        EtatData d = EtatData.get(s);
        if (d.agents.remove(id) == null) return null;
        String name = fr.minenorth.api.MineNorth.displayName(s, id);
        d.log(name + " révoqué (admin)");
        d.setDirty();
        net.minecraft.server.level.ServerPlayer on = s.getPlayerList().getPlayer(id);
        if (on != null) on.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[Mairie] Vous n'êtes plus agent municipal."));
        return name;
    }

    /** Dernières opérations du trésor, la plus récente d'abord : "horodatage|texte". */
    public static String[] ledger(MinecraftServer s) {
        java.util.List<String> l = EtatData.get(s).ledger;
        String[] out = new String[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(l.size() - 1 - i);
        return out;
    }

    /** Ajoute (cents > 0) ou retire (cents < 0) de l'argent au trésor. */
    public static String adjustTreasury(MinecraftServer s, long cents, String who) {
        EtatData d = EtatData.get(s);
        d.balance += cents;
        d.log((cents >= 0 ? "Ajout de " : "Retrait de ") + Etat.money(Math.abs(cents)) + " (admin " + who + ")");
        d.setDirty();
        return "Solde du trésor : " + Etat.money(d.balance) + ".";
    }

    /** Vrai pour le maire et les agents municipaux (le mod Portes l'appelle par réflexion : ne pas changer le nom ni les paramètres). */
    public static boolean isMairieStaff(net.minecraft.server.level.ServerPlayer p) {
        return Etat.isMayor(p) || EtatData.get(p.server).agents.containsKey(p.getUUID());
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
