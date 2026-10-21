package dev.zymekoh.alerttweaks.client.alert;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** A short list of vanilla note sounds, so a cycle button is enough to choose one. */
public enum AlertSound {
	NONE(null, 1.0F),
	ALARM(SoundEvents.NOTE_BLOCK_BELL, 1.35F),
	BELL(SoundEvents.NOTE_BLOCK_BELL, 1.0F),
	PLING(SoundEvents.NOTE_BLOCK_PLING, 1.25F),
	CHIME(SoundEvents.NOTE_BLOCK_CHIME, 1.4F),
	BASS(SoundEvents.NOTE_BLOCK_BASS, 0.8F),
	CHIRP(SoundEvents.NOTE_BLOCK_BIT, 1.6F);

	private final Holder<SoundEvent> sound;
	private final float pitch;

	AlertSound(Holder<SoundEvent> sound, float pitch) {
		this.sound = sound;
		this.pitch = pitch;
	}

	public Component label() {
		return Component.translatable("alert_tweaks.sound." + this.name().toLowerCase(Locale.ROOT));
	}

	public AlertSound next() {
		AlertSound[] all = values();
		return all[(this.ordinal() + 1) % all.length];
	}

	public static AlertSound parse(String name, AlertSound fallback) {
		if (name != null) {
			for (AlertSound sound : values()) {
				if (sound.name().equalsIgnoreCase(name)) {
					return sound;
				}
			}
		}
		return fallback;
	}

	/** ALARM is the same note played twice; the second one is queued by {@link AlertFeed}. */
	public void play(float volume) {
		if (this.sound == null || volume <= 0.0F) {
			return;
		}
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(this.sound.value(), this.pitch, volume));
	}

	public boolean repeats() {
		return this == ALARM;
	}
}
