package com.clokkworkk.locatormaps;

import com.mojang.datafixers.util.Pair;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.item.map.MapDecoration;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.item.map.MapState;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LocatorMapItem extends NetworkSyncedItem {
    public static final LocatorMapItem INSTANCE = new LocatorMapItem(new Settings().rarity(Rarity.RARE).maxCount(1));


    public LocatorMapItem(Settings settings) {
        super(settings);

    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(itemStack);
        } else {
            if(itemStack.get(LocatorMaps.STRUCTURE_KEY_COMPONENT) == null) {
                user.sendMessage(Text.of("§4You need to set a structure first!"), true);
            } else {
                Identifier structureId = Identifier.tryParse(Objects.requireNonNull(itemStack.get(LocatorMaps.STRUCTURE_KEY_COMPONENT)));
                if(structureId != null) {
                    ServerWorld serverWorld = (ServerWorld) world;
                    Optional<RegistryEntry.Reference<Structure>> structureEntry = serverWorld.getRegistryManager()
                            .get(RegistryKeys.STRUCTURE)
                            .getEntry(structureId);
                    if(structureEntry.isPresent()) {
                        user.sendMessage(Text.of("Thinking..."), true);
                        Pair<BlockPos, RegistryEntry<Structure>> strucPos = serverWorld.getChunkManager()
                                .getChunkGenerator()
                                .locateStructure(serverWorld, RegistryEntryList.of(structureEntry.get()), user.getBlockPos(), 20000, false);
                        if(strucPos != null) {
                            BlockPos pos = strucPos.getFirst();
                            ItemStack map = FilledMapItem.createMap(world, pos.getX(), pos.getZ(), (byte)2, true, true);
                            MapState.addDecorationsNbt(map, pos, "§a" + structureId.getPath(), MapDecorationTypes.RED_X);
                            itemStack.decrementUnlessCreative(1, user);
                            user.getWorld().playSoundFromEntity(null, user, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, user.getSoundCategory(), 1.0F, 1.0F);
                            user.getWorld().playSoundFromEntity(null, user, SoundEvents.BLOCK_PORTAL_AMBIENT, user.getSoundCategory(), 1.0F, 1.0F);
                            user.sendMessage(Text.of("§aDone! Located at §b" + pos.getX() + "§a, §b" + pos.getZ()), true);
                            map.set(DataComponentTypes.ITEM_NAME, Text.translatable("structure." + structureId.getNamespace() + "." + structureId.getPath() + " Map"));
                            if (itemStack.isEmpty()) {
                                return TypedActionResult.consume(map);
                            } else {
                                if (!user.getInventory().insertStack(map.copy())) {
                                    user.dropItem(map, false);
                                }
                                return TypedActionResult.consume(itemStack);
                            }
                        } else {
                            user.sendMessage(Text.of("§4Structure not found within acceptable range!"), true);
                            return TypedActionResult.fail(itemStack);
                        }
                    } else {
                        user.sendMessage(Text.of("§4Invalid Structure key!"), true);
                        return TypedActionResult.fail(itemStack);
                    }
                } else {
                    user.sendMessage(Text.of("§4You need to set a structure first!"), true);
                    return TypedActionResult.fail(itemStack);
                }
            }
        }
        return TypedActionResult.fail(itemStack);
    }

    @Override
    public Text getName(ItemStack stack) {
        String structure = stack.get(LocatorMaps.STRUCTURE_KEY_COMPONENT);
        if (structure != null) {
            return Text.of(structure + " Locator Map");
        } else {
            return super.getName(stack);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        String structure = stack.get(LocatorMaps.STRUCTURE_KEY_COMPONENT);
        if (structure != null) {
            tooltip.add(Text.of("§aRight click to locate a §b" + structure));
        } else {
            tooltip.add(Text.of("§4Invalid Structure key!"));
        }
    }




}
