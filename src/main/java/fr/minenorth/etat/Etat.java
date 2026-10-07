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

    /** kind : "police", "pompier" (grade >= 0) ou "agent" (grade ignoré). Euros par paie. */
    public static double salaryEuros(MinecraftServer s, String kind, int grade) {
        Double o = EtatData.get(s).salaryOverride.get(salaryKey(kind, grade));
        if (o != null) return o;
        EtatConfig c = EtatConfig.get();
        double[] table = kind.equals("police") ? c.salaire_police_euros : kind.equals("pompier") ? c.salaire_pompier_euros : null;
        if (table == null) return c.salaire_agent_euros;
        if (grade < 0) return 0;
        return table[Math.min(grade, table.length - 1)];
    }

    public static String salaryKey(String kind, int grade) { return kind.equals("agent") ? "agent" : kind + ":" + grade; }

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
