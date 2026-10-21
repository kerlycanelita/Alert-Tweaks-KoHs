package dev.zymekoh.alerttweaks.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.zymekoh.alerttweaks.AlertTweaks;
import dev.zymekoh.alerttweaks.client.alert.Alert;
import dev.zymekoh.alerttweaks.client.alert.AlertSound;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

public final class AlertTweaksConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("alert_tweaks.json");
	/** Settings from the mod this one replaces are imported once, so nobody has to redo them. */
	private static final Path LEGACY_PATH = FabricLoader.getInstance().getConfigDir().resolve("healt_tweaks.json");

	private static AlertTweaksConfig instance = new AlertTweaksConfig();

	public boolean vignetteEnabled = true;
	public float thresholdHealthPoints = 8.0F;
	public int vignetteRed = 102;
	public int vignetteGreen = 26;
	public int vignetteBlue = 160;
	public int vignetteAlpha = 190;
	public float vignetteSize = 1.0F;
	public boolean smoothVignetteEnabled = true;
	public boolean staticColorEnabled = false;
	public float staticColorIntensity = 0.65F;
	public boolean heartbeatEnabled = true;
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

	/** Where the alert cards appear: CROSSHAIR, TOP, RIGHT or LEFT. Only read to migrate 2.0 configs to {@link #defaultX}. */
	public String toastAnchor;
	/** Where alerts without a place of their own appear (0..1 of the screen); see AlertPlacement. */
	public float defaultX = -1.0F;
	public float defaultY = -1.0F;
	/** ThreatStyle name. */
	public String threatStyle = "CLASSIC";
	/** Players whose arrival or departure on the server raises an alert. */
	public java.util.List<WatchedPlayer> players = new java.util.ArrayList<>();
	public float toastScale = 1.0F;
	public float toastSeconds = 3.0F;
	public float alertVolume = 0.8F;
	/** The position editor has shown its tutorial once. */
	public boolean editorTutorialSeen;
	/** Danger alerts flash the screen edges once in crimson. */
	public boolean edgeFlash = true;
	public Map<String, AlertSetting> alerts = new LinkedHashMap<>();

	public static final class AlertSetting {
		public boolean enabled;
		public String sound;
		public float threshold;
		/** AlertLook name: CARD, COMPACT, ICON, TEXT or BANNER. */
		public String look = "CARD";
		/** Own place on screen (0..1); negative = with the other unplaced alerts at the default place. */
		public float x = -1.0F;
		public float y = -1.0F;

		public boolean placed() {
			return this.x >= 0.0F && this.y >= 0.0F;
		}
	}

	public static final class WatchedPlayer {
		public String name;
		public boolean enabled = true;

		public WatchedPlayer() {
		}

		public WatchedPlayer(String name) {
			this.name = name;
		}
	}

	/** The watched entry for a name, ignoring case, or null. */
	public WatchedPlayer watched(String name) {
		for (WatchedPlayer player : this.players) {
			if (player.name.equalsIgnoreCase(name)) {
				return player;
			}
		}
		return null;
	}

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
		} catch (IOException | RuntimeException exception) {
			AlertTweaks.LOGGER.warn("Could not load the Alert Tweaks config, using defaults.", exception);
			instance = new AlertTweaksConfig();
		}
		instance.validate();
		if (source == LEGACY_PATH) {
			AlertTweaks.LOGGER.info("Imported the previous healt_tweaks.json settings.");
		}
		save();
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

	public static void reset() {
		instance = new AlertTweaksConfig();
		save();
	}

	public AlertSetting alert(Alert alert) {
		AlertSetting setting = this.alerts.get(alert.key);
		if (setting == null) {
			setting = defaults(alert);
			this.alerts.put(alert.key, setting);
		}
		return setting;
	}

	public boolean enabled(Alert alert) {
		return this.alert(alert).enabled;
	}

	public float threshold(Alert alert) {
		return alert.threshold == null ? 0.0F : this.alert(alert).threshold;
	}

	public dev.zymekoh.alerttweaks.client.alert.AlertLook look(Alert alert) {
		return dev.zymekoh.alerttweaks.client.alert.AlertLook.parse(this.alert(alert).look);
	}

	public AlertSound sound(Alert alert) {
		return AlertSound.parse(this.alert(alert).sound, alert.defaultSound);
	}

	private static AlertSetting defaults(Alert alert) {
		AlertSetting setting = new AlertSetting();
		setting.enabled = alert.enabledByDefault;
		setting.sound = alert.defaultSound.name();
		setting.threshold = alert.threshold == null ? 0.0F : alert.threshold.defaultValue();
		return setting;
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
		if (this.defaultX < 0.0F || this.defaultY < 0.0F) {
			// 2.0 had four fixed places; turn the chosen one into a free position.
			String old = this.toastAnchor == null ? "CROSSHAIR" : this.toastAnchor;
			switch (old) {
				case "TOP" -> { this.defaultX = 0.5F; this.defaultY = 0.16F; }
				case "RIGHT" -> { this.defaultX = 0.985F; this.defaultY = 0.42F; }
				case "LEFT" -> { this.defaultX = 0.015F; this.defaultY = 0.42F; }
				default -> { this.defaultX = 0.5F; this.defaultY = 0.58F; }
			}
		}
		this.toastAnchor = null;
		this.defaultX = Mth.clamp(this.defaultX, 0.0F, 1.0F);
		this.defaultY = Mth.clamp(this.defaultY, 0.0F, 1.0F);
		if (this.threatStyle == null) {
			this.threatStyle = "CLASSIC";
		}
		if (this.players == null) {
			this.players = new java.util.ArrayList<>();
		}
		this.players.removeIf(p -> p == null || p.name == null || p.name.isBlank());
		this.toastScale = this.toastScale <= 0.0F ? 1.0F : Mth.clamp(this.toastScale, 0.6F, 1.6F);
		this.toastSeconds = this.toastSeconds <= 0.0F ? 3.0F : Mth.clamp(this.toastSeconds, 1.5F, 8.0F);
		this.alertVolume = Mth.clamp(this.alertVolume, 0.0F, 1.0F);
		if (this.alerts == null) {
			this.alerts = new LinkedHashMap<>();
		}
		for (Alert alert : Alert.values()) {
			AlertSetting setting = this.alert(alert);
			setting.sound = AlertSound.parse(setting.sound, alert.defaultSound).name();
			setting.look = dev.zymekoh.alerttweaks.client.alert.AlertLook.parse(setting.look).name();
			if (alert.threshold != null) {
				setting.threshold = Mth.clamp(setting.threshold, alert.threshold.min(), alert.threshold.max());
			}
			if (setting.placed()) {
				setting.x = Mth.clamp(setting.x, 0.0F, 1.0F);
				setting.y = Mth.clamp(setting.y, 0.0F, 1.0F);
			}
		}
	}
}
