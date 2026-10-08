package fr.minenorth.etat.block;

import fr.minenorth.etat.MineNorthEtat;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MineNorthEtat.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MineNorthEtat.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineNorthEtat.MOD_ID);

    public static final RegistryObject<Block> BALLOT = BLOCKS.register("bureau_de_vote", BallotBlock::new);
    public static final RegistryObject<Item> BALLOT_ITEM = ITEMS.register("bureau_de_vote",
            () -> new BlockItem(BALLOT.get(), new Item.Properties()));

    public static final RegistryObject<Item> TABLET = ITEMS.register("tablette_mairie", fr.minenorth.etat.item.TabletItem::new);

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("etat", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.minenorthetat"))
            .icon(() -> new ItemStack(TABLET.get()))
            .displayItems((params, out) -> {
                out.accept(TABLET.get());
                out.accept(BALLOT_ITEM.get());
            })
            .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
