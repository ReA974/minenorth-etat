package fr.minenorth.etat.wallet;

import fr.minenorth.etat.wallet.WalletRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/** Interface du portefeuille : emplacements réservés aux billets, cartes et clés (tag « storable »). */
public class WalletMenu extends AbstractContainerMenu {
    public static final int COLS = 6, ROWS = 3, SIZE = COLS * ROWS;
    public static final int GRID_X = 35, GRID_Y = 34;
    public static final int INV_X = 8, INV_Y = 110, HOTBAR_Y = 168;

    private final InteractionHand hand;
    private final Player player;
    /** Pile EXACTE ouverte (serveur) : si le joueur change de case sélectionnée, jette ou remplace son portefeuille, le menu se ferme au lieu d'écrire dans un autre. */
    private final ItemStack walletStack;
    private boolean loading;
    private final ItemStackHandler handler = new ItemStackHandler(SIZE) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(WalletRegistry.STORABLE);
        }

        @Override
        protected void onContentsChanged(int slot) {
            save();
        }
    };

    /** Constructeur client (ouvert via NetworkHooks). */
    public WalletMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readEnum(InteractionHand.class));
    }

    public WalletMenu(int id, Inventory inv, InteractionHand hand) {
        super(WalletRegistry.WALLET_MENU.get(), id);
        this.hand = hand;
        this.player = inv.player;
        this.walletStack = player.getItemInHand(hand);

        // Chargement du contenu stocké dans l'item (sans déclencher de sauvegarde)
        ItemStack wallet = player.getItemInHand(hand);
        CompoundTag stored = wallet.getTag();
        if (stored != null && stored.contains("Items")) {
            CompoundTag tag = new CompoundTag();
            tag.put("Items", stored.getList("Items", 10));
            tag.putInt("Size", SIZE);
            loading = true;
            handler.deserializeNBT(tag);
            loading = false;
        }

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                addSlot(new SlotItemHandler(handler, c + r * COLS, GRID_X + c * 18, GRID_Y + r * 18));
            }
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, INV_X + c * 18, HOTBAR_Y));
        }
    }

    /** Le portefeuille ouvert ne doit pas pouvoir être déplacé (sinon son contenu serait perdu). */
    private boolean isWallet(Slot slot) {
        return slot.container == player.getInventory() && !slot.getItem().isEmpty()
                && slot.getItem() == player.getItemInHand(hand);
    }

    private void save() {
        if (loading || player.level().isClientSide) return;
        if (walletStack.is(WalletRegistry.WALLET.get()) && player.getItemInHand(hand) == walletStack) {
            walletStack.getOrCreateTag().put("Items", handler.serializeNBT().getList("Items", 10));
        }
    }

    /**
     * Le NBT du portefeuille fait foi, pas la copie en mémoire du menu : un distributeur, une boutique ou un autre mod peut avoir pris des
     * billets dans l'objet pendant que le menu était ouvert (un client modifié peut envoyer ces paquets sans fermer le menu). Avant chaque
     * clic, le contenu est relu depuis l'objet : un billet déjà pris ne peut plus être récupéré une seconde fois.
     */
    private void reloadFromItem() {
        CompoundTag stored = walletStack.getTag();
        CompoundTag tag = new CompoundTag();
        tag.put("Items", stored != null && stored.contains("Items") ? stored.getList("Items", 10) : new net.minecraft.nbt.ListTag());
        tag.putInt("Size", SIZE);
        loading = true;
        handler.deserializeNBT(tag);
        loading = false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player p) {
        if (!player.level().isClientSide) {
            if (!stillValid(p)) return;
            reloadFromItem();
        }
        // pas de prise du portefeuille ouvert (clic, touche numérique)
        if (slotId >= 0 && slotId < slots.size() && isWallet(slots.get(slotId))) return;
        if (type == ClickType.SWAP && button >= 0 && button < 9 && isWallet(slots.get(slots.size() - 9 + button))) return;
        super.clicked(slotId, button, type, p);
    }

    @Override
    public boolean stillValid(Player p) {
        ItemStack inHand = p.getItemInHand(hand);
        if (!inHand.is(WalletRegistry.WALLET.get())) return false;
        return p.level().isClientSide || inHand == walletStack;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || isWallet(slot)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < SIZE) {
            if (!moveItemStackTo(stack, SIZE, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!stack.is(WalletRegistry.STORABLE) || !moveItemStackTo(stack, 0, SIZE, false)) {
            return ItemStack.EMPTY;
        }
        slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(p, stack);
        return original;
    }
}
