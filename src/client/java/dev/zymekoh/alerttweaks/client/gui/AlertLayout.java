package dev.zymekoh.alerttweaks.client.gui;

/**
 * Where everything on the settings screen goes for a given GUI size. Pure arithmetic with no
 * Minecraft classes, so {@link LayoutCheck} can prove it over every supported size at build time.
 */
public final class AlertLayout {
	public static final int MIN_WIDTH = 240;
	public static final int MIN_HEIGHT = 140;
	public static final int TABS = 5;
	public static final int TAB_SIZE = 22;
	public static final int TAB_GAP = 3;
	public static final int HEADER = 20;
	public static final int FOOTER = 22;
	/** Room the title takes at the left of the header. */
	public static final int TITLE_W = 118;

	public record Rect(int x, int y, int w, int h) {
		public int right() {
			return this.x + this.w;
		}

		public int bottom() {
			return this.y + this.h;
		}

		public boolean contains(double px, double py) {
			return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h;
		}

		public boolean overlaps(Rect o) {
			return this.x < o.right() && o.x < this.right() && this.y < o.bottom() && o.y < this.bottom();
		}

		public static final Rect EMPTY = new Rect(0, 0, 0, 0);
	}

	public final Rect frame;
	public final Rect header;
	public final Rect rail;
	public final Rect list;
	public final Rect preview;
	public final Rect reset;
	public final Rect done;
	public final Rect hint;
	/** The "Edit positions" button at the right of the header; labelled when wide enough. */
	public final Rect edit;
	public final boolean editLabel;
	/** Tabs run along the top of the body instead of down the left side. */
	public final boolean tabsOnTop;
	/** Tabs show their label next to the icon. */
	public final boolean tabLabels;

	private AlertLayout(Rect frame, Rect header, Rect rail, Rect list, Rect preview, Rect reset, Rect done, Rect hint, Rect edit,
		boolean editLabel, boolean tabsOnTop, boolean tabLabels) {
		this.edit = edit;
		this.editLabel = editLabel;
		this.frame = frame;
		this.header = header;
		this.rail = rail;
		this.list = list;
		this.preview = preview;
		this.reset = reset;
		this.done = done;
		this.hint = hint;
		this.tabsOnTop = tabsOnTop;
		this.tabLabels = tabLabels;
	}

	public static AlertLayout fit(int width, int height) {
		int margin = width >= 480 && height >= 260 ? 10 : 3;
		int fw = Math.min(width - margin * 2, 880);
		int fh = Math.min(height - margin * 2, 430);
		Rect frame = new Rect((width - fw) / 2, (height - fh) / 2, fw, fh);
		Rect header = new Rect(frame.x() + 1, frame.y() + 1, fw - 2, HEADER);
		int editRoom = header.w() - TITLE_W - 8;
		boolean editLabel = editRoom >= 104;
		int editW = editLabel ? Math.min(132, editRoom) : 20;
		Rect edit = new Rect(header.right() - 4 - editW, header.y() + 2, editW, 16);

		int buttonW = fw >= 300 ? 64 : 48;
		int by = frame.bottom() - FOOTER + 3;
		Rect reset = new Rect(frame.x() + 6, by, buttonW, 16);
		Rect done = new Rect(frame.right() - 6 - buttonW, by, buttonW, 16);
		Rect hint = new Rect(reset.right() + 6, by, Math.max(0, done.x() - reset.right() - 12), 16);

		int bx = frame.x() + 6;
		int bY = header.bottom() + 4;
		int bw = fw - 12;
		int bh = frame.bottom() - FOOTER - 2 - bY;

		int railTall = TABS * (TAB_SIZE + TAB_GAP);
		boolean onTop = bw < 400 || bh < railTall;
		Rect rail;
		boolean labels = false;
		if (onTop) {
			rail = new Rect(bx, bY, Math.min(bw, TABS * (TAB_SIZE + TAB_GAP) - TAB_GAP), TAB_SIZE);
			bY += TAB_SIZE + 4;
			bh -= TAB_SIZE + 4;
		} else {
			labels = bw >= 560;
			int rw = labels ? 96 : TAB_SIZE + 4;
			rail = new Rect(bx, bY, rw, Math.min(bh, railTall));
			bx += rw + 6;
			bw -= rw + 6;
		}

		Rect preview = Rect.EMPTY;
		if (bw >= 330 && bh >= 90) {
			int pw = Math.max(130, Math.min(250, Math.round(bw * 0.42F)));
			preview = new Rect(bx + bw - pw, bY, pw, bh);
			bw -= pw + 6;
		}
		Rect list = new Rect(bx, bY, bw, bh);
		return new AlertLayout(frame, header, rail, list, preview, reset, done, hint, edit, editLabel, onTop, labels);
	}

	/** The tab button at index {@code i}. */
	public Rect tab(int i) {
		if (this.tabsOnTop) {
			return new Rect(this.rail.x() + i * (TAB_SIZE + TAB_GAP), this.rail.y(), TAB_SIZE, TAB_SIZE);
		}
		return new Rect(this.rail.x(), this.rail.y() + i * (TAB_SIZE + TAB_GAP), this.rail.w(), TAB_SIZE);
	}
}
