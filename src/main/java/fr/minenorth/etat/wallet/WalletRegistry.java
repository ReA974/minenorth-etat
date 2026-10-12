package fr.minenorth.etat.wallet;

import fr.minenorth.etat.MineNorthEtat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Portefeuille (intégré à l'État) : objet + menu. L'objet est ajouté à l'onglet créatif de l'État. */
public final class WalletRegistry {
    private WalletRegistry() {}

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MineNorthEtat.MOD_ID);

    /** Items rangeables dans le portefeuille (modifiable par datapack : data/minenorthetat/tags/items/storable.json). */
    public static final TagKey<Item> STORABLE = TagKey.create(Registries.ITEM, new ResourceLocation(MineNorthEtat.MOD_ID, "storable"));

    public static final RegistryObject<Item> WALLET = fr.minenorth.etat.block.ModBlocks.ITEMS.register("wallet", WalletItem::new);

    public static final RegistryObject<MenuType<WalletMenu>> WALLET_MENU = MENUS.register("wallet",
            () -> IForgeMenuType.create(WalletMenu::new));
}
