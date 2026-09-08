import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the mod icon: a heart closed in on by four threat arrows, over the same purple nebula
 * the previous Healt Alert Tweaks icon used.
 *
 * <p>The background is painted at full resolution so the stars stay fine, while the subject is
 * authored on a 32x32 grid and blown up 8x, which is what gives it the chunky pixel-art look.
 */
public final class GenerateIcon {
	static final int SIZE = 256;
	static final int GRID = 32;
	static final int SCALE = SIZE / GRID;
	static final int CORNER_RADIUS = 46;

	// Sampled from the icon this one replaces, so the family stays recognisable.
	static final int BG_DEEP = 0x1B0250;
	static final int BG_MID = 0x230360;
	static final int NEBULA_A = 0x5911A9;
	static final int NEBULA_B = 0x9A12F8;
	static final int NEBULA_C = 0xC31BFA;
	static final int FRAME_OUTER = 0x5B02AD;
	static final int FRAME_INNER = 0x7412C2;

	static final int OUTLINE = 0x2D0572;
	static final int HEART_LIGHT = 0xD140FA;
	static final int HEART_MID = 0xC31BFA;
	static final int HEART_BODY = 0xAC02F8;
	static final int HEART_DEEP = 0x9A02F0;
	static final int HEART_DARK = 0x8607EF;
	static final int HIGHLIGHT = 0xF8E7FA;
	static final int SPARK = 0x7DF3FF;

	// Cooler ramp for the arrows, taken from the in-game threat sprite so they read as a separate object.
	static final int ARROW_DEEP = 0x3D0B6B;
	static final int ARROW_BODY = 0x7B2FD1;
	static final int ARROW_LIGHT = 0xB44BF5;
	static final int ARROW_CORE = 0x5EE9F7;

	static final String[] HEART = {
		".XXX...XXX.",
		"XXXXX.XXXXX",
		"XXXXXXXXXXX",
		"XXXXXXXXXXX",
		"XXXXXXXXXXX",
		".XXXXXXXXX.",
		"..XXXXXXX..",
		"...XXXXX...",
		"....XXX....",
		".....X....."
	};

	/** Pointing down; the other three sides are rotations of this. */
	static final String[] ARROW = {
		"..XXX..",
		"..XXX..",
		"..XXX..",
		"XXXXXXX",
		".XXXXX.",
		"..XXX..",
		"...X..."
	};

