package com.squidgame;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SquidGameMod.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SquidGameMod.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SquidGameMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SquidGameMod.MODID);

    public static final RegistryObject<Block> BLOCK_ONE = BLOCKS.register("block_one",
            () -> new LineBlock(1, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> BLOCK_TWO = BLOCKS.register("block_two",
            () -> new LineBlock(2, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN).strength(1.5F, 6.0F).sound(SoundType.STONE)));

    public static final RegistryObject<Block> BRIDGE_GLASS = BLOCKS.register("bridge_glass",
            () -> new BridgeGlassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE).strength(0.3F).sound(SoundType.GLASS).noOcclusion().noLootTable()
                    .isValidSpawn((s, g, p, e) -> false)
                    .isRedstoneConductor((s, g, p) -> false)
                    .isSuffocating((s, g, p) -> false)
                    .isViewBlocking((s, g, p) -> false)));

    public static final RegistryObject<Item> BRIDGE_GLASS_ITEM = ITEMS.register("bridge_glass",
            () -> new BridgeGlassItem(new Item.Properties()));
    public static final RegistryObject<Item> BRIDGE_WAND = ITEMS.register("bridge_wand",
            () -> new BridgeWandItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> BLOCK_ONE_ITEM = ITEMS.register("block_one",
            () -> new BlockItem(BLOCK_ONE.get(), new Item.Properties()));
    public static final RegistryObject<Item> BLOCK_TWO_ITEM = ITEMS.register("block_two",
            () -> new BlockItem(BLOCK_TWO.get(), new Item.Properties()));

    public static final RegistryObject<Item> HEADPHONES = ITEMS.register("headphones",
            () -> new HeadphonesItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<SoundEvent> GREEN_LIGHT = sound("green_light");
    public static final RegistryObject<SoundEvent> RED_LIGHT = sound("red_light");
    public static final RegistryObject<SoundEvent> GUNSHOT = sound("gunshot");
    public static final RegistryObject<SoundEvent> SUCCESS = sound("success");

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("squidgame",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.squidgame"))
                    .icon(() -> new ItemStack(BLOCK_TWO_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(BLOCK_ONE_ITEM.get());
                        output.accept(BLOCK_TWO_ITEM.get());
                        output.accept(HEADPHONES.get());
                        output.accept(BRIDGE_GLASS_ITEM.get());
                        output.accept(BRIDGE_WAND.get());
                    })
                    .build());

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SquidGameMod.MODID, name)));
    }
}
