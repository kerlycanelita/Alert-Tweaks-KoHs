package dev.zymekoh.alerttweaks.client.gui;

/** KoHs palette: black and very dark purple surfaces, black metal edges, purple to magenta energy, crimson only for danger. */
public final class Theme {
	public static final int VOID = 0xF2060309;
	public static final int PANEL = 0xEC0D0614;
	public static final int PANEL_HI = 0xF4170A24;
	public static final int ROW = 0xB4150A20;
	public static final int ROW_HOVER = 0xD8231236;
	public static final int METAL_DARK = 0xFF15121A;
	public static final int METAL = 0xFF3B3644;
	public static final int METAL_LIGHT = 0xFF8C8697;
	public static final int PURPLE = 0xFF8A45E0;
	public static final int PURPLE_DEEP = 0xFF3A1466;
	public static final int MAGENTA = 0xFFD45CFF;
	public static final int CRIMSON = 0xFFE0284E;
	public static final int AMBER = 0xFFF2B33D;
	public static final int MINT = 0xFF5CE0A8;
	public static final int TEXT = 0xFFF3ECFF;
	public static final int MUTED = 0xFFB4A8C8;
	public static final int DIM = 0xFF6C6080;

	private Theme() {
	}

	public static int alpha(int color, float alpha) {
		int a = Math.round(((color >>> 24) & 0xFF) * clamp01(alpha));
		return a << 24 | color & 0xFFFFFF;
	}

	public static int mix(int from, int to, float t) {
		t = clamp01(t);
		int a = lerp(from >>> 24, to >>> 24, t);
		int r = lerp(from >> 16 & 0xFF, to >> 16 & 0xFF, t);
		int g = lerp(from >> 8 & 0xFF, to >> 8 & 0xFF, t);
		int b = lerp(from & 0xFF, to & 0xFF, t);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int lerp(int a, int b, float t) {
		return Math.round(a + (b - a) * t);
	}

	public static float clamp01(float v) {
		return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
	}

	/** Frame-rate independent approach: fraction of the remaining distance covered in {@code dtMs}. */
	public static float approach(float dtMs, float timeConstantMs) {
		return 1.0F - (float)Math.exp(-dtMs / timeConstantMs);
	}

	public static float easeOutBack(float t) {
		float x = t - 1.0F;
		return 1.0F + 2.70158F * x * x * x + 1.70158F * x * x;
	}

	public static float easeOutCubic(float t) {
		float x = 1.0F - clamp01(t);
		return 1.0F - x * x * x;
	}

	public static float easeInOut(float t) {
		t = clamp01(t);
		return t * t * (3.0F - 2.0F * t);
	}
}
