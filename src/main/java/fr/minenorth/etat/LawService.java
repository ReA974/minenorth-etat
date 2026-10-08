package fr.minenorth.etat;

import fr.minenorth.api.MineNorth;
import fr.minenorth.etat.network.EtatNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Lois du maire : promulgation, abrogation et envoi du tableau d'affichage. Les droits sont vérifiés par l'appelant. */
public final class LawService {
    private LawService() {}

    public static final int MAX_LAWS = 100, MAX_TITLE = 64, MAX_TEXT = 400;

    /** Promulgue une loi. Renvoie un message d'erreur, ou null. */
    public static String enact(ServerPlayer by, String title, String text) {
        title = title == null ? "" : title.trim();
        text = text == null ? "" : text.trim();
        if (title.isEmpty()) return "Il faut un titre.";
        if (text.isEmpty()) return "Il faut un texte de loi.";
        if (title.length() > MAX_TITLE) return "Titre trop long (" + MAX_TITLE + " caractères maximum).";
        if (text.length() > MAX_TEXT) return "Texte trop long (" + MAX_TEXT + " caractères maximum).";
        EtatData d = EtatData.get(by.server);
        if (d.laws.size() >= MAX_LAWS) return "Le tableau est plein (" + MAX_LAWS + " lois) : abrogez-en une.";
        String who = MineNorth.displayName(by);
        int num = ++d.lawCounter;
        d.laws.add(new EtatData.Law(num, title, text, who, System.currentTimeMillis()));
        d.log("Loi n°" + num + " « " + title + " » promulguée par " + who);
        d.setDirty();
        broadcastTitles(by.server);
        return null;
    }

    /** Abroge une loi par son numéro. Renvoie un message d'erreur, ou null. */
    public static String repeal(ServerPlayer by, int num) {
        EtatData d = EtatData.get(by.server);
        EtatData.Law found = null;
        for (EtatData.Law l : d.laws) if (l.num() == num) found = l;
        if (found == null) return "Loi introuvable.";
        d.laws.remove(found);
        d.log("Loi n°" + num + " « " + found.title() + " » abrogée par " + MineNorth.displayName(by));
        d.setDirty();
        broadcastTitles(by.server);
        return null;
    }

    /** Lignes "num|titre|texte|auteur|date", la plus récente d'abord. */
    public static List<String> rows(EtatData d) {
        List<String> out = new ArrayList<>();
        for (int i = d.laws.size() - 1; i >= 0; i--) {
            EtatData.Law l = d.laws.get(i);
            out.add(l.num() + "|" + l.title().replace('|', '/') + "|" + l.text().replace('|', '/') + "|" + l.author().replace('|', '/') + "|" + l.date());
        }
        return out;
    }

    /** Ouvre le tableau des lois chez ce joueur (tout le monde peut le lire). */
    public static void open(ServerPlayer p) {
        EtatData d = EtatData.get(p.server);
        EtatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new EtatNetwork.LawsPacket(
                d.mayor == null ? "" : MineNorth.displayName(p.server, d.mayor), rows(d)));
    }

    private static EtatNetwork.LawTitlesPacket titles(EtatData d) {
        List<String> out = new ArrayList<>();
        for (int i = d.laws.size() - 1; i >= 0; i--) out.add(d.laws.get(i).num() + "|" + d.laws.get(i).title());
        return new EtatNetwork.LawTitlesPacket(out);
    }

    /** Titres affichés sur les tableaux : envoyés à la connexion et à chaque changement. */
    public static void sendTitles(ServerPlayer p) {
        EtatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), titles(EtatData.get(p.server)));
    }

    public static void broadcastTitles(net.minecraft.server.MinecraftServer s) {
        EtatNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), titles(EtatData.get(s)));
    }
}
