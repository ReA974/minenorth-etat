package fr.minenorth.etat.network;

import fr.minenorth.api.MineNorth;
import fr.minenorth.etat.ElectionService;
import fr.minenorth.etat.Etat;
import fr.minenorth.etat.EtatData;
import fr.minenorth.etat.MineNorthEtat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Écran du bureau de vote : l'état de l'élection descend vers le joueur, ses choix remontent. */
public final class EtatNetwork {
    private EtatNetwork() {}

    public static final int A_REFRESH = 0, A_CANDIDATE = 1, A_WITHDRAW = 2, A_VOTE = 3, A_CLOSE = 4;
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineNorthEtat.MOD_ID, "network"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int id = 0;

    /** Joueurs qui ont ouvert l'écran en cliquant un bureau de vote : seuls eux peuvent envoyer des actions. */
    private static final Set<UUID> OPEN = new HashSet<>();

    public static void register() {
        CHANNEL.registerMessage(id++, StatePacket.class, StatePacket::encode, StatePacket::decode, StatePacket::handle);
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode, ActionPacket::handle);
    }

    /** Ouvre l'écran du bureau de vote chez ce joueur. */
    public static void open(ServerPlayer p) {
        OPEN.add(p.getUUID());
        sendState(p, "", true);
    }

    public static void forget(UUID id) { OPEN.remove(id); }

    public static void sendState(ServerPlayer p, String message, boolean ok) {
        EtatData d = EtatData.get(p.server);
        List<Candidate> cands = new ArrayList<>();
        UUID myVote = d.votes.get(p.getUUID());
        for (Map.Entry<UUID, String> en : d.candidates.entrySet()) cands.add(new Candidate(en.getKey(), en.getValue(), en.getKey().equals(myVote)));
        int seconds = d.electionOpen ? (int) Math.max(0, (d.electionEnd - System.currentTimeMillis()) / 1000) : 0;
        String mayor = d.mayor == null ? "" : MineNorth.displayName(p.server, d.mayor);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new StatePacket(d.electionOpen, seconds, mayor,
                Etat.citizen(p.server, p.getUUID()), myVote != null, d.candidates.containsKey(p.getUUID()), cands, message, ok));
    }

    public record Candidate(UUID id, String name, boolean mine) {
        static void encode(FriendlyByteBuf b, Candidate c) { b.writeUUID(c.id); b.writeUtf(c.name); b.writeBoolean(c.mine); }
        static Candidate decode(FriendlyByteBuf b) { return new Candidate(b.readUUID(), b.readUtf(), b.readBoolean()); }
    }

    public record StatePacket(boolean open, int secondsLeft, String mayor, boolean canVote, boolean hasVoted, boolean isCandidate,
                              List<Candidate> candidates, String message, boolean ok) {
        static void encode(StatePacket p, FriendlyByteBuf b) {
            b.writeBoolean(p.open); b.writeVarInt(p.secondsLeft); b.writeUtf(p.mayor); b.writeBoolean(p.canVote);
            b.writeBoolean(p.hasVoted); b.writeBoolean(p.isCandidate);
            b.writeCollection(p.candidates, Candidate::encode);
            b.writeUtf(p.message); b.writeBoolean(p.ok);
        }
        static StatePacket decode(FriendlyByteBuf b) {
            return new StatePacket(b.readBoolean(), b.readVarInt(), b.readUtf(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                    b.readList(Candidate::decode), b.readUtf(), b.readBoolean());
        }
        static void handle(StatePacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> fr.minenorth.etat.client.ClientNetworkHandler.state(p)));
            c.get().setPacketHandled(true);
        }
    }

    public record ActionPacket(int action, UUID target) {
        static void encode(ActionPacket p, FriendlyByteBuf b) { b.writeVarInt(p.action); b.writeUUID(p.target); }
        static ActionPacket decode(FriendlyByteBuf b) { return new ActionPacket(b.readVarInt(), b.readUUID()); }
        static void handle(ActionPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer sender = c.get().getSender();
                if (sender != null) EtatNetwork.handle(sender, p);
            });
            c.get().setPacketHandled(true);
        }
    }

    private static void handle(ServerPlayer p, ActionPacket k) {
        if (k.action() == A_CLOSE) { OPEN.remove(p.getUUID()); return; }
        if (!OPEN.contains(p.getUUID())) return;
        String error = null;
        String done = "";
        switch (k.action()) {
            case A_CANDIDATE -> { error = ElectionService.candidate(p); done = "Vous êtes candidat."; }
            case A_WITHDRAW -> { error = ElectionService.withdraw(p); done = "Candidature retirée."; }
            case A_VOTE -> { error = ElectionService.voteFor(p, k.target()); done = "Vote enregistré, merci."; }
            default -> {}
        }
        sendState(p, error != null ? error : done, error == null);
    }
}
