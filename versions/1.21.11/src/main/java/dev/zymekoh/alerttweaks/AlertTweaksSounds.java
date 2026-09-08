package dev.zymekoh.alerttweaks;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class AlertTweaksSounds {
	public static final SoundEvent HEARTBEAT = create("heartbeat");

	private AlertTweaksSounds() {
	}

	/** Touching the class is what forces the static initialiser to run. */
	public static void register() {
	}

	private static SoundEvent create(String name) {
		Identifier id = AlertTweaks.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
