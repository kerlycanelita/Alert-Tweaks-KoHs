package dev.zymekoh.alerttweaks.client.hud;

import dev.zymekoh.alerttweaks.AlertTweaksSounds;
import dev.zymekoh.alerttweaks.client.config.AlertTweaksConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;

public final class HeartbeatSound {
	private static final int PREVIEW_BEATS = 4;

	private static int ticksUntilNextBeat;
	private static int previewBeatsRemaining;
	private static int previewTicksUntilNextBeat;

	private HeartbeatSound() {
	}

	public static void tick(Minecraft minecraft) {
		AlertTweaksConfig config = AlertTweaksConfig.get();
		if (tickPreview(minecraft, config)) {
			return;
		}
		float pressure = getLowHealthPressure(minecraft, config);
		if (pressure <= 0.0F || minecraft.isPaused()) {
			ticksUntilNextBeat = 0;
		} else if (ticksUntilNextBeat > 0) {
			ticksUntilNextBeat--;
		} else {
			playBeat(minecraft, config, pressure);
			ticksUntilNextBeat = getNextBeatDelay(config, pressure);
		}
	}

	public static void startPreview() {
		previewBeatsRemaining = PREVIEW_BEATS;
		previewTicksUntilNextBeat = 0;
	}

	public static void stopPreview() {
		previewBeatsRemaining = 0;
		previewTicksUntilNextBeat = 0;
	}

	private static boolean tickPreview(Minecraft minecraft, AlertTweaksConfig config) {
		if (previewBeatsRemaining <= 0) {
			return false;
		}
		if (!config.heartbeatSoundEnabled) {
			stopPreview();
			return false;
		}
		if (previewTicksUntilNextBeat > 0) {
			previewTicksUntilNextBeat--;
			return true;
		}
		float previewPressure = config.heartbeatSoundScaleWithLowHealth ? 0.75F : 0.0F;
		playBeat(minecraft, config, previewPressure);
		previewBeatsRemaining--;
		previewTicksUntilNextBeat = getNextBeatDelay(config, previewPressure);
		return true;
	}

	private static float getLowHealthPressure(Minecraft minecraft, AlertTweaksConfig config) {
		LocalPlayer player = minecraft.player;
		if (!config.heartbeatSoundEnabled || player == null || player.isCreative() || player.isSpectator() || !player.isAlive()) {
			return 0.0F;
		}
		float maxHealth = Math.max(1.0F, player.getMaxHealth());
		float threshold = Math.min(config.thresholdHealthPoints, maxHealth);
		float health = Mth.clamp(player.getHealth(), 0.0F, maxHealth);
		return health > threshold ? 0.0F : Mth.clamp((threshold - health) / threshold, 0.0F, 1.0F);
	}

	private static void playBeat(Minecraft minecraft, AlertTweaksConfig config, float pressure) {
		float intensity = getIntensityFactor(config);
		float volume = Mth.clamp(config.heartbeatSoundVolume, 0.0F, 1.0F);
		float healthPitch = config.heartbeatSoundScaleWithLowHealth ? pressure * 0.12F : 0.0F;
		float pitch = Mth.clamp(Mth.lerp(intensity, 0.9F, 1.14F) + healthPitch, 0.75F, 1.35F);
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(AlertTweaksSounds.HEARTBEAT, pitch, volume));
	}

	private static int getNextBeatDelay(AlertTweaksConfig config, float pressure) {
		float intensity = getIntensityFactor(config);
		float baseDelay = Mth.lerp(intensity, 34.0F, 14.0F);
		if (!config.heartbeatSoundScaleWithLowHealth) {
			return Mth.clamp(Math.round(baseDelay), 8, 44);
		}
		float healthMultiplier = Mth.lerp(pressure, 1.0F, 0.46F);
		return Mth.clamp(Math.round(baseDelay * healthMultiplier), 7, 44);
	}

	private static float getIntensityFactor(AlertTweaksConfig config) {
		return Mth.clamp((config.heartbeatSoundIntensity - 0.5F) / 1.5F, 0.0F, 1.0F);
	}
}
