package fr.minenorth.etat;

import fr.minenorth.api.Treasury;
import net.minecraft.server.MinecraftServer;

import java.util.Map;

/**
 * Trésor de l'État. Chaque somme encaissée par un mod (achat, amende…) passe ici : seul l'IMPÔT (un pourcentage
 * réglable) alimente le solde dépensable ; Core garde les statistiques de la totalité.
 * Les opérations de l'État lui-même (source « etat:… », salaires, dépenses) ne sont pas imposées.
 */
public final class EtatTreasury implements Treasury {
    private final Treasury stats;

    public EtatTreasury(Treasury stats) { this.stats = stats; }

    @Override
    public void collect(MinecraftServer s, long cents, String source) {
        stats.collect(s, cents, source);
        if (source != null && source.startsWith("etat:")) return;
        long tax = Math.round(cents * Etat.taxPercent(s) / 100.0);   // un remboursement (cents < 0) reprend sa part
        if (tax == 0) return;
        EtatData d = EtatData.get(s);
        d.balance += tax;
        d.addSource(source == null ? "?" : source, tax);
        d.setDirty();
    }

    @Override
    public long total(MinecraftServer s) { return stats.total(s); }

    @Override
    public Map<String, Long> bySource(MinecraftServer s) { return stats.bySource(s); }
}
