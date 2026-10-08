package fr.minenorth.etat;

import fr.minenorth.api.MineNorth;
import fr.minenorth.etat.api.EtatApi;
import fr.minenorth.etat.network.EtatNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tablette de la mairie. Le maire gère tout (impôt, salaires, agents, élections) ; les agents municipaux consultent
 * le trésor et gèrent les élections. Les droits sont revérifiés à CHAQUE action. Si un admin a suspendu les
 * pouvoirs du maire, la tablette passe en lecture seule (sauf pour les OP).
 */
public final class TabletService {
    private TabletService() {}

    public static final int ROLE_NONE = 0, ROLE_AGENT = 1, ROLE_MAYOR = 2;

    public static int role(ServerPlayer p) {
        if (p.hasPermissions(2) || Etat.isMayor(p)) return ROLE_MAYOR;
        return EtatData.get(p.server).agents.containsKey(p.getUUID()) ? ROLE_AGENT : ROLE_NONE;
    }

    private static boolean locked(ServerPlayer p) {
        return EtatData.get(p.server).mayorLocked && !p.hasPermissions(2);
    }

    public static void open(ServerPlayer p) {
        if (role(p) == ROLE_NONE) {
            p.sendSystemMessage(Component.literal("§c[Mairie] Cette tablette est réservée au maire et aux agents municipaux."));
            return;
        }
        sendState(p, "", true);
    }

