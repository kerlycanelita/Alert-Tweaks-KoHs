package dev.zymekoh.alerttweaks.test;

import com.mojang.authlib.GameProfile;
import dev.zymekoh.alerttweaks.client.Faces;
import dev.zymekoh.alerttweaks.client.alert.Alert;
import dev.zymekoh.alerttweaks.client.alert.AlertFeed;
import dev.zymekoh.alerttweaks.client.alert.AlertSamples;
import dev.zymekoh.alerttweaks.client.config.AlertTweaksConfig;
import dev.zymekoh.alerttweaks.client.gui.AlertPositionScreen;
import dev.zymekoh.alerttweaks.client.gui.AlertTweaksScreen;
import dev.zymekoh.alerttweaks.client.threat.ThreatTracker;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Takes the Modrinth gallery: real 1920x1080 screenshots in a crystal PvP arena, of the HUD in a
 * fight, the settings pages and the position editor. Runs only with ALERTTWEAKS_GALLERY=1; the shots
 * are gallery_*.png in versions/<v>/build/run/clientGameTest/screenshots.
 */
public final class GalleryRecorder implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getenv("ALERTTWEAKS_GALLERY") == null) {
			return;
		}
		context.runOnClient(mc -> {
			AlertTweaksConfig.reset();
			AlertTweaksConfig c = AlertTweaksConfig.get();
			c.toastSeconds = 15.0F;
			c.editorTutorialSeen = true;
			c.players.add(new AlertTweaksConfig.WatchedPlayer("Notch"));
			c.players.add(new AlertTweaksConfig.WatchedPlayer("jeb_"));
			mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
			mc.options.damageTiltStrength().set(0.0);
			mc.options.bobView().set(false);
			mc.options.fov().set(70);
			mc.options.guiScale().set(3);
		});
		// A real 1080p window, so the HUD and the screens lay out for it (a bigger screenshot alone only scales the world).
		context.getInput().resizeWindow(1920, 1080);
		context.waitTicks(5);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			PreviewRecorder.buildArena(server);
			server.runCommand("gamemode survival @a");
			// The rivals exist on this client only; keep their made-up names off the pictures.
			server.runCommand("team add rivals");
			server.runCommand("team modify rivals nametagVisibility never");
			for (int k = 1; k <= 4; k++) {
				server.runCommand("team join rivals Rival" + k);
			}
			server.runCommand("gamerule natural_health_regeneration false");
			String[] hotbar = {"netherite_sword", "end_crystal 64", "obsidian 64", "respawn_anchor 16", "glowstone 64",
				"ender_pearl 16", "golden_apple 24", "totem_of_undying", "experience_bottle 64"};
			for (int k = 0; k < hotbar.length; k++) {
				server.runCommand("item replace entity @a hotbar." + k + " with " + hotbar[k]);
			}
			for (String slot : new String[]{"head", "chest", "legs", "feet"}) {
				String piece = slot.equals("head") ? "helmet" : slot.equals("chest") ? "chestplate" : slot.equals("legs") ? "leggings" : "boots";
				server.runCommand("item replace entity @a armor." + slot + " with netherite_" + piece);
			}
			server.runCommand("item replace entity @a weapon.offhand with totem_of_undying");
			server.runCommand("give @a totem_of_undying 3");
			server.runCommand("tp @a 1.5 -60 6.5 184 3");
			context.waitTicks(100);
			int[] rivals = context.computeOnClient(GalleryRecorder::spawnRivals);

			// 1. A fight: low health, three rivals hitting, four alerts in four looks.
			context.runOnClient(mc -> {
				AlertTweaksConfig c = AlertTweaksConfig.get();
				place(c, Alert.TOTEM_OFFHAND, "BANNER", 0.5F, 0.17F);
				place(c, Alert.ENEMY_POP, "CARD", 0.985F, 0.33F);
				place(c, Alert.ARMOR_LOW, "COMPACT", 0.015F, 0.33F);
				place(c, Alert.EFFECT_HARMFUL, "ICON", 0.62F, 0.6F);
				AlertFeed.HUD.clear();
			});
			server.runCommand("damage @p 15 minecraft:magic");
			for (int k = 2; k >= 0; k--) {
				AlertTweaksClientTest.hitBy(context, rivals[k]);
			}
			server.runCommand("item replace entity @a weapon.offhand with air");
			fire(context, Alert.ENEMY_POP, Alert.ARMOR_LOW, Alert.EFFECT_HARMFUL);
			context.waitTicks(14);
			shot(context, "gallery_01_fight");

			// 2-5. The settings in the same world, over the arena.
			server.runCommand("item replace entity @a weapon.offhand with totem_of_undying");
			server.runCommand("effect give @a instant_health 1 3");
			context.runOnClient(mc -> {
				AlertFeed.HUD.clear();
				ThreatTracker.clear();
			});
			context.waitTicks(20);
			context.setScreen(() -> new AlertTweaksScreen(null));
			context.waitTicks(60);
			park(context);
			shot(context, "gallery_02_health");
			int[] pages = {2, 1, 3};
			String[] names = {"gallery_03_alerts", "gallery_04_threat", "gallery_05_players"};
			for (int k = 0; k < pages.length; k++) {
				AlertTweaksClientTest.clickTab(context, pages[k]);
				context.waitTicks(pages[k] == 3 ? 60 : 30);
				park(context);
				if (pages[k] == 1) {
					// The style tiles loop their own animation: a few moments to pick from.
					for (int m = 1; m <= 6; m++) {
						shot(context, names[k] + "_" + m);
						context.waitTicks(6);
					}
				} else {
					shot(context, names[k]);
				}
			}

			// 6. The position editor with every alert playing in its place.
			context.setScreen(() -> new AlertPositionScreen(null));
			context.waitTicks(20);
			context.runOnClient(mc -> ((AlertPositionScreen)dev.zymekoh.alerttweaks.client.Mc.screen(mc)).playAll());
			context.waitTicks(16);
			park(context);
			shot(context, "gallery_06_editor");
			context.setScreen(() -> null);
			context.runOnClient(mc -> AlertFeed.PREVIEW.clear());

			// 7. The five looks side by side.
			context.runOnClient(mc -> {
				AlertTweaksConfig c = AlertTweaksConfig.get();
				place(c, Alert.SERVER_LAG, "BANNER", 0.5F, 0.15F);
				place(c, Alert.TOTEMS_LOW, "CARD", 0.015F, 0.3F);
				place(c, Alert.SUPPLIES_LOW, "COMPACT", 0.985F, 0.3F);
				place(c, Alert.COOLDOWN_READY, "ICON", 0.66F, 0.4F);
				place(c, Alert.DANGEROUS_FALL, "TEXT", 0.5F, 0.72F);
				AlertFeed.HUD.clear();
			});
			fire(context, Alert.SERVER_LAG, Alert.TOTEMS_LOW, Alert.SUPPLIES_LOW, Alert.COOLDOWN_READY, Alert.DANGEROUS_FALL);
			context.waitTicks(14);
			shot(context, "gallery_07_looks");

			// 8. Four rivals at once, Weapon style: one arrow each, showing what hit you.
			context.runOnClient(mc -> {
				AlertFeed.HUD.clear();
				AlertTweaksConfig.get().threatStyle = "WEAPON";
			});
			for (int k = 3; k >= 0; k--) {
				AlertTweaksClientTest.hitBy(context, rivals[k]);
			}
			shot(context, "gallery_08_threats");

			// 9. Watched players joining and leaving, with their faces.
			context.runOnClient(mc -> {
				AlertTweaksConfig.get().threatStyle = "CLASSIC";
				ThreatTracker.clear();
				AlertFeed.HUD.clear();
				AlertTweaksConfig c = AlertTweaksConfig.get();
				place(c, Alert.PLAYER_JOIN, "CARD", 0.985F, 0.12F);
				place(c, Alert.PLAYER_LEAVE, "CARD", 0.985F, 0.12F);
				AlertFeed.HUD.muted = true;
				AlertFeed.HUD.fireFace(Alert.PLAYER_LEAVE, "jeb_", Component.translatable("alert_tweaks.msg.player_leave", "jeb_"),
					Component.translatable("alert_tweaks.msg.player_detail"), Faces.byName("jeb_"));
				AlertFeed.HUD.fireFace(Alert.PLAYER_JOIN, "notch", Component.translatable("alert_tweaks.msg.player_join", "Notch"),
					Component.translatable("alert_tweaks.msg.player_detail"), Faces.byName("Notch"));
				AlertFeed.HUD.muted = false;
			});
			context.waitTicks(30);
			shot(context, "gallery_09_players_hud");
		}
	}

	private static void place(AlertTweaksConfig config, Alert alert, String look, float x, float y) {
		AlertTweaksConfig.AlertSetting setting = config.alert(alert);
		setting.look = look;
		setting.x = x;
		setting.y = y;
	}

	private static void fire(ClientGameTestContext context, Alert... alerts) {
		context.runOnClient(mc -> {
			for (Alert alert : alerts) {
				AlertSamples.fire(AlertFeed.HUD, alert, false);
			}
		});
	}

	/** The cursor over the preview corner, where nothing lights up under it. */
	private static void park(ClientGameTestContext context) {
		double[] size = context.computeOnClient(mc -> new double[]{mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight()});
		context.getInput().setCursorPos(size[0] * 0.995, size[1] * 0.5);
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	/** Four rivals in netherite with different weapons: two in front, one behind, one to the left. */
	private static int[] spawnRivals(Minecraft mc) {
		double[][] at = {{-2.0, -4.5}, {4.0, -2.5}, {0.5, 9.5}, {-6.5, 5.0}};
		Item[] weapons = {Items.NETHERITE_SWORD, Items.MACE, Items.NETHERITE_AXE, Items.CROSSBOW};
		int[] ids = new int[at.length];
		for (int k = 0; k < at.length; k++) {
			RemotePlayer rival = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "Rival" + (k + 1)));
			double x = at[k][0];
			double z = at[k][1];
			float yaw = (float)Math.toDegrees(Math.atan2(mc.player.getZ() - z, mc.player.getX() - x)) - 90.0F;
			rival.snapTo(x, -60.0, z, yaw, 0.0F);
			rival.setYHeadRot(yaw);
			rival.yBodyRot = yaw;
			rival.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
			rival.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
			rival.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
			rival.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
			rival.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapons[k]));
			rival.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
			rival.setId(Integer.MAX_VALUE - 200 - k);
			mc.level.addEntity(rival);
			ids[k] = rival.getId();
		}
		return ids;
	}
}
