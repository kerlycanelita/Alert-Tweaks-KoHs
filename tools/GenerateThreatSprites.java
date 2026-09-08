import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Draws the two threat-indicator sprites in the mod's purple/cyan palette. */
public final class GenerateThreatSprites {
	static final int SIZE = 32;

	// Palette, outside in.
	static final int OUTLINE = 0x140427;
	static final int DEEP = 0x3D0B6B;
	static final int PURPLE = 0x7B2FD1;
	static final int VIOLET = 0xB44BF5;
	static final int LIGHT = 0xE3B6FF;
	static final int CORE = 0x5EE9F7;
	static final int SPARK = 0x7DF3FF;

	public static void main(String[] args) throws Exception {
		File out = new File(args[0]);
		out.mkdirs();
		ImageIO.write(arrow(), "png", new File(out, "threat_arrow.png"));
		ImageIO.write(burst(), "png", new File(out, "threat_burst.png"));
		System.out.println("wrote 2 sprites to " + out.getAbsolutePath());
	}

	// ------------------------------------------------------------------ arrow

	private static BufferedImage arrow() {
		BufferedImage image = blank();
		// Solid arrowhead with a swallow-tail notch, matching the reference sheet.
		double[] shapeX = {16.0, 2.5, 16.0, 29.5};
		double[] shapeY = {2.0, 24.5, 18.0, 24.5};

		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double px = x + 0.5;
				double py = y + 0.5;
				if (!inside(shapeX, shapeY, px, py)) {
					continue;
				}
				double depth = borderDistance(shapeX, shapeY, px, py);
				image.setRGB(x, y, argb(255, rampArrow(depth)));
			}
		}

		// Cyan pilot dot in front of the tip and speed dashes on both flanks, like the reference art.
		dot(image, 15, 0, SPARK, 235);
		dot(image, 16, 0, SPARK, 235);
		dash(image, 1, 13, 3, PURPLE, 210);
		dash(image, 0, 19, 3, VIOLET, 165);
		dashBack(image, 30, 13, 3, PURPLE, 210);
		dashBack(image, 31, 19, 3, VIOLET, 165);
		return image;
	}

	/** Colour bands measured inwards from the silhouette, so the arrow keeps a dark rim and a bright core. */
	private static int rampArrow(double depth) {
		if (depth < 1.0) {
			return OUTLINE;
		}
		if (depth < 2.0) {
			return DEEP;
		}
		if (depth < 2.7) {
			return PURPLE;
		}
		if (depth < 3.6) {
			return VIOLET;
		}
		if (depth < 4.5) {
			return LIGHT;
		}
		return CORE;
	}

	// ------------------------------------------------------------------ burst

	private static BufferedImage burst() {
		BufferedImage image = blank();
		double center = 15.5;

		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double dx = x + 0.5 - center;
				double dy = y + 0.5 - center;
				double radius = Math.hypot(dx, dy);

				// Four-point star: the spikes are where one axis stays near zero.
				double spike = Math.min(Math.abs(dx), Math.abs(dy));
				double spikeReach = 14.5 * (1.0 - clamp(spike / 2.6, 0.0, 1.0));
				int color = -1;
				int alpha = 0;
				if (radius <= 4.2) {
					color = radius <= 1.6 ? 0xFFFFFF : (radius <= 2.8 ? LIGHT : VIOLET);
					alpha = 255;
				} else if (spikeReach > 4.0 && radius <= spikeReach) {
					double along = clamp((radius - 4.0) / Math.max(0.5, spikeReach - 4.0), 0.0, 1.0);
					color = along < 0.45 ? VIOLET : PURPLE;
					alpha = (int)Math.round(255 * (1.0 - along * along));
				} else if (radius >= 10.0 && radius <= 12.2) {
					// Dashed shockwave ring.
					double angle = Math.atan2(dy, dx);
					double segment = (angle + Math.PI) / (Math.PI * 2.0) * 16.0;
					if (segment - Math.floor(segment) < 0.62) {
						color = CORE;
						alpha = 165;
					}
				}
				if (alpha > 0) {
					image.setRGB(x, y, argb(clampInt(alpha, 0, 255), color));
				}
			}
		}
		return image;
	}

	// ------------------------------------------------------------------ helpers

	private static BufferedImage blank() {
		return new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
	}

	private static void dot(BufferedImage image, int x, int y, int color, int alpha) {
		if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) {
			image.setRGB(x, y, argb(alpha, color));
		}
	}

	/** Short diagonal tick, drawn top-left to bottom-right. */
	private static void dash(BufferedImage image, int x, int y, int length, int color, int alpha) {
		for (int i = 0; i < length; i++) {
			dot(image, x + i, y + i, color, alpha);
		}
	}

	/** Mirror of {@link #dash}, for the right-hand flank. */
	private static void dashBack(BufferedImage image, int x, int y, int length, int color, int alpha) {
		for (int i = 0; i < length; i++) {
			dot(image, x - i, y + i, color, alpha);
		}
	}

	private static boolean inside(double[] xs, double[] ys, double px, double py) {
		boolean in = false;
		for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
			boolean straddles = ys[i] > py != ys[j] > py;
			if (straddles && px < (xs[j] - xs[i]) * (py - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
				in = !in;
			}
		}
		return in;
	}

	private static double borderDistance(double[] xs, double[] ys, double px, double py) {
		double best = Double.MAX_VALUE;
		for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
			best = Math.min(best, segmentDistance(px, py, xs[j], ys[j], xs[i], ys[i]));
		}
		return best;
	}

	private static int argb(int alpha, int rgb) {
		return (alpha & 0xFF) << 24 | (rgb & 0xFFFFFF);
	}

	private static double segmentDistance(double px, double py, double ax, double ay, double bx, double by) {
		double vx = bx - ax;
		double vy = by - ay;
		double length = vx * vx + vy * vy;
		double t = length == 0.0 ? 0.0 : clamp(((px - ax) * vx + (py - ay) * vy) / length, 0.0, 1.0);
		return Math.hypot(px - (ax + t * vx), py - (ay + t * vy));
	}

	private static double clamp(double value, double min, double max) {
		return value < min ? min : (value > max ? max : value);
	}

	private static int clampInt(int value, int min, int max) {
		return value < min ? min : (value > max ? max : value);
	}
}
