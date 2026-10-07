package fr.minenorth.etat;

import fr.minenorth.api.BankService;
import fr.minenorth.api.MineNorth;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Paie, à intervalle régulier, les policiers (connectés), les pompiers (en service) et les agents municipaux (connectés).
 * Le salaire de leur grade est prélevé sur le trésor ; si le trésor ne suffit plus, personne n'est payé pour ce tour.
 */
@Mod.EventBusSubscriber(modid = MineNorthEtat.MOD_ID)
public final class SalaryService {
    private SalaryService() {}

    private static final String SECOURS = "fr.minenorth.secours.api.SecoursApi";
    private static boolean warnedEmpty;

    /** Un salarié à payer : qui, quel type, quel grade (-1 = agent) et libellé. */
    public record Worker(UUID id, String kind, int grade, String label) {}

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) return;
        MinecraftServer s = e.getServer();
        EtatData d = EtatData.get(s);
        long now = System.currentTimeMillis();
        if (d.lastPay == 0) { d.lastPay = now; d.setDirty(); return; }
        if (now - d.lastPay < EtatConfig.get().salaire_intervalle_minutes * 60_000L) return;
        d.lastPay = now;
        d.setDirty();
        payAll(s);
    }

    /** Tous ceux qui ont droit à un salaire maintenant. */
    public static List<Worker> workers(MinecraftServer s) {
        List<Worker> out = new ArrayList<>();
        var police = MineNorth.police();
        List<String> pg = police.grades();
        for (Map.Entry<UUID, Integer> en : police.officers(s).entrySet()) {
            if (s.getPlayerList().getPlayer(en.getKey()) == null) continue;
            int g = Math.max(0, en.getValue());
            out.add(new Worker(en.getKey(), "police", g, "Police" + (g < pg.size() ? " (" + pg.get(g) + ")" : "")));
        }
        String[] sg = secoursGrades();
        for (Map.Entry<UUID, Integer> en : secoursStaff(s).entrySet()) {
            if (s.getPlayerList().getPlayer(en.getKey()) == null || !secoursOnDuty(s, en.getKey())) continue;
            int g = Math.max(0, en.getValue());
            out.add(new Worker(en.getKey(), "pompier", g, "Pompiers" + (g < sg.length ? " (" + sg[g] + ")" : "")));
        }
        EtatData d = EtatData.get(s);
        for (Map.Entry<UUID, String> en : d.agents.entrySet()) {
            if (s.getPlayerList().getPlayer(en.getKey()) == null) continue;
            out.add(new Worker(en.getKey(), "agent", -1, en.getValue().isBlank() ? "Agent municipal" : en.getValue()));
        }
        return out;
    }

    private static void payAll(MinecraftServer s) {
        EtatData d = EtatData.get(s);
        BankService bank = MineNorth.bank();
        long total = 0;
        int paid = 0;
        boolean empty = false;
        for (Worker w : workers(s)) {
            long cents = Etat.cents(Etat.salaryEuros(s, w.kind(), w.grade()));
            ServerPlayer p = s.getPlayerList().getPlayer(w.id());
            if (cents <= 0 || p == null) continue;
            if (!bank.hasAccount(s, w.id())) {
                p.sendSystemMessage(Component.literal("§eSalaire non versé : vous n'avez pas de compte bancaire."));
                continue;
            }
            if (d.balance < cents) { empty = true; break; }
            // La source « etat: » n'est pas imposée : la banque retire de Core seulement, le solde de l'État est débité ici.
            if (!bank.refund(s, w.id(), cents, "etat:salaire")) continue;
            d.balance -= cents;
            total += cents;
            paid++;
            p.sendSystemMessage(Component.literal("§a[État] Salaire de " + w.label() + " : " + Etat.money(cents) + " versés sur votre compte."));
        }
        if (paid > 0) d.log("Salaires : " + paid + " personne(s), " + Etat.money(total));
        d.setDirty();
        if (empty && !warnedEmpty) {
            warnedEmpty = true;
            d.log("Salaires non versés : trésor insuffisant");
            Component msg = Component.literal("§c[État] Le trésor est vide : les salaires ne sont plus versés.");
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                if (Etat.isMayor(p) || p.hasPermissions(2)) p.sendSystemMessage(msg);
            }
        } else if (!empty) {
            warnedEmpty = false;
        }
    }

    // ------------------------------------------------------------------ pompiers (mod Secours, optionnel : réflexion)

    private static Object call(String method, Class<?>[] types, Object... args) {
        try {
            Method m = Class.forName(SECOURS).getMethod(method, types);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    public static String[] secoursGrades() {
        Object o = call("grades", new Class<?>[0]);
        return o instanceof String[] a ? a : new String[0];
    }

    @SuppressWarnings("unchecked")
    public static Map<UUID, Integer> secoursStaff(MinecraftServer s) {
        Object o = call("staff", new Class<?>[]{MinecraftServer.class}, s);
        return o instanceof Map<?, ?> m ? (Map<UUID, Integer>) m : Map.of();
    }

    public static boolean secoursOnDuty(MinecraftServer s, UUID id) {
        return Boolean.TRUE.equals(call("onDuty", new Class<?>[]{MinecraftServer.class, UUID.class}, s, id));
    }
}
