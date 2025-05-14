package com.clokkworkk.locatormaps;

import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;

import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.UnaryOperator;

public class LocatorMaps implements ModInitializer {
	public static final String MOD_ID = "locatormaps";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final ComponentType<String> STRUCTURE_KEY_COMPONENT = register("structure_key", builder -> builder.codec(Codec.STRING));

	private static <T>ComponentType<T> register(String name, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
		return Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(MOD_ID, name), builderOperator.apply(ComponentType.builder()).build());
	}

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");
		LOGGER.info("Registering Locator Map Item...");
		Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "locator_map"), LocatorMapItem.INSTANCE);
	}
}