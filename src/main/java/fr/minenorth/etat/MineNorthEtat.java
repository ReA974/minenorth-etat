package fr.minenorth.etat;

import fr.minenorth.api.MineNorth;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(MineNorthEtat.MOD_ID)
public class MineNorthEtat {
    public static final String MOD_ID = "minenorthetat";

    public MineNorthEtat() {
        EtatConfig.load();
        fr.minenorth.etat.block.ModBlocks.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        fr.minenorth.etat.network.EtatNetwork.register();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        fr.minenorth.etat.network.EtatNetwork.forget(e.getEntity().getUUID());
    }

    /**
     * Après le chargement de tous les mods : le trésor de l'État remplace celui de Core (qui ne faisait que compter)
     * en gardant Core comme statistiques, pour que tous les mods qui appellent MineNorth.treasury() passent par lui.
     */
    @SubscribeEvent
    public void serverStarting(ServerStartingEvent e) {
        if (MineNorth.treasury() instanceof EtatTreasury) return;
        MineNorth.provide(fr.minenorth.api.Treasury.class, new EtatTreasury(MineNorth.treasury()));
    }
}
