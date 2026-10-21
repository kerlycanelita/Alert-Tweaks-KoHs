package dev.zymekoh.alerttweaks.test;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.alerttweaks.client.alert.Alert;
import dev.zymekoh.alerttweaks.client.alert.AlertFeed;
import dev.zymekoh.alerttweaks.client.config.AlertTweaksConfig;
import dev.zymekoh.alerttweaks.client.gui.AlertLayout;
import dev.zymekoh.alerttweaks.client.gui.AlertPositionScreen;
import dev.zymekoh.alerttweaks.client.gui.AlertTweaksScreen;
import dev.zymekoh.alerttweaks.client.threat.ThreatTracker;
import java.util.Arrays;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Opens the settings from the title screen and from a world, clicks through every page with the
 * real mouse, types a watched player, drags an alert to a new place in the position editor, and
 * makes the real alert detectors fire there in survival. Screenshots of each step land in
 * versions/<v>/build/run/clientGameTest/screenshots; look at them.
 */
public final class AlertTweaksClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getenv("ALERTTWEAKS_RECORD") != null || System.getenv("ALERTTWEAKS_GALLERY") != null) {
			return;
		}
		context.runOnClient(mc -> {
			AlertTweaksConfig.reset();
		});
		context.setScreen(() -> new AlertTweaksScreen(null));
		context.waitTicks(30);
		context.takeScreenshot("title-health");
		for (int page = 1; page < AlertLayout.TABS; page++) {
			clickTab(context, page);
			context.waitTicks(25);
			context.takeScreenshot("title-page-" + page);
			if (page == 1) {
				for (int k = 0; k < 4; k++) {
					context.waitTicks(7);
					context.takeScreenshot("title-threat-styles-" + k);
				}
			}
		}

		// Player alerts page (index 3): type a name into the field and press Enter.
		clickTab(context, 3);
		context.waitTicks(20);
		clickListRow(context, 1);
		context.getInput().typeChars("Notch");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(40);
		boolean added = context.computeOnClient(mc -> AlertTweaksConfig.get().watched("Notch") != null);
		if (!added) {
			throw new AssertionError("Typing a name did not add it to the watch list");
		}
		context.takeScreenshot("title-players-added");

		// The position editor from the header button, then drag the default stack's top card away.
		clickGui(context, mc -> {
			AlertLayout l = AlertLayout.fit(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
			return new double[]{l.edit.x() + l.edit.w() / 2.0, l.edit.y() + l.edit.h() / 2.0};
		});
		context.waitForScreen(AlertPositionScreen.class);
		// The tutorial plays by itself the first time: catch each of its four steps, then skip it.
		int[] at = {26, 46, 44, 46};
		for (int k = 0; k < at.length; k++) {
			context.waitTicks(at[k]);
			context.takeScreenshot("editor-tutorial-" + (k + 1));
		}
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(10);
		context.takeScreenshot("editor-title");
		dragGui(context, 0.5, 0.58, 0.16, 0.22);
		context.waitTicks(10);
		boolean placed = context.computeOnClient(mc -> AlertTweaksConfig.get().alert(Alert.TOTEM_OFFHAND).placed());
		if (!placed) {
			throw new AssertionError("Dragging the top card did not give it its own place");
		}
		context.takeScreenshot("editor-title-dragged");
		// Play all must show every switched-on alert, not only the last few.
		int[] counts = context.computeOnClient(mc -> {
			((AlertPositionScreen)dev.zymekoh.alerttweaks.client.Mc.screen(mc)).playAll();
			int on = 0;
			for (Alert alert : Alert.values()) {
				if (AlertTweaksConfig.get().enabled(alert)) {
					on++;
				}
			}
			return new int[]{on, AlertFeed.PREVIEW.cards().size()};
		});
		if (counts[0] != counts[1]) {
			throw new AssertionError("Play all showed " + counts[1] + " of " + counts[0] + " alerts");
		}
		context.waitTicks(8);
		context.takeScreenshot("editor-play-all");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(10);
		context.setScreen(() -> null);

		// Different looks on the alerts the world part fires.
		context.runOnClient(mc -> {
			AlertTweaksConfig c = AlertTweaksConfig.get();
			c.alert(Alert.TOTEM_OFFHAND).look = "BANNER";
			c.alert(Alert.EFFECT_HARMFUL).look = "ICON";
			c.alert(Alert.DANGEROUS_FALL).look = "TEXT";
			c.alert(Alert.TOTEMS_LOW).look = "COMPACT";
		});
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("item replace entity @a weapon.offhand with totem_of_undying");
			server.runCommand("give @a totem_of_undying 1");
			server.runCommand("give @a end_crystal 64");
			server.runCommand("give @a obsidian 64");
			context.waitTicks(80);

			// The settings in a world: the preview shows this player's real hotbar.
			context.setScreen(() -> new AlertTweaksScreen(null));
			context.waitTicks(30);
			context.takeScreenshot("world-settings-health");
			context.setScreen(() -> new AlertPositionScreen(null));
			context.waitTicks(20);
			context.takeScreenshot("world-editor");
			context.setScreen(() -> null);
			context.waitTicks(10);

			// Real detectors: the offhand totem card must show at its new place.
			server.runCommand("item replace entity @a weapon.offhand with air");
			server.runCommand("effect give @a poison 12 1");
			context.waitTicks(12);
			context.takeScreenshot("world-hud-placed");
			check(context, Alert.TOTEM_OFFHAND);
			check(context, Alert.EFFECT_HARMFUL);

			server.runCommand("effect clear @a");

			// Several enemies at once. A test server cannot host other real players, so the client gets the
			// damage packets a server sends when different players hit you, twelve ticks apart like real hits
			// (invulnerability frames): one arrow each, the same player again refreshes his own, and a fifth
			// replaces the stalest.
			context.runOnClient(mc -> AlertFeed.HUD.clear());
			int[] enemies = context.computeOnClient(AlertTweaksClientTest::spawnEnemies);
			for (int k = 0; k < 4; k++) {
				hitBy(context, enemies[k]);
			}
			expectThreats(context, enemies[0], enemies[1], enemies[2], enemies[3]);
			context.takeScreenshot("world-threats-four");
			context.runOnClient(mc -> AlertTweaksConfig.get().threatStyle = "WEAPON");
			context.waitTicks(2);
			context.takeScreenshot("world-threats-weapon");
			hitBy(context, enemies[1]);
			expectThreats(context, enemies[0], enemies[1], enemies[2], enemies[3]);
			hitBy(context, enemies[4]);
			expectThreats(context, enemies[1], enemies[2], enemies[3], enemies[4]);
			context.takeScreenshot("world-threats-fifth");
			context.runOnClient(mc -> AlertTweaksConfig.get().threatStyle = "CLASSIC");

			server.runCommand("execute as @a at @s run tp @s ~ ~80 ~");
			context.waitTicks(14);
			context.takeScreenshot("world-hud-fall");
			check(context, Alert.DANGEROUS_FALL);
			server.runCommand("effect give @a slow_falling 30");
			context.waitTicks(10);
		}
	}

	/** Five players standing round you with different weapons, known to this client only. */
	private static int[] spawnEnemies(net.minecraft.client.Minecraft mc) {
		Item[] weapons = {Items.NETHERITE_SWORD, Items.MACE, Items.NETHERITE_AXE, Items.CROSSBOW, Items.TRIDENT};
		int[] ids = new int[weapons.length];
		for (int k = 0; k < weapons.length; k++) {
			RemotePlayer enemy = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "Enemy" + k));
			double angle = Math.toRadians(20.0 + k * 72.0);
			double x = mc.player.getX() + Math.sin(angle) * 4.0;
			double z = mc.player.getZ() + Math.cos(angle) * 4.0;
			float yaw = (float)Math.toDegrees(Math.atan2(mc.player.getZ() - z, mc.player.getX() - x)) - 90.0F;
			enemy.snapTo(x, mc.player.getY(), z, yaw, 0.0F);
			enemy.setYHeadRot(yaw);
			enemy.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapons[k]));
			// The server hands out entity ids; take some far above anything it will use.
			enemy.setId(Integer.MAX_VALUE - 100 - k);
			mc.level.addEntity(enemy);
			ids[k] = enemy.getId();
		}
		return ids;
	}

	/** The damage packet a server sends you when that player lands a hit, handled by the real handler. */
	static void hitBy(ClientGameTestContext context, int attackerId) {
		context.runOnClient(mc -> {
			Player attacker = (Player)mc.level.getEntity(attackerId);
			mc.getConnection().handleDamageEvent(new ClientboundDamageEventPacket(mc.player, mc.level.damageSources().playerAttack(attacker)));
		});
		context.waitTicks(12);
	}

	private static void expectThreats(ClientGameTestContext context, int... ids) {
		int[] seen = context.computeOnClient(mc -> ThreatTracker.active().stream().mapToInt(ThreatTracker.Threat::entityId).sorted().toArray());
		int[] want = ids.clone();
		Arrays.sort(want);
		if (!Arrays.equals(seen, want)) {
			throw new AssertionError("Threat arrows for " + Arrays.toString(seen) + ", expected " + Arrays.toString(want));
		}
	}

	private static void check(ClientGameTestContext context, Alert alert) {
		boolean shown = context.computeOnClient(mc -> AlertFeed.HUD.cards().stream().anyMatch(card -> card.alert == alert));
		if (!shown) {
			throw new AssertionError("No card for " + alert);
		}
	}

	private interface GuiPoint {
		double[] at(net.minecraft.client.Minecraft mc);
	}

	/** Moves the real cursor to a GUI point and clicks. */
	private static void clickGui(ClientGameTestContext context, GuiPoint point) {
		double[] at = context.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			double[] gui = point.at(mc);
			return new double[]{gui[0] * scale, gui[1] * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	static void clickTab(ClientGameTestContext context, int index) {
		clickGui(context, mc -> {
			AlertLayout l = AlertLayout.fit(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
			AlertLayout.Rect tab = l.tab(index);
			return new double[]{tab.x() + tab.w() / 2.0, tab.y() + tab.h() / 2.0};
		});
	}

	/** Clicks the n-th row of the list (rows are about 21 GUI pixels apart; the first is a section title). */
	private static void clickListRow(ClientGameTestContext context, int n) {
		clickGui(context, mc -> {
			AlertLayout l = AlertLayout.fit(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
			return new double[]{l.list.x() + 40.0, l.list.y() + 2 + 18 + n * 0.0 + 9};
		});
	}

	/** Presses at one point of the screen (fractions) and drags to another in small steps, like a hand would. */
	private static void dragGui(ClientGameTestContext context, double fx, double fy, double tx, double ty) {
		double[] size = context.computeOnClient(mc -> new double[]{mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight()});
		context.getInput().setCursorPos(fx * size[0], fy * size[1]);
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		for (int i = 1; i <= 12; i++) {
			double t = i / 12.0;
			context.getInput().setCursorPos((fx + (tx - fx) * t) * size[0], (fy + (ty - fy) * t) * size[1]);
			context.waitTick();
		}
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
	}
}
