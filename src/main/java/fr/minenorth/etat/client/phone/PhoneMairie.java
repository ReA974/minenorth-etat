package fr.minenorth.etat.client.phone;

import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import fr.minenorth.etat.network.EtatNetwork;
import fr.minenorth.etat.network.PhoneNet;
import net.minecraft.client.Minecraft;

/**
 * Seul point d'entrée vers {@link PhoneMairieScreen} (qui hérite d'une classe du téléphone MikasRevs Phone 2). Ne JAMAIS toucher cette classe sans
 * avoir vérifié {@code PhoneAppClient.phone()} : sa vérification par la JVM charge PhoneMairieScreen, donc la classe du téléphone. Côté client uniquement.
 */
public final class PhoneMairie {
    /** Ouverture demandée depuis le téléphone : le prochain état de la tablette ouvre l'application dans le téléphone (pendant 5 s). */
    private static long pendingUntil;

    private PhoneMairie() {}

    /** Clic sur l'icône : on garde le téléphone ouvert et on demande l'ouverture au serveur (qui revérifie le métier). */
    public static void request() {
        pendingUntil = System.currentTimeMillis() + 5000;
        EtatNetwork.CHANNEL.sendToServer(new PhoneNet.Open());
    }

    /** true si l'état a été pris par l'application du téléphone (ouverte ou mise à jour). */
    public static boolean handle(EtatNetwork.TabletState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PhoneMairieScreen ps) {
            ps.update(state);
            return true;
        }
        if (System.currentTimeMillis() < pendingUntil && mc.player != null) {
            pendingUntil = 0;
            PhoneNavigationState.open(mc, mc.screen, new PhoneMairieScreen(state));
            return true;
        }
        return false;
    }
}
