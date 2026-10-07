package fr.minenorth.etat;

import fr.minenorth.api.PlayerWipeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Suppression d'un joueur depuis le panneau admin : mandat de maire, agent, candidature et votes effacés. */
@Mod.EventBusSubscriber(modid = MineNorthEtat.MOD_ID)
public final class EtatWipe {
    private EtatWipe() {}

    @SubscribeEvent
    public static void onWipe(PlayerWipeEvent e) {
        EtatData d = EtatData.get(e.server());
        boolean any = false;
        if (e.player().equals(d.mayor)) {
            ElectionService.setMayor(e.server(), null, "");
            any = true;
        }
        any |= d.agents.remove(e.player()) != null;
        any |= d.candidates.remove(e.player()) != null;
        any |= d.votes.remove(e.player()) != null;
        any |= d.votes.values().removeIf(v -> v.equals(e.player()));
        if (any) {
            d.setDirty();
            e.cleaned("état");
        }
    }
}
