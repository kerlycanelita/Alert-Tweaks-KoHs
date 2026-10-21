package dev.zymekoh.alerttweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Game calls whose name or place changed between the supported versions. */
public final class Mc {
	private Mc() {
	}

	public static boolean hudHidden(Minecraft mc) {
		//? if >=26.2 {
		return mc.gui.hud.isHidden();
		//?} else {
		/*return mc.options.hideGui;
		*///?}
	}

	public static void setScreen(Minecraft mc, Screen screen) {
		//? if >=26.2 {
		mc.gui.setScreen(screen);
		//?} else {
		/*mc.setScreen(screen);
		*///?}
	}

	public static Screen screen(Minecraft mc) {
		//? if >=26.2 {
		return mc.gui.screen();
		//?} else {
		/*return mc.screen;
		*///?}
	}
}
