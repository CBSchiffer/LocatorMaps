package com.clokkworkk.locatormaps;

import com.mojang.datafixers.util.Pair;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
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
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import javax.swing.text.html.Option;
import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.item.FilledMapItem.getMapState;

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
            String dimensionKey = itemStack.getOrDefault(LocatorMaps.DIMENSION_KEY_COMPONENT, "minecraft:overworld");
            if(!world.getRegistryKey().getValue().toString().equals(dimensionKey)) {
                user.sendMessage(Text.of("§4Structure located in " + dimensionKey), true);
                return TypedActionResult.fail(itemStack);
            }
            String rawStructureKey = itemStack.get(LocatorMaps.STRUCTURE_KEY_COMPONENT);
            if(rawStructureKey == null) {
                user.sendMessage(Text.of("§4You need to set a structure first!"), true);
                return TypedActionResult.fail(itemStack);
            }
            boolean isTag = rawStructureKey.startsWith("#");
            Identifier structureId = Identifier.tryParse(isTag ? rawStructureKey.substring(1) : rawStructureKey);
            if(structureId != null) {
                ServerWorld serverWorld = (ServerWorld) world;
                RegistryEntryList<Structure> structureTargets;
                if(isTag) {
                    TagKey<Structure> structureTagKey = TagKey.of(RegistryKeys.STRUCTURE, structureId);
                    LocatorMaps.LOGGER.info("Created structure tag key: {}", structureTagKey.toString());
                    Optional<RegistryEntryList.Named<Structure>> tag = serverWorld.getRegistryManager()
                            .get(RegistryKeys.STRUCTURE)
                            .getEntryList(structureTagKey);
                    if(tag.isEmpty()) {
                        user.sendMessage(Text.of("§4Structure tag not found: " + structureId), true);
                        return TypedActionResult.fail(itemStack);
                    }
                    structureTargets = tag.get();
                } else {
                    Optional<RegistryEntry.Reference<Structure>> structureEntry = serverWorld.getRegistryManager()
                            .get(RegistryKeys.STRUCTURE)
                            .getEntry(structureId);
                    if(structureEntry.isEmpty()) {
                        user.sendMessage(Text.of("§4Structure not found: " + structureId), true);
                        return TypedActionResult.fail(itemStack);
                    }
                    structureTargets = RegistryEntryList.of(structureEntry.get());
                }

                int range = itemStack.getOrDefault(LocatorMaps.STRUCTURE_FINDER_RANGE_COMPONENT, 20000);
                boolean disallowDuplicateFinds = itemStack.getOrDefault(LocatorMaps.ALLOW_DUPLICATE_FINDS_COMPONENT, true);
                user.sendMessage(Text.of("Thinking..."), true);
                Pair<BlockPos, RegistryEntry<Structure>> strucPos = serverWorld.getChunkManager()
                        .getChunkGenerator()
                        .locateStructure(serverWorld, structureTargets, user.getBlockPos(), range, !disallowDuplicateFinds);
                if(strucPos != null) {
                    boolean showCoords = itemStack.getOrDefault(LocatorMaps.MAP_SHOW_COORDS_COMPONENT, true);
                    BlockPos pos = strucPos.getFirst();
                    ItemStack map = FilledMapItem.createMap(world, pos.getX(), pos.getZ(), (byte)2, true, true);
                    LocatorMaps.LOGGER.info("Map created at: {}. Map is: {}", pos, map);
                    if(itemStack.getOrDefault(LocatorMaps.DIMENSION_KEY_COMPONENT, "minecraft:overworld").equals("minecraft:overworld")) {
                        LocatorMaps.LOGGER.info("Using default map fill");
                        FilledMapItem.fillExplorationMap(serverWorld, map);
                    } else if(itemStack.getOrDefault(LocatorMaps.USE_ADVANCED_TERRAIN_COMPONENT, false)) {
                        LocatorMaps.LOGGER.info("Using custom map fill");
                        fillTerrain(serverWorld, map, user.getY() < 0 ? 0 : (int) user.getY());
                    }
                    MapState.addDecorationsNbt(map, pos, "§a" + structureId.getPath(), MapDecorationTypes.RED_X);


                    user.getWorld().playSoundFromEntity(null, user, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, user.getSoundCategory(), 1.0F, 1.0F);
                    user.getWorld().playSoundFromEntity(null, user, SoundEvents.BLOCK_PORTAL_AMBIENT, user.getSoundCategory(), 1.0F, 1.0F);
                    map.set(DataComponentTypes.ITEM_NAME, Text.of("§b" + getStructureName(Objects.requireNonNull(itemStack.get(LocatorMaps.STRUCTURE_KEY_COMPONENT))) + " §aMap"));

                    if(showCoords) {
                        user.sendMessage(Text.of("§aDone! §fLocated at: [§a" + pos.getX() + "§f, §a" + pos.getZ() + "§f]"), true);
                        LoreComponent lore = map.get(DataComponentTypes.LORE);
                        List<Text> loreList = new ArrayList<>();
                        LoreComponent existingLore = map.get(DataComponentTypes.LORE);
                        loreList.add(Text.of("§fLocated at: [§a" + pos.getX() + "§f, §a" + pos.getZ() + "§f]"));
                        String formattedDimKey = dimensionKey.substring(dimensionKey.indexOf(":") + 1)
                                .replace("_", " ")
                                .toLowerCase();
                        formattedDimKey = Arrays.stream(formattedDimKey.split(" "))
                                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                                .collect(Collectors.joining(" "));
                        switch (formattedDimKey) {
                            case "Overworld" -> formattedDimKey = "§aThe Overworld";
                            case "The Nether" -> formattedDimKey = "§cThe Nether";
                            case "The End" -> formattedDimKey = "§5The End";
                            case "The Aether" -> formattedDimKey = "§bThe Aether";
                            default -> formattedDimKey = "§3" + formattedDimKey;
                        }
                        loreList.add(Text.of("§fLocated in: §l" + formattedDimKey));
                        if (existingLore != null) {
                            loreList.addAll(existingLore.lines());
                        }
                        map.set(DataComponentTypes.LORE, new LoreComponent(loreList));
                    } else {
                        user.sendMessage(Text.of("§aDone!"), true);
                    }
                    itemStack.decrementUnlessCreative(1, user);

                    // Replace item in hand with map if it was consumed
                    return TypedActionResult.success(map, world.isClient());

                } else {
                    user.sendMessage(Text.of("§4Structure not found within acceptable range! Please ensure you are in the right dimension, or travel further."), true);
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
            return Text.of(getStructureName(structure) + " Locator Map");
        } else {
            return super.getName(stack);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        String structure = stack.get(LocatorMaps.STRUCTURE_KEY_COMPONENT);
        if (structure != null) {
            tooltip.add(Text.of("§aRight click to locate a §b" + getStructureName(structure) + "§a structure within §4" + stack.getOrDefault(LocatorMaps.STRUCTURE_FINDER_RANGE_COMPONENT, 20000) + " chunks!"));
            String dimensionKey = stack.getOrDefault(LocatorMaps.DIMENSION_KEY_COMPONENT, "minecraft:overworld");
            String formattedDimKey = dimensionKey.substring(dimensionKey.indexOf(":") + 1)
                    .replace("_", " ")
                    .toLowerCase();
            formattedDimKey = Arrays.stream(formattedDimKey.split(" "))
                    .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                    .collect(Collectors.joining(" "));
            switch (formattedDimKey) {
                case "Overworld" -> formattedDimKey = "§aThe Overworld";
                case "The Nether" -> formattedDimKey = "§cThe Nether";
                case "The End" -> formattedDimKey = "§5The End";
                case "The Aether" -> formattedDimKey = "§bThe Aether";
                default -> formattedDimKey = "§3" + formattedDimKey;
            }
            tooltip.add(Text.of("§fLocated in: §l" + formattedDimKey));
        } else {
            tooltip.add(Text.of("§4Invalid Structure key!"));
        }
    }

    private String getStructureName(String structure) {
        String structureName = structure.substring(structure.indexOf(":") + 1)
                .replace("_", " ")
                .toLowerCase();
        structureName = Arrays.stream(structureName.split(" "))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
        return structureName;
    }

//    private static void fillTerrain(ServerWorld world, ItemStack map) {
//        MapState mapState = getMapState(map, world);
//        if (mapState == null) return;
//
//        int scale = 1 << mapState.scale;
//        int centerX = mapState.centerX;
//        int centerZ = mapState.centerZ;
//        int startX = centerX / scale - 64;
//        int startZ = centerZ / scale - 64;
//
//        BlockPos.Mutable pos = new BlockPos.Mutable();
//
//        for (int dz = 0; dz < 128; dz++) {
//            for (int dx = 0; dx < 128; dx++) {
//                int worldX = (startX + dx) * scale;
//                int worldZ = (startZ + dz) * scale;
//
//                // Get chunk directly
//                Chunk chunk = world.getChunk(worldX >> 4, worldZ >> 4);
//                BlockState state = chunk.getBlockState(pos.set(worldX, 32, worldZ));
//
//                boolean isEmpty = state.isAir() || state.getFluidState().isStill();
//
//                byte color = isEmpty
//                        ? MapColor.LIGHT_GRAY.getRenderColorByte(MapColor.Brightness.LOW)
//                        : MapColor.GREEN.getRenderColorByte(MapColor.Brightness.NORMAL);
//
//                mapState.setColor(dx, dz, color);
//            }
//        }
//    }


    private static void fillTerrain(ServerWorld world, ItemStack map, int ypos) {
        MapState mapState = getMapState(map, world);
        if (mapState != null) {
            int mapScale = 1 << mapState.scale;
            int centerX = mapState.centerX;
            int centerZ = mapState.centerZ;
            boolean[] bls = new boolean[16384];
            int startX = centerX / mapScale - 64;
            int startZ = centerZ / mapScale - 64;
            BlockPos.Mutable mutable = new BlockPos.Mutable();
            boolean covered = false;
            boolean checked = false;
            BlockState state;

            for (int dz = 0; dz < 128; dz++) {
                for (int dx = 0; dx < 128; dx++) {
                    int worldX = (startX + dx) * mapScale;
                    int worldZ = (startZ + dz) * mapScale;
                    Chunk chunk = world.getChunk(worldX >> 4, worldZ >> 4);
                    if(!covered && !checked) {
                        state = chunk.getBlockState(mutable.set(worldX, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ), worldZ));
                        if(state.isOf(Blocks.BEDROCK)) {
                            covered = true;
                        }
                        checked = true;
                    }
                    if(covered) {
                        state = chunk.getBlockState(mutable.set(worldX, ypos, worldZ));
                    } else {
                        state = chunk.getBlockState(mutable.set(worldX, ypos, worldZ));
                    }

                    bls[dz * 128 + dx] = state.isAir() || state.getFluidState().isStill();
                    //LocatorMaps.LOGGER.info("BlockState at ({}, {}): {}", startX + dx, startZ + dz, state);
                }
            }

            for (int z = 1; z < 127; z++) {
                for (int x = 1; x < 127; x++) {
                    int neighbors = 0;

                    for (int dz = -1; dz < 2; dz++) {
                        for (int dx = -1; dx < 2; dx++) {
                            if ((dz != 0 || dx != 0) && isSeaOrSky(bls, z + dz, x + dx)) {
                                neighbors++;
                            }
                        }
                    }

                    MapColor.Brightness brightness = MapColor.Brightness.LOWEST;
                    MapColor mapColor = MapColor.CLEAR;
                    if (isSeaOrSky(bls, z, x)) {
                        mapColor = MapColor.ORANGE;
                        if (neighbors > 7 && x % 2 == 0) {
                            switch ((z + (int)(MathHelper.sin(x + 0.0F) * 7.0F)) / 8 % 5) {
                                case 0:
                                case 4:
                                    brightness = MapColor.Brightness.LOW;
                                    break;
                                case 1:
                                case 3:
                                    brightness = MapColor.Brightness.NORMAL;
                                    break;
                                case 2:
                                    brightness = MapColor.Brightness.HIGH;
                            }
                        } else if (neighbors > 7) {
                            mapColor = MapColor.CLEAR;
                        } else if (neighbors > 5) {
                            brightness = MapColor.Brightness.NORMAL;
                        } else if (neighbors > 3) {
                            brightness = MapColor.Brightness.LOW;
                        } else if (neighbors > 1) {
                            brightness = MapColor.Brightness.LOW;
                        }
                    } else if (neighbors > 0) {
                        mapColor = MapColor.BROWN;
                        if (neighbors > 3) {
                            brightness = MapColor.Brightness.NORMAL;
                        } else {
                            brightness = MapColor.Brightness.LOWEST;
                        }
                    }

                    if (mapColor != MapColor.CLEAR) {
                        mapState.setColor(z, x, mapColor.getRenderColorByte(brightness));
                    } else {
                        mapState.setColor(z, x, mapColor.getRenderColorByte(MapColor.Brightness.HIGH));
                    }
                }
            }
        } else {
            LocatorMaps.LOGGER.error("MapState is null for map: {}", map);
        }
    }

    private static boolean isSeaOrSky(boolean[] bls, int z, int x) {
        return bls[z * 128 + x];
    }


}
