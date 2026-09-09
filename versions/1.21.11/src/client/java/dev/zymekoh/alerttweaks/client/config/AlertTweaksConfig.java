package dev.zymekoh.alerttweaks.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.zymekoh.alerttweaks.AlertTweaks;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

public final class AlertTweaksConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("alert_tweaks.json");
	/** Settings from the mod this one replaces are imported once, so nobody has to redo them. */
	private static final Path LEGACY_PATH = FabricLoader.getInstance().getConfigDir().resolve("healt_tweaks.json");

	private static AlertTweaksConfig instance = new AlertTweaksConfig();

	public boolean vignetteEnabled = true;
	public float thresholdHealthPoints = 10.0F;
	public int vignetteRed = 102;
	public int vignetteGreen = 26;
	public int vignetteBlue = 160;
	public int vignetteAlpha = 190;
	public float vignetteSize = 1.0F;
	public boolean smoothVignetteEnabled = true;
	public boolean staticColorEnabled = false;
	public float staticColorIntensity = 0.65F;
	public boolean heartbeatEnabled = false;
	public boolean heartbeatScaleWithLowHealth = true;
	public boolean heartbeatSoundEnabled = true;
	public boolean heartbeatSoundScaleWithLowHealth = true;
	public float heartbeatSoundVolume = 0.65F;
	public float heartbeatSoundIntensity = 1.0F;

	public boolean threatIndicatorEnabled = true;
	public boolean threatAdaptiveScale = true;
	public float threatScale = 1.0F;
	/** 0 puts the arrows right next to the crosshair, 1 pushes them out to the edge of the ring. */
	public float threatDistance = 0.30F;

	public static AlertTweaksConfig get() {
		return instance;
	}

	public static void load() {
		Path source = Files.exists(CONFIG_PATH) ? CONFIG_PATH : LEGACY_PATH;
		if (!Files.exists(source)) {
			instance = new AlertTweaksConfig();
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(source)) {
			AlertTweaksConfig loaded = GSON.fromJson(reader, AlertTweaksConfig.class);
			instance = loaded == null ? new AlertTweaksConfig() : loaded;
			instance.validate();
		} catch (IOException | RuntimeException exception) {
			AlertTweaks.LOGGER.warn("Could not load the Alert Tweaks config, using defaults.", exception);
			instance = new AlertTweaksConfig();
		}
		if (source == LEGACY_PATH) {
			AlertTweaks.LOGGER.info("Imported the previous healt_tweaks.json settings.");
			save();
		}
	}

	public static void save() {
		instance.validate();
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException exception) {
			AlertTweaks.LOGGER.warn("Could not save the Alert Tweaks config.", exception);
		}
	}

	public void reset() {
		AlertTweaksConfig defaults = new AlertTweaksConfig();
		this.vignetteEnabled = defaults.vignetteEnabled;
		this.thresholdHealthPoints = defaults.thresholdHealthPoints;
		this.vignetteRed = defaults.vignetteRed;
		this.vignetteGreen = defaults.vignetteGreen;
		this.vignetteBlue = defaults.vignetteBlue;
		this.vignetteAlpha = defaults.vignetteAlpha;
		this.vignetteSize = defaults.vignetteSize;
		this.smoothVignetteEnabled = defaults.smoothVignetteEnabled;
		this.staticColorEnabled = defaults.staticColorEnabled;
		this.staticColorIntensity = defaults.staticColorIntensity;
		this.heartbeatEnabled = defaults.heartbeatEnabled;
		this.heartbeatScaleWithLowHealth = defaults.heartbeatScaleWithLowHealth;
		this.heartbeatSoundEnabled = defaults.heartbeatSoundEnabled;
		this.heartbeatSoundScaleWithLowHealth = defaults.heartbeatSoundScaleWithLowHealth;
		this.heartbeatSoundVolume = defaults.heartbeatSoundVolume;
		this.heartbeatSoundIntensity = defaults.heartbeatSoundIntensity;
		this.threatIndicatorEnabled = defaults.threatIndicatorEnabled;
		this.threatAdaptiveScale = defaults.threatAdaptiveScale;
		this.threatScale = defaults.threatScale;
		this.threatDistance = defaults.threatDistance;
	}

	public int getVignetteColor(int alpha) {
		return Mth.clamp(alpha, 0, 255) << 24
			| Mth.clamp(this.vignetteRed, 0, 255) << 16
			| Mth.clamp(this.vignetteGreen, 0, 255) << 8
			| Mth.clamp(this.vignetteBlue, 0, 255);
	}

	private void validate() {
		this.thresholdHealthPoints = Mth.clamp(this.thresholdHealthPoints, 1.0F, 20.0F);
		this.vignetteRed = Mth.clamp(this.vignetteRed, 0, 255);
		this.vignetteGreen = Mth.clamp(this.vignetteGreen, 0, 255);
		this.vignetteBlue = Mth.clamp(this.vignetteBlue, 0, 255);
		this.vignetteAlpha = Mth.clamp(this.vignetteAlpha, 0, 255);
		this.vignetteSize = this.vignetteSize <= 0.0F ? 1.0F : Mth.clamp(this.vignetteSize, 0.45F, 1.75F);
		this.staticColorIntensity = this.staticColorIntensity <= 0.0F ? 0.65F : Mth.clamp(this.staticColorIntensity, 0.05F, 1.0F);
		this.heartbeatSoundVolume = this.heartbeatSoundVolume <= 0.0F ? 0.65F : Mth.clamp(this.heartbeatSoundVolume, 0.05F, 1.0F);
		this.heartbeatSoundIntensity = this.heartbeatSoundIntensity <= 0.0F ? 1.0F : Mth.clamp(this.heartbeatSoundIntensity, 0.5F, 2.0F);
		this.threatScale = this.threatScale <= 0.0F ? 1.0F : Mth.clamp(this.threatScale, 0.5F, 2.5F);
		this.threatDistance = Mth.clamp(this.threatDistance, 0.0F, 1.0F);
	}
}
