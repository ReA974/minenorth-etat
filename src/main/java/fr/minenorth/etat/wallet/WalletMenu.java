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
        ItemStack wallet = player.getItemInHand(hand);
        if (wallet.is(WalletRegistry.WALLET.get())) {
            wallet.getOrCreateTag().put("Items", handler.serializeNBT().getList("Items", 10));
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player p) {
        // pas de prise du portefeuille ouvert (clic, touche numérique)
        if (slotId >= 0 && slotId < slots.size() && isWallet(slots.get(slotId))) return;
        if (type == ClickType.SWAP && button >= 0 && button < 9 && isWallet(slots.get(slots.size() - 9 + button))) return;
        super.clicked(slotId, button, type, p);
    }

    @Override
    public boolean stillValid(Player p) {
        return p.getItemInHand(hand).is(WalletRegistry.WALLET.get());
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
