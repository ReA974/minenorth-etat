package fr.minenorth.etat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * config/minenorth_etat.json (serveur dédié uniquement : le client ne crée ni ne lit aucun fichier).
 * Valeurs de départ et plafonds. L'impôt et les salaires peuvent ensuite être changés en jeu
 * (maire, menu admin) : la valeur en jeu est sauvegardée avec le monde et passe avant celle-ci.
 */
public final class EtatConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static EtatConfig current = new EtatConfig();

    /** Impôt : part de CHAQUE achat (en %) qui va au trésor de l'État. */
    public double impot_pourcent = 2.0;
    /** Plafond que le maire ne peut pas dépasser (l'admin peut aller au-delà via /etat ou le menu admin). */
    public double impot_max_pourcent = 10.0;
    /** Salaire par grade, du plus haut (index 0) au plus bas, en euros par paie. */
    public double[] salaire_police_euros = {300, 200, 100};
    public double[] salaire_pompier_euros = {300, 200, 100};
    public double salaire_agent_euros = 100;
    /** Plafond de salaire que le maire peut fixer. */
    public double salaire_max_euros = 2000;
    /** Intervalle entre deux paies, en minutes. */
    public int salaire_intervalle_minutes = 60;
    /** Durée du mandat du maire, en jours (0 = illimité). */
    public int mandat_jours = 14;
    /** Durée par défaut d'une élection, en minutes. */
    public int election_duree_minutes = 1440;
    public int agents_max = 10;

    public static EtatConfig get() { return current; }

    public static boolean load() {
        // Config côté serveur uniquement : le client ne crée ni ne lit aucun fichier.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist != net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER) return true;
        Path f = FMLPaths.CONFIGDIR.get().resolve("minenorth_etat.json");
        boolean ok = true;
        try {
            if (Files.exists(f)) {
                EtatConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), EtatConfig.class);
                if (c != null) current = c;
            }
        } catch (Exception e) {
            ok = false;
        }
        EtatConfig d = new EtatConfig(), c = current;
        if (c.salaire_police_euros == null || c.salaire_police_euros.length == 0) c.salaire_police_euros = d.salaire_police_euros;
        if (c.salaire_pompier_euros == null || c.salaire_pompier_euros.length == 0) c.salaire_pompier_euros = d.salaire_pompier_euros;
        c.impot_max_pourcent = Math.max(0, Math.min(100, c.impot_max_pourcent));
        c.impot_pourcent = Math.max(0, Math.min(100, c.impot_pourcent));
        c.salaire_intervalle_minutes = Math.max(1, c.salaire_intervalle_minutes);
        c.agents_max = Math.max(0, c.agents_max);
        c.election_duree_minutes = Math.max(1, c.election_duree_minutes);
        // On ne réécrit pas un fichier illisible : l'admin peut ainsi corriger sa faute de frappe.
        if (ok) {
            try {
                Files.createDirectories(f.getParent());
                Files.writeString(f, GSON.toJson(c), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }
        return ok;
    }
}
