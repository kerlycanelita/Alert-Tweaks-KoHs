package dev.zymekoh.alerttweaks.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Interface sounds of the settings screen, quiet and from the game's own set. */
public final class UiSound {
	private UiSound() {
	}

	private static void play(SoundEvent sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	public static void click() {
		play(SoundEvents.UI_BUTTON_CLICK.value(), 1.25F, 0.35F);
	}

	/** Changing page: the totem sweep. */
	public static void sweep() {
		play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.6F);
	}

	/** First opening of the session. */
	public static void entrance() {
		play(SoundEvents.TOTEM_USE, 1.6F, 0.22F);
		play(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.5F);
	}
}
