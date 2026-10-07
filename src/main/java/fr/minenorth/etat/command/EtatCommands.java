package fr.minenorth.etat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.minenorth.api.BankService;
import fr.minenorth.api.MineNorth;
import fr.minenorth.etat.ElectionService;
import fr.minenorth.etat.Etat;
import fr.minenorth.etat.EtatConfig;
import fr.minenorth.etat.EtatData;
import fr.minenorth.etat.MineNorthEtat;
import fr.minenorth.etat.SalaryService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /election (citoyens), /mairie (infos publiques), /maire (le maire, ou un OP), /etat (OP).
 */
@Mod.EventBusSubscriber(modid = MineNorthEtat.MOD_ID)
public final class EtatCommands {
    private EtatCommands() {}

    private static int say(CommandSourceStack src, String msg, boolean ok) {
        src.sendSystemMessage(Component.literal((ok ? "§a" : "§c") + msg));
        return ok ? 1 : 0;
    }

    private static int result(CommandSourceStack src, String error, String okMessage) {
        return error == null ? say(src, okMessage, true) : say(src, error, false);
    }

    private static boolean mayorOrOp(CommandSourceStack s) {
        return s.hasPermission(2) || (s.getEntity() instanceof ServerPlayer p && Etat.isMayor(p));
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(election());
        d.register(mairie());
        d.register(maire());
        d.register(etat());
    }

    // ------------------------------------------------------------------ /election

