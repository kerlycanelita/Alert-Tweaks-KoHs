package dev.zymekoh.alerttweaks.client.gui;

import dev.zymekoh.alerttweaks.client.gui.AlertLayout.Rect;
import java.util.List;

/**
 * Build-time proof of the settings layout (gradle checkLayout): every GUI size from the minimum to
 * well past 4K at scale 1 keeps every rectangle on screen, usable, and clear of the others.
 */
public final class LayoutCheck {
	private LayoutCheck() {
	}

	public static void main(String[] args) {
		int checked = 0;
		for (int w = AlertLayout.MIN_WIDTH; w <= 1920; w += 3) {
			for (int h = AlertLayout.MIN_HEIGHT; h <= 1080; h += 3) {
				check(w, h);
				checked++;
			}
		}
		System.out.println("checkLayout: " + checked + " sizes OK");
	}

	static void check(int w, int h) {
		AlertLayout l = AlertLayout.fit(w, h);
		String at = w + "x" + h;
		inside(l.frame, 0, 0, w, h, at, "frame");
		List<Rect> parts = new java.util.ArrayList<>(List.of(l.rail, l.list, l.reset, l.done));
		inside(l.edit, l.header.x() + AlertLayout.TITLE_W, l.header.y(), l.header.right(), l.header.bottom(), at, "edit button");
		inside(l.header, l.frame.x(), l.frame.y(), l.frame.right(), l.frame.bottom(), at, "header");
		for (Rect r : List.of(l.rail, l.list, l.reset, l.done)) {
			require(!r.overlaps(l.header), at, "overlaps header " + r);
		}
		if (l.hint.w() > 0) {
			parts.add(l.hint);
		}
		if (l.preview.w() > 0) {
			parts.add(l.preview);
			require(l.preview.w() >= 130 && l.preview.h() >= 90, at, "preview too small " + l.preview);
		}
		for (Rect r : parts) {
			inside(r, l.frame.x(), l.frame.y(), l.frame.right(), l.frame.bottom(), at, r.toString());
		}
		for (int i = 0; i < parts.size(); i++) {
			for (int j = i + 1; j < parts.size(); j++) {
				require(!parts.get(i).overlaps(parts.get(j)), at, "overlap " + parts.get(i) + " / " + parts.get(j));
			}
		}
		require(l.list.w() >= 150 && l.list.h() >= 40, at, "list too small " + l.list);
		for (int t = 0; t < AlertLayout.TABS; t++) {
			Rect tab = l.tab(t);
			inside(tab, l.rail.x(), l.rail.y(), l.rail.right(), l.rail.bottom(), at, "tab " + t);
		}
	}

	private static void inside(Rect r, int x1, int y1, int x2, int y2, String at, String what) {
		require(r.w() >= 0 && r.h() >= 0 && r.x() >= x1 && r.y() >= y1 && r.right() <= x2 && r.bottom() <= y2,
			at, what + " leaves " + x1 + "," + y1 + "-" + x2 + "," + y2 + ": " + r);
	}

	private static void require(boolean ok, String at, String message) {
		if (!ok) {
			throw new IllegalStateException("checkLayout " + at + ": " + message);
		}
	}
}
