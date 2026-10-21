package dev.zymekoh.alerttweaks.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.util.Mth;

/**
 * Records the background clip of the settings preview: a crystal PvP arena at sunset, no HUD (the
 * preview draws the player's own HUD over it), one frame per tick at 1280x720 while the camera
 * sways in a loop. Runs only with ALERTTWEAKS_RECORD=1; tools/make_preview_frames.py turns the
 * shots into the looping frames the mod ships.
 */
public final class PreviewRecorder implements FabricClientGameTest {
	/** Frames kept plus the extra ones crossfaded into the start so the loop has no seam. */
	public static final int FRAMES = 48;
	public static final int OVERLAP = 8;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getenv("ALERTTWEAKS_RECORD") == null) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("gamemode creative @a");
			buildArena(server);
			server.runCommand("summon mannequin 1.5 -60 -3.5 {Rotation:[18f,0f],"
				+ "equipment:{head:{id:\"minecraft:netherite_helmet\"},chest:{id:\"minecraft:netherite_chestplate\"},"
				+ "legs:{id:\"minecraft:netherite_leggings\"},feet:{id:\"minecraft:netherite_boots\"},"
				+ "mainhand:{id:\"minecraft:netherite_sword\"},offhand:{id:\"minecraft:totem_of_undying\"}}}");
			server.runCommand("tp @a 0.5 -60 3.5 180 4");
			context.runOnClient(mc -> {
				mc.options.bobView().set(false);
				mc.options.fov().set(70);
				if (!dev.zymekoh.alerttweaks.client.Mc.hudHidden(mc)) {
					//? if >=26.2 {
					mc.gui.hud.toggle();
					//?} else {
					/*mc.options.hideGui = true;
					*///?}
				}
			});
			context.waitTicks(100);
			for (int k = 0; k < FRAMES + OVERLAP; k++) {
				float phase = (float)(2.0 * Math.PI * k / FRAMES);
				float yaw = 180.0F + 6.0F * Mth.sin(phase);
				float pitch = 4.0F + 1.2F * Mth.sin(phase * 2.0F);
				context.runOnClient(mc -> {
					mc.player.setYRot(yaw);
					mc.player.yRotO = yaw;
					mc.player.setXRot(pitch);
					mc.player.xRotO = pitch;
				});
				if (k % 2 == 0) {
					server.runCommand("particle minecraft:reverse_portal -0.5 -59.2 -1.5 0.4 0.6 0.4 0.02 6 force");
					server.runCommand("particle minecraft:reverse_portal -6.5 -56.2 -8.5 0.4 0.6 0.4 0.02 4 force");
				}
				context.takeScreenshot(TestScreenshotOptions.of(String.format(java.util.Locale.ROOT, "rec_%03d", k))
					.withSize(1280, 720).disableCounterPrefix());
				context.waitTick();
			}
		}
	}

	/** A used crystal PvP arena at sunset on the superflat test world: obsidian floor, anchors, four crystals. */
	static void buildArena(TestServerContext server) {
		server.runCommand("time set 12100");
		server.runCommand("weather clear");
		server.runCommand("kill @e[type=!player]");
		server.runCommand("fill -12 -61 -16 12 -61 8 obsidian");
		server.runCommand("fill -12 -60 -16 12 -52 8 air");
		// Break up the floor like a used arena: crying obsidian and bedrock patches.
		for (int[] b : new int[][]{{-2, -1}, {3, -2}, {-4, -5}, {1, -7}, {5, -9}, {-6, -2}, {2, 1}, {-1, -10}, {7, -5}, {-8, -7}}) {
			server.runCommand("setblock " + b[0] + " -61 " + b[1] + (b[0] % 2 == 0 ? " crying_obsidian" : " bedrock"));
		}
		server.runCommand("setblock -3 -60 -2 respawn_anchor[charges=4]");
		server.runCommand("setblock 4 -60 -5 respawn_anchor[charges=2]");
		server.runCommand("fill -7 -60 -9 -7 -58 -9 obsidian");
		server.runCommand("fill 7 -60 -3 7 -59 -3 obsidian");
		server.runCommand("summon end_crystal -0.5 -60 -1.5 {ShowBottom:0b}");
		server.runCommand("summon end_crystal -6.5 -57 -8.5 {ShowBottom:0b}");
		server.runCommand("summon end_crystal 7.5 -58 -2.5 {ShowBottom:0b}");
		server.runCommand("summon end_crystal 3.5 -60 -9.5 {ShowBottom:0b}");
	}
}
