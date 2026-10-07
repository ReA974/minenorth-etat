package fr.minenorth.etat;

import fr.minenorth.api.MineNorth;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Élection du maire : candidatures et votes pendant toute la durée, un vote par citoyen, le plus de voix gagne. */
@Mod.EventBusSubscriber(modid = MineNorthEtat.MOD_ID)
public final class ElectionService {
    private ElectionService() {}

    private static void broadcast(MinecraftServer s, String text) {
        s.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }

    /** Ouvre une élection. Renvoie un message d'erreur, ou null si OK. */
    public static String open(MinecraftServer s, int minutes) {
        EtatData d = EtatData.get(s);
        if (d.electionOpen) return "Une élection est déjà en cours.";
        d.electionOpen = true;
        d.electionEnd = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;
        d.candidates.clear();
        d.votes.clear();
        d.setDirty();
        d.log("Élection ouverte (" + minutes + " min)");
        broadcast(s, "§6§l[Mairie] Élection municipale ouverte !§r§6 Fin dans " + duration(minutes * 60_000L)
                + ". Candidature : §e/election candidat§6 · Vote : §e/election voter <nom>§6 · Liste : §e/election liste");
        return null;
    }

    /** Clôture tout de suite et compte les voix (fin normale ou décision d'un admin). */
    public static String close(MinecraftServer s) {
        EtatData d = EtatData.get(s);
        if (!d.electionOpen) return "Aucune élection en cours.";
        d.electionOpen = false;
        Map<UUID, Integer> count = new HashMap<>();
        for (UUID c : d.candidates.keySet()) count.put(c, 0);
        for (UUID c : d.votes.values()) count.merge(c, 1, Integer::sum);
        UUID winner = null;
        int best = -1;
        boolean tie = false;
        for (Map.Entry<UUID, Integer> en : count.entrySet()) {
            if (en.getValue() > best) { best = en.getValue(); winner = en.getKey(); tie = false; }
            else if (en.getValue() == best) tie = true;
        }
        StringBuilder res = new StringBuilder();
        count.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).forEach(en ->
                res.append("\n§7 - ").append(name(d, en.getKey())).append(" : ").append(en.getValue()).append(" voix"));
        if (count.isEmpty() || best <= 0) {
            broadcast(s, "§6[Mairie] Élection terminée sans résultat (aucun candidat ou aucun vote). Le maire reste inchangé." + res);
            d.log("Élection terminée sans résultat");
        } else if (tie) {
            broadcast(s, "§6[Mairie] Élection terminée sur une égalité : aucun maire élu, le maire reste inchangé." + res);
            d.log("Élection terminée : égalité");
        } else {
            setMayor(s, winner, name(d, winner));
            broadcast(s, "§6§l[Mairie] " + name(d, winner) + " est élu maire !§r" + res);
            d.log("Élection : " + name(d, winner) + " élu maire (" + best + " voix)");
        }
        d.candidates.clear();
        d.votes.clear();
        d.setDirty();
        return null;
    }

    public static String cancel(MinecraftServer s) {
        EtatData d = EtatData.get(s);
        if (!d.electionOpen) return "Aucune élection en cours.";
        d.electionOpen = false;
        d.candidates.clear();
        d.votes.clear();
        d.setDirty();
        d.log("Élection annulée");
        broadcast(s, "§6[Mairie] L'élection a été annulée.");
        return null;
    }

    /** Nomme un maire (élection ou admin) : ses agents sont ceux de l'ancien maire, ils sont révoqués. */
    public static void setMayor(MinecraftServer s, UUID id, String name) {
        EtatData d = EtatData.get(s);
        d.mayor = id;
        d.mayorName = id == null ? "" : name;
        int days = EtatConfig.get().mandat_jours;
        d.mayorUntil = id == null || days <= 0 ? 0 : System.currentTimeMillis() + days * 86_400_000L;
        d.agents.clear();
        d.mayorLocked = false;
        d.setDirty();
    }

    public static String candidate(ServerPlayer p) {
        MinecraftServer s = p.server;
        EtatData d = EtatData.get(s);
        if (!d.electionOpen) return "Aucune élection en cours.";
        if (!Etat.citizen(s, p.getUUID())) return "Il faut une carte d'identité pour se présenter.";
        if (d.candidates.containsKey(p.getUUID())) return "Vous êtes déjà candidat.";
        d.candidates.put(p.getUUID(), MineNorth.displayName(p));
        d.setDirty();
        broadcast(s, "§6[Mairie] " + MineNorth.displayName(p) + " se présente à l'élection municipale.");
        return null;
    }

    public static String withdraw(ServerPlayer p) {
        EtatData d = EtatData.get(p.server);
        if (!d.electionOpen) return "Aucune élection en cours.";
        if (d.candidates.remove(p.getUUID()) == null) return "Vous n'êtes pas candidat.";
        d.votes.values().removeIf(v -> v.equals(p.getUUID()));
        d.setDirty();
        return null;
    }

    public static String vote(ServerPlayer p, String who) {
        MinecraftServer s = p.server;
        EtatData d = EtatData.get(s);
        if (!d.electionOpen) return "Aucune élection en cours.";
        if (!Etat.citizen(s, p.getUUID())) return "Il faut une carte d'identité pour voter.";
        if (d.votes.containsKey(p.getUUID())) return "Vous avez déjà voté.";
        UUID target = find(d, who);
        if (target == null) return "Candidat introuvable (voir /election liste).";
        d.votes.put(p.getUUID(), target);
        d.setDirty();
        return null;
    }

    /** Vote pour un candidat connu par son identifiant (écran du bureau de vote). */
    public static String voteFor(ServerPlayer p, UUID target) {
        MinecraftServer s = p.server;
        EtatData d = EtatData.get(s);
        if (!d.electionOpen) return "Aucune élection en cours.";
        if (!Etat.citizen(s, p.getUUID())) return "Il faut une carte d'identité pour voter.";
        if (d.votes.containsKey(p.getUUID())) return "Vous avez déjà voté.";
        if (!d.candidates.containsKey(target)) return "Ce candidat n'existe plus.";
        d.votes.put(p.getUUID(), target);
        d.setDirty();
        return null;
    }

    /** Candidat par nom RP ou pseudo (exact d'abord, puis partiel). */
    static UUID find(EtatData d, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        UUID partial = null;
        for (Map.Entry<UUID, String> en : d.candidates.entrySet()) {
            String n = en.getValue().toLowerCase(Locale.ROOT);
            if (n.equals(q)) return en.getKey();
            if (partial == null && n.contains(q)) partial = en.getKey();
        }
        return partial;
    }

    private static String name(EtatData d, UUID id) {
        String n = d.candidates.get(id);
        return n != null ? n : id.toString().substring(0, 8);
    }

    public static String duration(long ms) {
        long min = Math.max(0, ms / 60_000L);
        if (min >= 1440) return (min / 1440) + " j " + ((min % 1440) / 60) + " h";
        if (min >= 60) return (min / 60) + " h " + (min % 60) + " min";
        return min + " min";
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) return;
        MinecraftServer s = e.getServer();
        EtatData d = EtatData.get(s);
        long now = System.currentTimeMillis();
        if (d.electionOpen && now >= d.electionEnd) close(s);
        if (d.mayor != null && d.mayorUntil > 0 && now >= d.mayorUntil) {
            String name = d.mayorName;
            setMayor(s, null, "");
            d.log("Fin du mandat de " + name);
            broadcast(s, "§6[Mairie] Le mandat de " + name + " est terminé. La ville n'a plus de maire : une élection doit être ouverte.");
        }
    }
}
