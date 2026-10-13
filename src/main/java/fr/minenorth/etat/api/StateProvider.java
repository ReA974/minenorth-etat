package fr.minenorth.etat.api;

import fr.minenorth.api.StateService;
import fr.minenorth.etat.Etat;
import fr.minenorth.etat.EtatData;
import net.minecraft.server.level.ServerPlayer;

/** Fournit la mairie aux autres mods via MineNorth API : le maire élu et les agents qu'il a nommés. */
public final class StateProvider implements StateService {
    @Override
    public boolean isTownHall(ServerPlayer p) {
        return Etat.isMayor(p) || EtatData.get(p.server).agents.containsKey(p.getUUID());
    }
}
