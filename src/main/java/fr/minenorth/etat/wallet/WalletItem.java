package fr.minenorth.etat.wallet;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Portefeuille : clic droit pour l'ouvrir. Le contenu est stocké dans le NBT de l'item (clé « Items »). */
public class WalletItem extends Item {
    public WalletItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp,
                    new SimpleMenuProvider((id, inv, p) -> new WalletMenu(id, inv, hand), stack.getHoverName()),
                    buf -> buf.writeEnum(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
        int count = 0;
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            ListTag list = tag.getList("Items", 10);
            for (int i = 0; i < list.size(); i++) count += list.getCompound(i).getByte("Count");
        }
        tips.add(Component.translatable("tooltip.minenorthetat.contents", count).withStyle(ChatFormatting.GRAY));
        tips.add(Component.translatable("tooltip.minenorthetat.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
