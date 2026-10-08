package fr.minenorth.etat.client;

import fr.minenorth.etat.network.EtatNetwork;
import net.minecraft.client.Minecraft;

/** Côté client : ouvre ou met à jour l'écran du bureau de vote. */
public final class ClientNetworkHandler {
    private ClientNetworkHandler() {}

    public static void tablet(EtatNetwork.TabletState p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TabletScreen screen) screen.update(p);
        else mc.setScreen(new TabletScreen(p));
    }

    public static void laws(EtatNetwork.LawsPacket p) {
        Minecraft.getInstance().setScreen(new LawsScreen(p));
    }

    public static void state(EtatNetwork.StatePacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BallotScreen screen) screen.update(p);
        else mc.setScreen(new BallotScreen(p));
    }
}
