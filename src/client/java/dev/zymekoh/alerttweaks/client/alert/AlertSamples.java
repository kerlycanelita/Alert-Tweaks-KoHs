package dev.zymekoh.alerttweaks.client.alert;

import dev.zymekoh.alerttweaks.client.Faces;
import dev.zymekoh.alerttweaks.client.config.AlertTweaksConfig;
import dev.zymekoh.alerttweaks.client.gui.Icons;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/** Example cards for the previews and the position editor, built from the same messages the real alerts use. */
public final class AlertSamples {
	public record Content(String subject, Component title, Component detail, Identifier icon, Supplier<PlayerSkin> face) {
	}

	private AlertSamples() {
	}

	private static Component msg(String key, Object... args) {
		return Component.translatable("alert_tweaks.msg." + key, args);
	}

	private static Component item(String id) {
		return Component.translatable("item.minecraft." + id);
	}

	private static Component effect(String id) {
		return Component.translatable("effect.minecraft." + id);
	}

	private static Identifier effectIcon(String id) {
		return Identifier.withDefaultNamespace("textures/mob_effect/" + id + ".png");
	}

	private static Content of(String subject, Component title, Component detail, Identifier icon) {
		return new Content(subject, title, detail, icon, null);
	}

	/** A name for the player samples: the first watched player, or the person playing. */
	private static String samplePlayer() {
		AlertTweaksConfig config = AlertTweaksConfig.get();
		return config.players.isEmpty() ? Minecraft.getInstance().getUser().getName() : config.players.get(0).name;
	}

	public static Content content(Alert alert) {
		return switch (alert) {
			case TOTEM_OFFHAND -> of("", msg("totem_offhand"), msg("totems_left", 3), Icons.TOTEM);
			case TOTEMS_LOW -> of("", msg("totems_low"), msg("totems_left", 1), Icons.TOTEM);
			case ENEMY_POP -> of("demo", msg("enemy_pop", "Rival"), msg("enemy_pop_count", 2), Icons.TOTEM);
			case ARMOR_LOW -> of("chest", msg("armor_low", item("netherite_chestplate")), msg("percent_left", 12), Icons.item("netherite_chestplate"));
			case HELD_LOW -> of("", msg("held_low", item("netherite_sword")), msg("percent_left", 8), Icons.item("netherite_sword"));
			case SHIELD_DISABLED -> of("", msg("shield_disabled"), msg("shield_disabled_detail"), Icons.AXE);
			case COOLDOWN_READY -> of("pearl", msg("cooldown_ready", item("ender_pearl")), msg("cooldown_ready_detail", 15.0F), Icons.PEARL);
			case EFFECT_HARMFUL -> of("poison", effect("poison").copy().append(" ").append(Component.translatable("potion.potency.1")),
				msg("seconds", 8), Icons.POISON);
			case EFFECT_ENDING -> of("fire", msg("effect_ending", effect("fire_resistance")), msg("seconds", 5), effectIcon("fire_resistance"));
			case NO_SPRINT -> of("", msg("no_sprint"), msg("no_sprint_detail"), Icons.BEEF);
			case DANGEROUS_FALL -> of("", msg("dangerous_fall"), msg("fall_lethal"), Icons.FEATHER);
			case SUPPLIES_LOW -> of("gapple", msg("supplies_low", item("golden_apple")), msg("count_left", 2), Icons.GAPPLE);
			case INVENTORY_FULL -> of("", msg("inventory_full"), msg("inventory_full_detail"), Icons.BUNDLE);
			case SERVER_LAG -> of("", msg("server_lag"), msg("server_lag_detail", 2.4F), Icons.CLOCK);
			case PLAYER_JOIN, PLAYER_LEAVE, PLAYER_ONLINE -> {
				String name = samplePlayer();
				Component title = switch (alert) {
					case PLAYER_JOIN -> msg("player_join", name);
					case PLAYER_LEAVE -> msg("player_leave", name);
					default -> msg("player_online", name);
				};
				Component detail = alert == Alert.PLAYER_ONLINE ? msg("player_online_detail", 1) : msg("player_detail");
				yield new Content(name.toLowerCase(java.util.Locale.ROOT), title, detail, Icons.NAME_TAG, Faces.byName(name));
			}
		};
	}

	public static void fire(AlertFeed feed, Alert alert, boolean audible) {
		Content c = content(alert);
		feed.muted = !audible;
		if (c.face() != null) {
			feed.fireFace(alert, c.subject(), c.title(), c.detail(), c.face());
		} else {
			feed.fire(alert, c.subject(), c.title(), c.detail(), c.icon());
		}
		feed.muted = false;
	}
}
