package fr.minenorth.etat;

import fr.minenorth.api.Treasury;
import net.minecraft.server.MinecraftServer;

import java.util.Map;

/**
 * Trésor de l'État. Chaque somme encaissée par un mod (achat, amende…) passe ici pour les statistiques de Core ; l'État ne prélève PLUS d'impôt sur les achats
 * (le trésor est alimenté par l'impôt sur le compte et l'impôt sur le salaire).
 */
public final class EtatTreasury implements Treasury {
    private final Treasury stats;

    public EtatTreasury(Treasury stats) { this.stats = stats; }

    @Override
    public void collect(MinecraftServer s, long cents, String source) {
        stats.collect(s, cents, source);
        // plus d'impôt sur les achats : seuls l'impôt sur le compte et l'impôt sur le salaire alimentent le trésor (voir WealthTaxService, Etat.withholdSalary)
    }

    @Override
    public long total(MinecraftServer s) { return stats.total(s); }

    @Override
    public Map<String, Long> bySource(MinecraftServer s) { return stats.bySource(s); }
}