	public static void main(String[] args) throws Exception {
		int[] pixels = new int[SIZE * SIZE];
		background(pixels);

		int[] art = new int[GRID * GRID];
		heart(art, 10, 11);
		arrows(art);
		outline(art);
		blit(pixels, art);

		frame(pixels);

		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, SIZE, SIZE, pixels, 0, SIZE);
		for (String path : args) {
			File file = new File(path);
			if (file.getParentFile() != null) {
				file.getParentFile().mkdirs();
			}
			ImageIO.write(image, "png", file);
			System.out.println("wrote " + file.getAbsolutePath());
		}
	}

	// ------------------------------------------------------------------ background

	private static void background(int[] pixels) {
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double nx = x / (double)SIZE;
				double ny = y / (double)SIZE;
				double dx = nx - 0.5;
				double dy = ny - 0.5;
				double distance = Math.sqrt(dx * dx + dy * dy) * 2.0;

				double cloud = fbm(nx * 3.4, ny * 3.4);
				// Push the midtones apart so the clouds keep the contrast of the original icon.
				cloud = clamp((cloud - 0.34) * 2.15, 0.0, 1.0);
				double glow = clamp(1.55 - distance * 1.0, 0.18, 1.0);
				double core = clamp(1.0 - distance * 1.9, 0.0, 1.0);

				int color = mix(BG_DEEP, BG_MID, clamp(cloud * 1.5, 0.0, 1.0));
				color = mix(color, NEBULA_A, clamp(cloud * cloud * 2.1 + 0.12, 0.0, 1.0));
				color = mix(color, NEBULA_B, clamp((cloud - 0.30) * 2.0 * glow, 0.0, 1.0));
				color = mix(color, NEBULA_C, clamp((cloud - 0.55) * 2.6 * glow * glow, 0.0, 1.0));
				// Halo behind the subject, so the heart sits on light instead of on noise.
				color = mix(color, NEBULA_C, core * core * 0.42);
				pixels[y * SIZE + x] = 0xFF000000 | color;
			}
		}
		stars(pixels);
	}

	private static void stars(int[] pixels) {
		int seed = 0x5EED;
		for (int index = 0; index < 230; index++) {
			seed = next(seed);
			int x = Math.floorMod(seed >>> 7, SIZE);
			seed = next(seed);
			int y = Math.floorMod(seed >>> 7, SIZE);
			seed = next(seed);
			int roll = Math.floorMod(seed >>> 9, 100);

			// Keep the field off the subject so the heart stays clean.
			double dx = x - SIZE / 2.0;
			double dy = y - SIZE / 2.0;
			if (Math.sqrt(dx * dx + dy * dy) < 78.0) {
				continue;
			}

			int color = roll < 62 ? 0xFFFFFF : (roll < 88 ? 0xE8D0FF : SPARK);
			double brightness = 0.35 + (roll % 13) / 18.0;
			plot(pixels, x, y, color, brightness);
			if (roll > 82) {
				// A few brighter ones get a small cross glint.
				plot(pixels, x - 1, y, color, brightness * 0.45);
				plot(pixels, x + 1, y, color, brightness * 0.45);
				plot(pixels, x, y - 1, color, brightness * 0.45);
				plot(pixels, x, y + 1, color, brightness * 0.45);
			}
		}
	}

	// ------------------------------------------------------------------ subject

	private static void heart(int[] art, int originX, int originY) {
		for (int row = 0; row < HEART.length; row++) {
			for (int column = 0; column < HEART[row].length(); column++) {
				if (HEART[row].charAt(column) != 'X') {
					continue;
				}
				int color;
				if (row <= 1) {
					color = HEART_LIGHT;
				} else if (row <= 3) {
					color = HEART_MID;
				} else if (row <= 5) {
					color = HEART_BODY;
				} else if (row <= 7) {
					color = HEART_DEEP;
				} else {
					color = HEART_DARK;
				}
				set(art, originX + column, originY + row, color);
			}
		}
		// The classic highlight on the upper left lobe, same as the icon this replaces.
		set(art, originX + 2, originY + 1, HIGHLIGHT);
		set(art, originX + 3, originY + 1, HIGHLIGHT);
		set(art, originX + 2, originY + 2, HIGHLIGHT);
	}

	private static void arrows(int[] art) {
		stamp(art, 13, 1, 0);    // top, pointing down
		stamp(art, 13, 24, 2);   // bottom, pointing up
		stamp(art, 1, 13, 1);    // left, pointing right
		stamp(art, 24, 13, 3);   // right, pointing left
	}

	/** Stamps the arrow rotated by {@code quarterTurns} clockwise, tip shaded towards cyan. */
	private static void stamp(int[] art, int originX, int originY, int quarterTurns) {
		int height = ARROW.length;
		int width = ARROW[0].length();
		for (int row = 0; row < height; row++) {
			for (int column = 0; column < width; column++) {
				if (ARROW[row].charAt(column) != 'X') {
					continue;
				}
				int color = row == height - 1 ? ARROW_CORE
					: (row == 0 ? ARROW_DEEP : (row < 3 ? ARROW_BODY : ARROW_LIGHT));
				int x;
				int y;
				switch (quarterTurns) {
					case 1 -> {
						x = originX + row;
						y = originY + column;
					}
					case 2 -> {
						x = originX + (width - 1 - column);
						y = originY + (height - 1 - row);
					}
					case 3 -> {
						x = originX + (height - 1 - row);
						y = originY + (width - 1 - column);
					}
					default -> {
						x = originX + column;
						y = originY + row;
					}
				}
				set(art, x, y, color);
			}
		}
	}

	/** Wraps every filled cell in the dark rim that makes the sprite read against the nebula. */
	private static void outline(int[] art) {
		int[] copy = art.clone();
		for (int y = 0; y < GRID; y++) {
			for (int x = 0; x < GRID; x++) {
				if (copy[y * GRID + x] != 0) {
					continue;
				}
				boolean touching = filled(copy, x - 1, y) || filled(copy, x + 1, y)
					|| filled(copy, x, y - 1) || filled(copy, x, y + 1);
				if (touching) {
					set(art, x, y, OUTLINE);
				}
			}
		}
	}

	private static void blit(int[] pixels, int[] art) {
		for (int y = 0; y < GRID; y++) {
			for (int x = 0; x < GRID; x++) {
				int color = art[y * GRID + x];
				if (color == 0) {
					continue;
				}
				// Soft drop shadow one real pixel out, so the sprite lifts off the background.
				for (int sy = 0; sy < SCALE; sy++) {
					for (int sx = 0; sx < SCALE; sx++) {
						pixels[(y * SCALE + sy) * SIZE + x * SCALE + sx] = 0xFF000000 | (color & 0xFFFFFF);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ frame

	private static void frame(int[] pixels) {
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double distance = roundedRectDistance(x + 0.5, y + 0.5);
				int index = y * SIZE + x;
				if (distance > 0.0) {
					int alpha = (int)Math.round(255 * clamp(1.0 - distance, 0.0, 1.0));
					pixels[index] = alpha << 24 | (FRAME_OUTER & 0xFFFFFF);
				} else if (distance > -3.0) {
					pixels[index] = 0xFF000000 | FRAME_OUTER;
				} else if (distance > -6.0) {
					pixels[index] = 0xFF000000 | FRAME_INNER;
				} else if (distance > -8.0) {
					pixels[index] = 0xFF000000 | mix(pixels[index] & 0xFFFFFF, FRAME_INNER, 0.45);
				}
			}
		}
	}

	/** Positive outside the rounded square, negative inside. */
	private static double roundedRectDistance(double x, double y) {
		double half = SIZE / 2.0;
		double dx = Math.abs(x - half) - (half - CORNER_RADIUS);
		double dy = Math.abs(y - half) - (half - CORNER_RADIUS);
		double outside = Math.hypot(Math.max(dx, 0.0), Math.max(dy, 0.0));
		return outside + Math.min(Math.max(dx, dy), 0.0) - CORNER_RADIUS;
	}

	// ------------------------------------------------------------------ helpers

	private static boolean filled(int[] art, int x, int y) {
		return x >= 0 && y >= 0 && x < GRID && y < GRID && art[y * GRID + x] != 0;
	}

	private static void set(int[] art, int x, int y, int color) {
		if (x >= 0 && y >= 0 && x < GRID && y < GRID) {
			art[y * GRID + x] = 0xFF000000 | color;
		}
	}

	private static void plot(int[] pixels, int x, int y, int color, double amount) {
		if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
			return;
		}
		int index = y * SIZE + x;
		pixels[index] = 0xFF000000 | mix(pixels[index] & 0xFFFFFF, color, clamp(amount, 0.0, 1.0));
	}

	private static int mix(int from, int to, double amount) {
		double t = clamp(amount, 0.0, 1.0);
		int r = (int)Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
		int g = (int)Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
		int b = (int)Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return r << 16 | g << 8 | b;
	}

	private static double clamp(double value, double min, double max) {
		return value < min ? min : (value > max ? max : value);
	}

	private static int next(int value) {
		value ^= value << 13;
		value ^= value >>> 17;
		value ^= value << 5;
		return value * 0x45D9F3B;
	}

	private static double fbm(double x, double y) {
		double sum = 0.0;
		double amplitude = 0.5;
		double frequency = 1.0;
		for (int octave = 0; octave < 4; octave++) {
			sum += valueNoise(x * frequency, y * frequency) * amplitude;
			frequency *= 2.07;
			amplitude *= 0.52;
		}
		return clamp(sum, 0.0, 1.0);
	}

	private static double valueNoise(double x, double y) {
		int x0 = (int)Math.floor(x);
		int y0 = (int)Math.floor(y);
		double fx = smooth(x - x0);
		double fy = smooth(y - y0);
		double top = lerp(hash(x0, y0), hash(x0 + 1, y0), fx);
		double bottom = lerp(hash(x0, y0 + 1), hash(x0 + 1, y0 + 1), fx);
		return lerp(top, bottom, fy);
	}

	private static double hash(int x, int y) {
		int h = next(x * 374761393 + y * 668265263 + 0xA53);
		return (h >>> 8 & 0xFFFF) / 65535.0;
	}

	private static double smooth(double t) {
		return t * t * (3.0 - 2.0 * t);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}
}