    private static LiteralArgumentBuilder<CommandSourceStack> election() {
        return Commands.literal("election")
                .then(Commands.literal("ouvrir").requires(s -> s.hasPermission(2))
                        .executes(c -> result(c.getSource(), ElectionService.open(c.getSource().getServer(), EtatConfig.get().election_duree_minutes), "Élection ouverte."))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 60 * 24 * 30))
                                .executes(c -> result(c.getSource(), ElectionService.open(c.getSource().getServer(), IntegerArgumentType.getInteger(c, "minutes")), "Élection ouverte."))))
                .then(Commands.literal("fermer").requires(s -> s.hasPermission(2))
                        .executes(c -> result(c.getSource(), ElectionService.close(c.getSource().getServer()), "Élection clôturée.")))
                .then(Commands.literal("annuler").requires(s -> s.hasPermission(2))
                        .executes(c -> result(c.getSource(), ElectionService.cancel(c.getSource().getServer()), "Élection annulée.")))
                .then(Commands.literal("candidat")
                        .executes(c -> result(c.getSource(), ElectionService.candidate(c.getSource().getPlayerOrException()), "Vous êtes candidat.")))
                .then(Commands.literal("retrait")
                        .executes(c -> result(c.getSource(), ElectionService.withdraw(c.getSource().getPlayerOrException()), "Candidature retirée.")))
                .then(Commands.literal("voter")
                        .then(Commands.argument("candidat", StringArgumentType.greedyString())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        EtatData.get(c.getSource().getServer()).candidates.values().stream().map(n -> n.contains(" ") ? n : n), b))
                                .executes(c -> result(c.getSource(), ElectionService.vote(c.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(c, "candidat")), "Vote enregistré, merci."))))
                .then(Commands.literal("liste").executes(c -> {
                    EtatData data = EtatData.get(c.getSource().getServer());
                    if (!data.electionOpen) return say(c.getSource(), "Aucune élection en cours.", false);
                    say(c.getSource(), "Élection en cours, fin dans " + ElectionService.duration(data.electionEnd - System.currentTimeMillis())
                            + " · " + data.votes.size() + " vote(s)", true);
                    if (data.candidates.isEmpty()) c.getSource().sendSystemMessage(Component.literal("§7Aucun candidat pour le moment."));
                    for (String n : data.candidates.values()) c.getSource().sendSystemMessage(Component.literal("§7 - §f" + n));
                    return 1;
                }));
    }

    // ------------------------------------------------------------------ /mairie

    private static LiteralArgumentBuilder<CommandSourceStack> mairie() {
        return Commands.literal("mairie")
                .then(Commands.literal("info").executes(c -> {
                    MinecraftServer s = c.getSource().getServer();
                    EtatData data = EtatData.get(s);
                    CommandSourceStack src = c.getSource();
                    src.sendSystemMessage(Component.literal("§6— Mairie —"));
                    if (data.mayor == null) src.sendSystemMessage(Component.literal("§7Maire : §fpersonne"));
                    else src.sendSystemMessage(Component.literal("§7Maire : §f" + MineNorth.displayName(s, data.mayor)
                            + (data.mayorUntil > 0 ? " §7(mandat : encore " + ElectionService.duration(data.mayorUntil - System.currentTimeMillis()) + ")" : "")));
                    src.sendSystemMessage(Component.literal("§7Impôt sur les achats : §f" + Etat.percent(Etat.taxPercent(s))));
                    src.sendSystemMessage(Component.literal("§7Élection : §f" + (data.electionOpen
                            ? "en cours, fin dans " + ElectionService.duration(data.electionEnd - System.currentTimeMillis()) : "aucune")));
                    return 1;
                }))
                .then(Commands.literal("agents").executes(c -> {
                    MinecraftServer s = c.getSource().getServer();
                    EtatData data = EtatData.get(s);
                    if (data.agents.isEmpty()) return say(c.getSource(), "Aucun agent municipal.", false);
                    for (Map.Entry<UUID, String> en : data.agents.entrySet())
                        c.getSource().sendSystemMessage(Component.literal("§7 - §f" + MineNorth.displayName(s, en.getKey()) + " §7(" + en.getValue() + ")"));
                    return 1;
                }));
    }

    // ------------------------------------------------------------------ /maire

    private static UUID agentByName(EtatData data, MinecraftServer s, String q) {
        String needle = q.trim().toLowerCase(Locale.ROOT);
        for (UUID id : data.agents.keySet()) {
            if (MineNorth.displayName(s, id).toLowerCase(Locale.ROOT).equals(needle) || MineNorth.pseudo(s, id).toLowerCase(Locale.ROOT).equals(needle)) return id;
        }
        return null;
    }

    private static List<String> agentNames(MinecraftServer s) {
        List<String> out = new ArrayList<>();
        for (UUID id : EtatData.get(s).agents.keySet()) out.add(MineNorth.displayName(s, id));
        return out;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> maire() {
        return Commands.literal("maire").requires(EtatCommands::mayorOrOp)
                .then(Commands.literal("tresor").executes(c -> {
                    treasuryReport(c.getSource());
                    return 1;
                }))
                .then(Commands.literal("impot")
                        .then(Commands.argument("pourcent", DoubleArgumentType.doubleArg(0, 100)).executes(c -> {
                            MinecraftServer s = c.getSource().getServer();
                            double p = DoubleArgumentType.getDouble(c, "pourcent");
                            double max = EtatConfig.get().impot_max_pourcent;
                            if (p > max && !c.getSource().hasPermission(2)) return say(c.getSource(), "Le maximum autorisé est " + Etat.percent(max) + ".", false);
                            EtatData data = EtatData.get(s);
                            data.taxOverride = p;
                            data.setDirty();
                            data.log("Impôt fixé à " + Etat.percent(p) + " par " + c.getSource().getTextName());
                            return say(c.getSource(), "Impôt sur les achats fixé à " + Etat.percent(p) + ".", true);
                        })))
                .then(Commands.literal("salaire")
                        .then(Commands.literal("liste").executes(c -> {
                            salaryReport(c.getSource());
                            return 1;
                        }))
                        .then(Commands.literal("police").then(Commands.argument("grade", IntegerArgumentType.integer(1, 20))
                                .then(Commands.argument("euros", DoubleArgumentType.doubleArg(0)).executes(c ->
                                        setSalary(c.getSource(), "police", IntegerArgumentType.getInteger(c, "grade") - 1, DoubleArgumentType.getDouble(c, "euros"))))))
                        .then(Commands.literal("pompier").then(Commands.argument("grade", IntegerArgumentType.integer(1, 20))
                                .then(Commands.argument("euros", DoubleArgumentType.doubleArg(0)).executes(c ->
                                        setSalary(c.getSource(), "pompier", IntegerArgumentType.getInteger(c, "grade") - 1, DoubleArgumentType.getDouble(c, "euros"))))))
                        .then(Commands.literal("agent").then(Commands.argument("euros", DoubleArgumentType.doubleArg(0)).executes(c ->
                                setSalary(c.getSource(), "agent", -1, DoubleArgumentType.getDouble(c, "euros"))))))
                .then(Commands.literal("agent")
                        .then(Commands.literal("nommer").then(Commands.argument("joueur", EntityArgument.player())
                                .executes(c -> appoint(c.getSource(), EntityArgument.getPlayer(c, "joueur"), "Agent municipal"))
                                .then(Commands.argument("titre", StringArgumentType.greedyString())
                                        .executes(c -> appoint(c.getSource(), EntityArgument.getPlayer(c, "joueur"), StringArgumentType.getString(c, "titre"))))))
                        .then(Commands.literal("revoquer").then(Commands.argument("nom", StringArgumentType.greedyString())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(agentNames(c.getSource().getServer()), b))
                                .executes(c -> {
                                    MinecraftServer s = c.getSource().getServer();
                                    EtatData data = EtatData.get(s);
                                    UUID id = agentByName(data, s, StringArgumentType.getString(c, "nom"));
                                    if (id == null) return say(c.getSource(), "Agent introuvable.", false);
                                    String name = MineNorth.displayName(s, id);
                                    data.agents.remove(id);
                                    data.log(name + " révoqué par " + c.getSource().getTextName());
                                    data.setDirty();
                                    ServerPlayer on = s.getPlayerList().getPlayer(id);
                                    if (on != null) on.sendSystemMessage(Component.literal("§e[Mairie] Vous n'êtes plus agent municipal."));
                                    return say(c.getSource(), name + " n'est plus agent municipal.", true);
                                })))
                        .then(Commands.literal("liste").executes(c -> {
                            MinecraftServer s = c.getSource().getServer();
                            EtatData data = EtatData.get(s);
                            if (data.agents.isEmpty()) return say(c.getSource(), "Aucun agent municipal.", false);
                            for (Map.Entry<UUID, String> en : data.agents.entrySet())
                                c.getSource().sendSystemMessage(Component.literal("§7 - §f" + MineNorth.displayName(s, en.getKey()) + " §7(" + en.getValue() + ")"));
                            return 1;
                        })))
                .then(Commands.literal("depenser").then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("euros", DoubleArgumentType.doubleArg(0.01))
                                .then(Commands.argument("motif", StringArgumentType.greedyString()).executes(c -> spend(c.getSource(),
                                        EntityArgument.getPlayer(c, "joueur"), DoubleArgumentType.getDouble(c, "euros"), StringArgumentType.getString(c, "motif")))))));
    }

    private static int appoint(CommandSourceStack src, ServerPlayer target, String title) {
        MinecraftServer s = src.getServer();
        EtatData data = EtatData.get(s);
        if (data.agents.containsKey(target.getUUID())) {
            data.agents.put(target.getUUID(), title.trim());
            data.setDirty();
            return say(src, "Titre de " + MineNorth.displayName(target) + " changé : " + title.trim() + ".", true);
        }
        if (data.agents.size() >= EtatConfig.get().agents_max) return say(src, "Nombre maximum d'agents atteint (" + EtatConfig.get().agents_max + ").", false);
        if (data.mayor != null && data.mayor.equals(target.getUUID())) return say(src, "Le maire ne peut pas être son propre agent.", false);
        data.agents.put(target.getUUID(), title.trim());
        data.log(MineNorth.displayName(target) + " nommé " + title.trim() + " par " + src.getTextName());
        data.setDirty();
        target.sendSystemMessage(Component.literal("§a[Mairie] Vous êtes nommé : " + title.trim() + ". Salaire : "
                + Etat.money(Etat.cents(Etat.salaryEuros(s, "agent", -1))) + " par paie."));
        return say(src, MineNorth.displayName(target) + " est nommé " + title.trim() + ".", true);
    }

    private static int setSalary(CommandSourceStack src, String kind, int grade, double euros) {
        MinecraftServer s = src.getServer();
        EtatConfig cfg = EtatConfig.get();
        if (euros > cfg.salaire_max_euros && !src.hasPermission(2))
            return say(src, "Le salaire maximum autorisé est " + Etat.money(Etat.cents(cfg.salaire_max_euros)) + ".", false);
        if (!kind.equals("agent")) {
            int n = kind.equals("police") ? MineNorth.police().grades().size() : SalaryService.secoursGrades().length;
            if (n == 0) return say(src, "Le mod " + (kind.equals("police") ? "Police" : "Secours") + " n'est pas installé.", false);
            if (grade < 0 || grade >= n) return say(src, "Grade inconnu (1 à " + n + ", 1 = le plus haut).", false);
        }
        EtatData data = EtatData.get(s);
        data.salaryOverride.put(Etat.salaryKey(kind, grade), euros);
        data.log("Salaire " + kind + (grade >= 0 ? " grade " + (grade + 1) : "") + " fixé à " + Etat.money(Etat.cents(euros)) + " par " + src.getTextName());
        data.setDirty();
        return say(src, "Salaire " + kind + (grade >= 0 ? " (grade " + (grade + 1) + ")" : "") + " : " + Etat.money(Etat.cents(euros)) + " par paie.", true);
    }

    private static void salaryReport(CommandSourceStack src) {
        MinecraftServer s = src.getServer();
        src.sendSystemMessage(Component.literal("§6— Salaires (toutes les " + EtatConfig.get().salaire_intervalle_minutes + " min) —"));
        List<String> pg = MineNorth.police().grades();
        for (int i = 0; i < pg.size(); i++)
            src.sendSystemMessage(Component.literal("§7Police " + (i + 1) + " · " + pg.get(i) + " : §f" + Etat.money(Etat.cents(Etat.salaryEuros(s, "police", i)))));
        String[] sg = SalaryService.secoursGrades();
        for (int i = 0; i < sg.length; i++)
            src.sendSystemMessage(Component.literal("§7Pompiers " + (i + 1) + " · " + sg[i] + " : §f" + Etat.money(Etat.cents(Etat.salaryEuros(s, "pompier", i)))));
        src.sendSystemMessage(Component.literal("§7Agents municipaux : §f" + Etat.money(Etat.cents(Etat.salaryEuros(s, "agent", -1)))));
    }

    private static void treasuryReport(CommandSourceStack src) {
        MinecraftServer s = src.getServer();
        EtatData data = EtatData.get(s);
        src.sendSystemMessage(Component.literal("§6— Trésor —"));
        src.sendSystemMessage(Component.literal("§7Solde : §f" + Etat.money(data.balance) + " §7· impôt : §f" + Etat.percent(Etat.taxPercent(s))));
        data.sources.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(8)
                .forEach(en -> src.sendSystemMessage(Component.literal("§7 - " + en.getKey() + " : §f" + Etat.money(en.getValue()))));
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");
        int from = Math.max(0, data.ledger.size() - 6);
        for (int i = data.ledger.size() - 1; i >= from; i--) {
            String[] parts = data.ledger.get(i).split("\\|", 2);
            try { src.sendSystemMessage(Component.literal("§8" + fmt.format(new Date(Long.parseLong(parts[0]))) + " §7" + (parts.length > 1 ? parts[1] : ""))); }
            catch (RuntimeException ex) { src.sendSystemMessage(Component.literal("§7" + data.ledger.get(i))); }
        }
    }

    private static int spend(CommandSourceStack src, ServerPlayer target, double euros, String reason) {
        MinecraftServer s = src.getServer();
        EtatData data = EtatData.get(s);
        long cents = Etat.cents(euros);
        BankService bank = MineNorth.bank();
        if (cents <= 0) return say(src, "Montant invalide.", false);
        if (data.balance < cents) return say(src, "Le trésor ne contient que " + Etat.money(data.balance) + ".", false);
        if (!bank.hasAccount(s, target.getUUID())) return say(src, MineNorth.displayName(target) + " n'a pas de compte bancaire.", false);
        if (!bank.refund(s, target.getUUID(), cents, "etat:depense")) return say(src, "Versement impossible.", false);
        data.balance -= cents;
        data.log(Etat.money(cents) + " versés à " + MineNorth.displayName(target) + " (" + reason + ") par " + src.getTextName());
        data.setDirty();
        target.sendSystemMessage(Component.literal("§a[État] Vous recevez " + Etat.money(cents) + " du trésor : " + reason));
        return say(src, Etat.money(cents) + " versés à " + MineNorth.displayName(target) + ". Reste : " + Etat.money(data.balance) + ".", true);
    }

    // ------------------------------------------------------------------ /etat (OP)

    private static LiteralArgumentBuilder<CommandSourceStack> etat() {
        return Commands.literal("etat").requires(s -> s.hasPermission(2))
                .then(Commands.literal("info").executes(c -> {
                    treasuryReport(c.getSource());
                    salaryReport(c.getSource());
                    return 1;
                }))
                .then(Commands.literal("reload").executes(c ->
                        EtatConfig.load() ? say(c.getSource(), "Config rechargée.", true) : say(c.getSource(), "Config illisible : ancienne conservée.", false)))
                .then(Commands.literal("maire")
                        .then(Commands.literal("aucun").executes(c -> {
                            ElectionService.setMayor(c.getSource().getServer(), null, "");
                            EtatData.get(c.getSource().getServer()).log("Maire retiré par " + c.getSource().getTextName());
                            return say(c.getSource(), "Il n'y a plus de maire.", true);
                        }))
                        .then(Commands.argument("joueur", EntityArgument.player()).executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "joueur");
                            ElectionService.setMayor(c.getSource().getServer(), p.getUUID(), MineNorth.displayName(p));
                            EtatData.get(c.getSource().getServer()).log(MineNorth.displayName(p) + " nommé maire par " + c.getSource().getTextName());
                            p.sendSystemMessage(Component.literal("§6[Mairie] Vous êtes maire. /maire pour gérer la ville."));
                            return say(c.getSource(), MineNorth.displayName(p) + " est maintenant maire.", true);
                        })))
                .then(Commands.literal("impot")
                        .then(Commands.literal("reset").executes(c -> {
                            EtatData data = EtatData.get(c.getSource().getServer());
                            data.taxOverride = -1;
                            data.setDirty();
                            return say(c.getSource(), "Impôt remis à " + Etat.percent(Etat.taxPercent(c.getSource().getServer())) + " (config).", true);
                        }))
                        .then(Commands.argument("pourcent", DoubleArgumentType.doubleArg(0, 100)).executes(c -> {
                            EtatData data = EtatData.get(c.getSource().getServer());
                            double p = DoubleArgumentType.getDouble(c, "pourcent");
                            data.taxOverride = p;
                            data.log("Impôt fixé à " + Etat.percent(p) + " par " + c.getSource().getTextName());
                            data.setDirty();
                            return say(c.getSource(), "Impôt fixé à " + Etat.percent(p) + ".", true);
                        })))
                .then(Commands.literal("tresor")
                        .then(Commands.literal("ajouter").then(Commands.argument("euros", DoubleArgumentType.doubleArg(0.01)).executes(c -> adjust(c.getSource(), DoubleArgumentType.getDouble(c, "euros")))))
                        .then(Commands.literal("retirer").then(Commands.argument("euros", DoubleArgumentType.doubleArg(0.01)).executes(c -> adjust(c.getSource(), -DoubleArgumentType.getDouble(c, "euros"))))));
    }

    private static int adjust(CommandSourceStack src, double euros) throws CommandSyntaxException {
        EtatData data = EtatData.get(src.getServer());
        long cents = Etat.cents(euros);
        data.balance += cents;
        data.log((cents >= 0 ? "Ajout de " : "Retrait de ") + Etat.money(Math.abs(cents)) + " par " + src.getTextName());
        data.setDirty();
        return say(src, "Solde du trésor : " + Etat.money(data.balance) + ".", true);
    }
}
