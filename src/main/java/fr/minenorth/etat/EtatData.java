package fr.minenorth.etat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Trésor, maire, élection, agents et réglages en jeu. Sauvegardé avec le monde. */
public class EtatData extends SavedData {
    private static final String NAME = "minenorth_etat";

    /** Solde dépensable du trésor, en centimes. */
    public long balance;
    /** Revenus d'impôt par source (la part prélevée, en centimes). */
    public final Map<String, Long> sources = new LinkedHashMap<>();
    /** Dernières opérations, "horodatage|texte", les plus anciennes d'abord. */
    public final List<String> ledger = new ArrayList<>();

    public UUID mayor;
    public String mayorName = "";
    /** Fin du mandat (ms), 0 = illimité. */
    public long mayorUntil;
    public final Map<UUID, String> agents = new LinkedHashMap<>();

    /** Pouvoirs du maire suspendus par un admin (il garde son titre mais ses commandes sont bloquées). */
    public boolean mayorLocked;

    public boolean electionOpen;
    public long electionEnd;
    public final Map<UUID, String> candidates = new LinkedHashMap<>();
    /** Votant -> candidat. */
    public final Map<UUID, UUID> votes = new LinkedHashMap<>();

    /** Réglages en jeu ; absents = valeur de la config. */
    public double taxOverride = -1;
    public final Map<String, Double> salaryOverride = new LinkedHashMap<>();
    public long lastPay;

    /** Lois promulguées par le maire (les plus anciennes d'abord). */
    public final List<Law> laws = new ArrayList<>();
    public int lawCounter;

    public record Law(int num, String title, String text, String author, long date) {}

    public static EtatData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(EtatData::load, EtatData::new, NAME);
    }

    public void log(String text) {
        ledger.add(System.currentTimeMillis() + "|" + text);
        while (ledger.size() > 60) ledger.remove(0);
        setDirty();
    }

    public void addSource(String source, long cents) {
        sources.merge(source, cents, Long::sum);
        setDirty();
    }

    public static EtatData load(CompoundTag tag) {
        EtatData d = new EtatData();
        d.balance = tag.getLong("balance");
        for (Tag t : tag.getList("sources", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.sources.put(c.getString("k"), c.getLong("v"));
        }
        for (Tag t : tag.getList("ledger", Tag.TAG_STRING)) d.ledger.add(t.getAsString());
        if (tag.hasUUID("mayor")) d.mayor = tag.getUUID("mayor");
        d.mayorName = tag.getString("mayorName");
        d.mayorUntil = tag.getLong("mayorUntil");
        for (Tag t : tag.getList("agents", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.agents.put(c.getUUID("id"), c.getString("title"));
        }
        d.mayorLocked = tag.getBoolean("mayorLocked");
        d.electionOpen = tag.getBoolean("electionOpen");
        d.electionEnd = tag.getLong("electionEnd");
        for (Tag t : tag.getList("candidates", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.candidates.put(c.getUUID("id"), c.getString("name"));
        }
        for (Tag t : tag.getList("votes", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.votes.put(c.getUUID("voter"), c.getUUID("for"));
        }
        d.taxOverride = tag.contains("taxOverride") ? tag.getDouble("taxOverride") : -1;
        for (Tag t : tag.getList("salaries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.salaryOverride.put(c.getString("k"), c.getDouble("v"));
        }
        d.lastPay = tag.getLong("lastPay");
        d.lawCounter = tag.getInt("lawCounter");
        for (Tag t : tag.getList("laws", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.laws.add(new Law(c.getInt("num"), c.getString("title"), c.getString("text"), c.getString("author"), c.getLong("date")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("balance", balance);
        ListTag sl = new ListTag();
        sources.forEach((k, v) -> { CompoundTag c = new CompoundTag(); c.putString("k", k); c.putLong("v", v); sl.add(c); });
        tag.put("sources", sl);
        ListTag ll = new ListTag();
        for (String line : ledger) ll.add(StringTag.valueOf(line));
        tag.put("ledger", ll);
        if (mayor != null) tag.putUUID("mayor", mayor);
        tag.putString("mayorName", mayorName);
        tag.putLong("mayorUntil", mayorUntil);
        ListTag al = new ListTag();
        agents.forEach((id, title) -> { CompoundTag c = new CompoundTag(); c.putUUID("id", id); c.putString("title", title); al.add(c); });
        tag.put("agents", al);
        tag.putBoolean("mayorLocked", mayorLocked);
        tag.putBoolean("electionOpen", electionOpen);
        tag.putLong("electionEnd", electionEnd);
        ListTag cl = new ListTag();
        candidates.forEach((id, n) -> { CompoundTag c = new CompoundTag(); c.putUUID("id", id); c.putString("name", n); cl.add(c); });
        tag.put("candidates", cl);
        ListTag vl = new ListTag();
        votes.forEach((v, f) -> { CompoundTag c = new CompoundTag(); c.putUUID("voter", v); c.putUUID("for", f); vl.add(c); });
        tag.put("votes", vl);
        if (taxOverride >= 0) tag.putDouble("taxOverride", taxOverride);
        ListTag sal = new ListTag();
        salaryOverride.forEach((k, v) -> { CompoundTag c = new CompoundTag(); c.putString("k", k); c.putDouble("v", v); sal.add(c); });
        tag.put("salaries", sal);
        tag.putLong("lastPay", lastPay);
        tag.putInt("lawCounter", lawCounter);
        ListTag lw = new ListTag();
        for (Law l : laws) {
            CompoundTag c = new CompoundTag();
            c.putInt("num", l.num()); c.putString("title", l.title()); c.putString("text", l.text());
            c.putString("author", l.author()); c.putLong("date", l.date());
            lw.add(c);
        }
        tag.put("laws", lw);
        return tag;
    }
}
