package fr.minenorth.etat;

import fr.minenorth.api.BankService;
import fr.minenorth.api.MineNorth;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Impôt sur le compte : à intervalle régulier, un pourcentage de ce que chaque joueur CONNECTÉ possède au-dessus de la franchise
 * est prélevé sur son compte bancaire et versé au trésor de l'État. Le joueur est prévenu dans le chat.
 */
@Mod.EventBusSubscriber(modid = MineNorthEtat.MOD_ID)
public final class WealthTaxService {
    private WealthTaxService() {}

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) return;
        MinecraftServer s = e.getServer();
        EtatData d = EtatData.get(s);
        long now = System.currentTimeMillis();
        if (d.lastWealthTax == 0) { d.lastWealthTax = now; d.setDirty(); return; }
        if (now - d.lastWealthTax < EtatConfig.get().impot_compte_intervalle_minutes * 60_000L) return;
        d.lastWealthTax = now;
        d.setDirty();
        collect(s);
    }

    private static void collect(MinecraftServer s) {
        double pct = Etat.wealthPercent(s);
        if (pct <= 0) return;
        EtatData d = EtatData.get(s);
        BankService bank = MineNorth.bank();
        long franchise = Etat.cents(EtatConfig.get().impot_compte_franchise_euros);
        long total = 0;
        int taxed = 0;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (!bank.hasAccount(s, p.getUUID())) continue;
            long base = bank.balance(s, p.getUUID()) - franchise;
            long cents = Math.round(base * pct / 100.0);
            if (base <= 0 || cents <= 0) continue;
            // source « etat: » : non imposée une seconde fois ; le trésor est crédité ici.
            if (!bank.debit(s, p.getUUID(), cents, "etat:impot_compte", false).ok()) continue;
            d.balance += cents;
            total += cents;
            taxed++;
            p.sendSystemMessage(Component.literal("§6[Impôts] §fImpôt sur le compte : §c-" + Etat.money(cents) + "§f (" + Etat.percent(pct)
                    + " au-dessus de " + Etat.money(franchise) + "). Il finance les salaires et la ville."));
        }
        if (taxed > 0) d.log("Impôt sur le compte : " + taxed + " personne(s), " + Etat.money(total));
        d.setDirty();
    }
}