    public static void sendState(ServerPlayer p, String message, boolean ok) {
        int role = role(p);
        if (role == ROLE_NONE) return;
        MinecraftServer s = p.server;
        EtatData d = EtatData.get(s);
        List<String> salaries = List.of(EtatApi.salaryRows(s));
        List<String> agents = new ArrayList<>();
        for (Map.Entry<UUID, String> en : d.agents.entrySet())
            agents.add(en.getKey() + "|" + MineNorth.displayName(s, en.getKey()) + "|" + en.getValue());
        List<String> ledger = new ArrayList<>();
        for (int i = d.ledger.size() - 1; i >= 0 && ledger.size() < 8; i--) ledger.add(d.ledger.get(i));
        int seconds = d.electionOpen ? (int) Math.max(0, (d.electionEnd - System.currentTimeMillis()) / 1000) : 0;
        EtatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new EtatNetwork.TabletState(
                role, locked(p), d.balance, Etat.taxPercent(s), EtatConfig.get().impot_max_pourcent,
                d.mayor == null ? "" : MineNorth.displayName(s, d.mayor), d.electionOpen, seconds, d.candidates.size(), d.votes.size(),
                salaries, agents, ledger, message, ok));
    }

    /** Traite une action de la tablette ; le résultat repart vers le joueur avec l'état à jour. */
    public static void handle(ServerPlayer p, String cmd, String a, String b) {
        int role = role(p);
        if (role == ROLE_NONE) return;
        if (locked(p)) { sendState(p, "Pouvoirs suspendus par un administrateur.", false); return; }
        MinecraftServer s = p.server;
        EtatData d = EtatData.get(s);
        String who = MineNorth.displayName(p);
        String error = null, done = "";
        boolean mayor = role == ROLE_MAYOR;
        switch (cmd) {
            case "elec_open" -> {
                int min = parseInt(a, EtatConfig.get().election_duree_minutes);
                if (min < 1 || min > 60 * 24 * 30) error = "Durée invalide (1 minute à 30 jours).";
                else { error = ElectionService.open(s, min); done = "Élection ouverte."; }
            }
            case "elec_close" -> { error = ElectionService.close(s); done = "Élection clôturée."; }
            case "elec_cancel" -> { error = ElectionService.cancel(s); done = "Élection annulée."; }
            case "tax" -> {
                if (!mayor) error = "Réservé au maire.";
                else {
                    double v = parseDouble(a);
                    double max = EtatConfig.get().impot_max_pourcent;
                    if (Double.isNaN(v) || v < 0 || v > 100) error = "Impôt invalide (0 à 100 %).";
                    else if (v > max && !p.hasPermissions(2)) error = "Le maximum autorisé est " + Etat.percent(max) + ".";
                    else {
                        d.taxOverride = v;
                        d.log("Impôt fixé à " + Etat.percent(v) + " par " + who);
                        d.setDirty();
                        done = "Impôt fixé à " + Etat.percent(v) + ".";
                    }
                }
            }
            case "salary" -> {
                if (!mayor) error = "Réservé au maire.";
                else {
                    double v = parseDouble(b);
                    boolean known = false;
                    for (String row : EtatApi.salaryRows(s)) if (row.startsWith(a + "|")) known = true;
                    if (!known) error = "Poste inconnu.";
                    else if (Double.isNaN(v) || v < 0) error = "Salaire invalide.";
                    else if (v > EtatConfig.get().salaire_max_euros && !p.hasPermissions(2))
                        error = "Le salaire maximum autorisé est " + Etat.money(Etat.cents(EtatConfig.get().salaire_max_euros)) + ".";
                    else {
                        d.salaryOverride.put(a, v);
                        d.log("Salaire " + a + " fixé à " + Etat.money(Etat.cents(v)) + " par " + who);
                        d.setDirty();
                        done = "Salaire fixé à " + Etat.money(Etat.cents(v)) + " par paie.";
                    }
                }
            }
            case "appoint" -> {
                if (!mayor) error = "Réservé au maire.";
                else {
                    error = appoint(p, a.trim(), b.trim().isEmpty() ? "Agent municipal" : b.trim());
                    done = a.trim() + " est agent municipal.";
                }
            }
            case "revoke" -> {
                if (!mayor) error = "Réservé au maire.";
                else {
                    UUID id;
                    try { id = UUID.fromString(a); } catch (IllegalArgumentException e) { id = null; }
                    if (id == null || !d.agents.containsKey(id)) error = "Agent introuvable.";
                    else {
                        String name = MineNorth.displayName(s, id);
                        d.agents.remove(id);
                        d.log(name + " révoqué par " + who);
                        d.setDirty();
                        ServerPlayer on = s.getPlayerList().getPlayer(id);
                        if (on != null) on.sendSystemMessage(Component.literal("§e[Mairie] Vous n'êtes plus agent municipal."));
                        done = name + " n'est plus agent municipal.";
                    }
                }
            }
            default -> error = "Action inconnue.";
        }
        sendState(p, error != null ? error : done, error == null);
    }

    private static String appoint(ServerPlayer mayor, String name, String title) {
        MinecraftServer s = mayor.server;
        EtatData d = EtatData.get(s);
        ServerPlayer target = s.getPlayerList().getPlayerByName(name);
        if (target == null) return "Joueur introuvable (il doit être connecté, pseudo Minecraft exact).";
        if (d.agents.containsKey(target.getUUID())) {
            d.agents.put(target.getUUID(), title);
            d.setDirty();
            return null;
        }
        if (d.agents.size() >= EtatConfig.get().agents_max) return "Nombre maximum d'agents atteint (" + EtatConfig.get().agents_max + ").";
        if (Etat.isMayor(target)) return "Le maire ne peut pas être son propre agent.";
        d.agents.put(target.getUUID(), title);
        d.log(MineNorth.displayName(target) + " nommé " + title + " par " + MineNorth.displayName(mayor));
        d.setDirty();
        target.sendSystemMessage(Component.literal("§a[Mairie] Vous êtes nommé : " + title + ". Salaire : "
                + Etat.money(Etat.cents(Etat.salaryEuros(s, "agent", -1))) + " par paie. Utilisez la tablette de la mairie pour gérer la ville."));
        return null;
    }

    private static int parseInt(String v, int def) {
        try { return v == null || v.isBlank() ? def : Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return -1; }
    }

    private static double parseDouble(String v) {
        try { return Double.parseDouble(v.trim().replace(',', '.').replace("€", "").replace("%", "").trim()); } catch (RuntimeException e) { return Double.NaN; }
    }
}
