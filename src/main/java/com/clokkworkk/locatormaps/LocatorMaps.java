package com.clokkworkk.locatormaps;

import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;

import net.minecraft.block.MapColor;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.UnaryOperator;

public class LocatorMaps implements ModInitializer {
	public static final String MOD_ID = "locatormaps";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final ComponentType<String> STRUCTURE_KEY_COMPONENT = register("structure_key", builder -> builder.codec(Codec.STRING));
	public static final ComponentType<Integer> STRUCTURE_FINDER_RANGE_COMPONENT = register("locator_range", builder -> builder.codec(Codec.INT));
	public static final ComponentType<Boolean> MAP_SHOW_COORDS_COMPONENT = register("map_show_coords", builder -> builder.codec(Codec.BOOL));
	public static final ComponentType<Boolean> ALLOW_DUPLICATE_FINDS_COMPONENT = register("allow_duplicate_finds", builder -> builder.codec(Codec.BOOL));
	public static final ComponentType<String> DIMENSION_KEY_COMPONENT = register("dimension_key", builder -> builder.codec(Codec.STRING));

	private static <T>ComponentType<T> register(String name, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
		return Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(MOD_ID, name), builderOperator.apply(ComponentType.builder()).build());
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Registering Locator Map Item...");
		Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "locator_map"), LocatorMapItem.INSTANCE);
	}
}
