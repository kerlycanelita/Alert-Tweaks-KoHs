package dev.zymekoh.alerttweaks.client.alert;

import dev.zymekoh.alerttweaks.client.gui.Icons;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Every alert the mod can raise. Each one reports something the player cannot read at a glance
 * from the vanilla screen, using only what the client already knows. None of them acts for the player.
 */
public enum Alert {
	TOTEM_OFFHAND(Group.TOTEMS, Icons.TOTEM, Tone.DANGER, true, AlertSound.ALARM, null),
	TOTEMS_LOW(Group.TOTEMS, Icons.TOTEM, Tone.WARN, true, AlertSound.PLING, new Threshold(0, 5, 1, 1, Unit.COUNT)),
	ENEMY_POP(Group.TOTEMS, Icons.TOTEM, Tone.GOOD, true, AlertSound.CHIME, null),
	ARMOR_LOW(Group.GEAR, Icons.CHESTPLATE, Tone.WARN, true, AlertSound.BASS, new Threshold(5, 50, 5, 15, Unit.PERCENT)),
	HELD_LOW(Group.GEAR, Icons.SWORD, Tone.WARN, true, AlertSound.BASS, new Threshold(5, 50, 5, 10, Unit.PERCENT)),
	SHIELD_DISABLED(Group.GEAR, Icons.AXE, Tone.WARN, true, AlertSound.BASS, null),
	COOLDOWN_READY(Group.GEAR, Icons.PEARL, Tone.GOOD, true, AlertSound.CHIRP, new Threshold(1, 20, 1, 3, Unit.SECONDS)),
	EFFECT_HARMFUL(Group.BODY, Icons.POISON, Tone.DANGER, true, AlertSound.BASS, null),
	EFFECT_ENDING(Group.BODY, Icons.POTION, Tone.WARN, true, AlertSound.PLING, new Threshold(3, 30, 1, 5, Unit.SECONDS)),
	NO_SPRINT(Group.BODY, Icons.BEEF, Tone.WARN, true, AlertSound.PLING, null),
	DANGEROUS_FALL(Group.BODY, Icons.FEATHER, Tone.DANGER, true, AlertSound.ALARM, new Threshold(25, 100, 5, 100, Unit.PERCENT)),
	SUPPLIES_LOW(Group.WORLD, Icons.GAPPLE, Tone.WARN, true, AlertSound.PLING, null),
	INVENTORY_FULL(Group.WORLD, Icons.BUNDLE, Tone.INFO, false, AlertSound.CHIRP, null),
	SERVER_LAG(Group.WORLD, Icons.CLOCK, Tone.DANGER, true, AlertSound.BASS, new Threshold(1.5F, 6, 0.5F, 2, Unit.SECONDS)),
	PLAYER_JOIN(Group.PLAYERS, Icons.NAME_TAG, Tone.GOOD, true, AlertSound.CHIME, null),
	PLAYER_LEAVE(Group.PLAYERS, Icons.NAME_TAG, Tone.INFO, true, AlertSound.CHIRP, null),
	PLAYER_ONLINE(Group.PLAYERS, Icons.NAME_TAG, Tone.GOOD, true, AlertSound.PLING, null);

	public enum Group {
		TOTEMS, GEAR, BODY, WORLD, PLAYERS;

		public Component title() {
			return Component.translatable("alert_tweaks.group." + this.name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	/** Danger is crimson, the rest stay in the KoHs purple family. */
	public enum Tone {
		DANGER, WARN, INFO, GOOD
	}

	public enum Unit {
		COUNT, PERCENT, SECONDS
	}

	public record Threshold(float min, float max, float step, float defaultValue, Unit unit) {
	}

	public final Group group;
	public final Identifier icon;
	public final Tone tone;
	public final boolean enabledByDefault;
	public final AlertSound defaultSound;
	public final Threshold threshold;
	public final String key;

	Alert(Group group, Identifier icon, Tone tone, boolean enabledByDefault, AlertSound defaultSound, Threshold threshold) {
		this.group = group;
		this.icon = icon;
		this.tone = tone;
		this.enabledByDefault = enabledByDefault;
		this.defaultSound = defaultSound;
		this.threshold = threshold;
		this.key = this.name().toLowerCase(java.util.Locale.ROOT);
	}

	public Component title() {
		return Component.translatable("alert_tweaks.alert." + this.key);
	}

	public Component description() {
		return Component.translatable("alert_tweaks.alert." + this.key + ".desc");
	}

	public Component thresholdLabel() {
		return Component.translatable("alert_tweaks.alert." + this.key + ".threshold");
	}
}
