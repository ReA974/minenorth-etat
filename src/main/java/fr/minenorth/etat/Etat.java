package fr.minenorth.etat;

import fr.minenorth.api.IdentityService;
import fr.minenorth.api.MineNorth;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Règles communes : impôt, salaires, maire, argent. */
public final class Etat {
    private Etat() {}

    /** Impôt en % de chaque achat. */
    public static double taxPercent(MinecraftServer s) {
        double o = EtatData.get(s).taxOverride;
        return o >= 0 ? o : EtatConfig.get().impot_pourcent;
    }

    /** Impôt sur le compte des joueurs connectés, en % du solde au-dessus de la franchise. */
    public static double wealthPercent(MinecraftServer s) {
        double o = EtatData.get(s).wealthOverride;
        return o >= 0 ? o : EtatConfig.get().impot_compte_pourcent;
    }

    /** Impôt sur le salaire, en % de chaque salaire reçu. */
    public static double salaryTaxPercent(MinecraftServer s) {
        double o = EtatData.get(s).salaryTaxOverride;
        return o >= 0 ? o : EtatConfig.get().impot_salaire_pourcent;
    }

    /**
     * Retient l'impôt sur un salaire de {@code gross} centimes déjà versé au joueur : prélevé sur son compte (source « etat: », non imposée une
     * seconde fois) et versé au trésor. Renvoie le montant retenu (0 si désactivé ou si le prélèvement échoue).
     */
    public static long withholdSalary(MinecraftServer s, UUID id, long gross) {
        double pct = salaryTaxPercent(s);
        if (pct <= 0 || gross <= 0) return 0;
        long tax = Math.round(gross * pct / 100.0);
        if (tax <= 0 || !MineNorth.bank().debit(s, id, tax, "etat:impot_salaire", false).ok()) return 0;
        EtatData d = EtatData.get(s);
        d.balance += tax;
        d.addSource("etat:impot_salaire", tax);
        d.setDirty();
        ServerPlayer p = s.getPlayerList().getPlayer(id);
        if (p != null) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6[Impôts] §fImpôt sur le salaire : §c-" + money(tax) + "§f (" + percent(pct) + ")."));
        return tax;
    }

    /** kind : "police", "pompier" (grade >= 0) ou "agent" (grade ignoré). Euros par paie. */
    public static double salaryEuros(MinecraftServer s, String kind, int grade) {
        Double o = EtatData.get(s).salaryOverride.get(salaryKey(kind, grade));
        if (o != null) return o;
        EtatConfig c = EtatConfig.get();
        if (kind.equals("maire")) return c.salaire_maire_euros;
        double[] table = kind.equals("police") ? c.salaire_police_euros : kind.equals("pompier") ? c.salaire_pompier_euros : null;
        if (table == null) return c.salaire_agent_euros;
        if (grade < 0) return 0;
        return table[Math.min(grade, table.length - 1)];
    }

    public static String salaryKey(String kind, int grade) { return kind.equals("agent") || kind.equals("maire") ? kind : kind + ":" + grade; }

    /** Clé du salaire individuel d'un agent municipal (passe avant le salaire commun des agents). */
    public static String agentKey(UUID id) { return "agent:" + id; }

    /** Salaire d'un agent municipal : le sien s'il a été fixé, sinon le salaire commun des agents. Euros par paie. */
    public static double agentSalaryEuros(MinecraftServer s, UUID id) {
        Double o = EtatData.get(s).salaryOverride.get(agentKey(id));
        return o != null ? o : salaryEuros(s, "agent", -1);
    }

    /** Salaire d'un salarié de l'État (individuel pour un agent). Euros par paie. */
    public static double workerSalaryEuros(MinecraftServer s, SalaryService.Worker w) {
        return w.kind().equals("agent") ? agentSalaryEuros(s, w.id()) : salaryEuros(s, w.kind(), w.grade());
    }

    public static boolean isMayor(MinecraftServer s, UUID id) {
        UUID m = EtatData.get(s).mayor;
        return m != null && m.equals(id);
    }

    public static boolean isMayor(ServerPlayer p) { return isMayor(p.server, p.getUUID()); }

    /** Un citoyen peut voter / se présenter s'il a une carte d'identité (si le mod Identité est absent : tout le monde). */
    public static boolean citizen(MinecraftServer s, UUID id) {
        return MineNorth.identity() == IdentityService.NONE || MineNorth.identity().has(s, id);
    }

    public static long cents(double euros) { return Math.round(euros * 100.0); }

    public static String money(long cents) {
        long a = Math.abs(cents);
        return (cents < 0 ? "-" : "") + (a / 100) + (a % 100 == 0 ? "" : "," + String.format("%02d", a % 100)) + " €";
    }

    public static String percent(double p) {
        String s = p == Math.rint(p) ? String.valueOf((long) p) : String.format(java.util.Locale.ROOT, "%.2f", p).replaceAll("0+$", "");
        return s + " %";
    }
}
