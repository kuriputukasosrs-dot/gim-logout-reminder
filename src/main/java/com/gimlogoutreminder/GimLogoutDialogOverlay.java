/*
 * Copyright (c) 2026, Bad Pick
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gimlogoutreminder;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * OSRS-style box in the middle of the game window listing the items that still need to go
 * into group storage, with "Logout anyway" and "Cancel" buttons.
 */
class GimLogoutDialogOverlay extends Overlay implements MouseListener
{
	// Colours taken from the OSRS interface style
	private static final Color BG = new Color(62, 53, 41);
	private static final Color BORDER_OUTER = Color.BLACK;
	private static final Color BORDER_INNER = new Color(108, 96, 77);
	private static final Color BUTTON_BG = new Color(76, 67, 54);
	private static final Color BUTTON_BG_HOVER = new Color(101, 89, 70);
	private static final Color SLOT_HOVER = new Color(255, 255, 255, 40);
	private static final Color ORANGE = new Color(255, 152, 31);
	private static final Color SHADOW = Color.BLACK;

	private static final String TITLE = "Still to put in group storage";
	private static final String LOGOUT_TEXT = "Logout anyway";
	private static final String CANCEL_TEXT = "Cancel";

	private static final int PADDING = 16;
	private static final int ICON_W = 36;
	private static final int ICON_H = 32;
	private static final int ICONS_PER_ROW = 8;
	private static final int BUTTON_W = 120;
	private static final int BUTTON_H = 26;
	private static final int BUTTON_GAP = 16;
	private static final int CLOSE_SIZE = 15;

	private final Client client;
	private final GimLogoutReminderPlugin plugin;
	private final ItemManager itemManager;
	private final TooltipManager tooltipManager;

	// Written on the client thread (render), read on the AWT thread (mouse)
	private volatile Rectangle boxBounds;
	private volatile Rectangle logoutBounds;
	private volatile Rectangle cancelBounds;
	private volatile Rectangle closeBounds;
	private volatile boolean swallowNextRelease;

	@Inject
	GimLogoutDialogOverlay(Client client, GimLogoutReminderPlugin plugin, ItemManager itemManager, TooltipManager tooltipManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.tooltipManager = tooltipManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!plugin.isDialogOpen())
		{
			boxBounds = logoutBounds = cancelBounds = closeBounds = null;
			return null;
		}

		List<GimLogoutReminderPlugin.DialogItem> items = plugin.getDialogItems();
		int cols = Math.max(1, Math.min(items.size(), ICONS_PER_ROW));
		int rows = Math.max(1, (items.size() + ICONS_PER_ROW - 1) / ICONS_PER_ROW);

		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics titleFm = g.getFontMetrics();

		int buttonsWidth = BUTTON_W * 2 + BUTTON_GAP;
		int gridWidth = cols * ICON_W;
		int contentWidth = Math.max(buttonsWidth, Math.max(gridWidth, titleFm.stringWidth(TITLE) + CLOSE_SIZE * 2));
		int width = contentWidth + PADDING * 2;
		int height = PADDING + titleFm.getHeight() + 10 + rows * ICON_H + 14 + BUTTON_H + PADDING;

		int x = (client.getCanvasWidth() - width) / 2;
		int y = (client.getCanvasHeight() - height) / 2;
		Point mouse = client.getMouseCanvasPosition();

		// Box with OSRS-style double border
		g.setColor(BG);
		g.fillRect(x, y, width, height);
		g.setColor(BORDER_OUTER);
		g.drawRect(x, y, width - 1, height - 1);
		g.setColor(BORDER_INNER);
		g.drawRect(x + 1, y + 1, width - 3, height - 3);
		g.setColor(BORDER_OUTER);
		g.drawRect(x + 2, y + 2, width - 5, height - 5);

		// Close (X) button
		Rectangle close = new Rectangle(x + width - CLOSE_SIZE - 6, y + 6, CLOSE_SIZE, CLOSE_SIZE);
		drawCloseButton(g, close, mouse);

		// Title
		int ty = y + PADDING + titleFm.getAscent();
		drawShadowed(g, TITLE, x + (width - titleFm.stringWidth(TITLE)) / 2, ty, ORANGE);

		// Item icons, centred, name shown as tooltip on hover
		int gridX = x + (width - gridWidth) / 2;
		int gridY = y + PADDING + titleFm.getHeight() + 10;
		for (int i = 0; i < items.size(); i++)
		{
			GimLogoutReminderPlugin.DialogItem item = items.get(i);
			int ix = gridX + (i % ICONS_PER_ROW) * ICON_W;
			int iy = gridY + (i / ICONS_PER_ROW) * ICON_H;
			Rectangle slot = new Rectangle(ix, iy, ICON_W, ICON_H);

			boolean hover = mouse != null && slot.contains(mouse.getX(), mouse.getY());
			if (hover)
			{
				g.setColor(SLOT_HOVER);
				g.fillRect(slot.x, slot.y, slot.width, slot.height);
				String tip = item.name + "</br>" + item.where;
				tooltipManager.add(new Tooltip(tip));
			}

			BufferedImage image = itemManager.getImage(item.itemId, item.quantity, item.quantity > 1);
			if (image != null)
			{
				g.drawImage(image, ix + (ICON_W - image.getWidth()) / 2, iy + (ICON_H - image.getHeight()) / 2, null);
			}
		}

		// Buttons
		int by = y + height - PADDING - BUTTON_H;
		int bx = x + (width - buttonsWidth) / 2;
		Rectangle logout = new Rectangle(bx, by, BUTTON_W, BUTTON_H);
		Rectangle cancel = new Rectangle(bx + BUTTON_W + BUTTON_GAP, by, BUTTON_W, BUTTON_H);
		drawButton(g, logout, LOGOUT_TEXT, mouse);
		drawButton(g, cancel, CANCEL_TEXT, mouse);

		boxBounds = new Rectangle(x, y, width, height);
		logoutBounds = logout;
		cancelBounds = cancel;
		closeBounds = close;
		return null;
	}

	private void drawButton(Graphics2D g, Rectangle r, String text, Point mouse)
	{
		boolean hover = mouse != null && r.contains(mouse.getX(), mouse.getY());
		g.setColor(hover ? BUTTON_BG_HOVER : BUTTON_BG);
		g.fillRect(r.x, r.y, r.width, r.height);
		g.setColor(BORDER_OUTER);
		g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
		g.setColor(BORDER_INNER);
		g.drawRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3);

		g.setFont(FontManager.getRunescapeFont());
		FontMetrics fm = g.getFontMetrics();
		int tx = r.x + (r.width - fm.stringWidth(text)) / 2;
		int ty = r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent();
		drawShadowed(g, text, tx, ty, hover ? Color.WHITE : ORANGE);
	}

	private void drawCloseButton(Graphics2D g, Rectangle r, Point mouse)
	{
		boolean hover = mouse != null && r.contains(mouse.getX(), mouse.getY());
		g.setColor(hover ? BUTTON_BG_HOVER : BUTTON_BG);
		g.fillRect(r.x, r.y, r.width, r.height);
		g.setColor(BORDER_OUTER);
		g.drawRect(r.x, r.y, r.width - 1, r.height - 1);

		int m = 4;
		g.setColor(SHADOW);
		g.drawLine(r.x + m + 1, r.y + m + 1, r.x + r.width - m, r.y + r.height - m);
		g.drawLine(r.x + r.width - m, r.y + m + 1, r.x + m + 1, r.y + r.height - m);
		g.setColor(hover ? Color.WHITE : ORANGE);
		g.drawLine(r.x + m, r.y + m, r.x + r.width - m - 1, r.y + r.height - m - 1);
		g.drawLine(r.x + r.width - m - 1, r.y + m, r.x + m, r.y + r.height - m - 1);
	}

	private static void drawShadowed(Graphics2D g, String text, int x, int y, Color color)
	{
		g.setColor(SHADOW);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	// ---- Mouse handling (AWT thread) ----

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		if (!plugin.isDialogOpen() || boxBounds == null)
		{
			return e;
		}

		java.awt.Point p = e.getPoint();
		if (logoutBounds != null && logoutBounds.contains(p))
		{
			plugin.answer(true);
			return swallow(e);
		}
		if ((cancelBounds != null && cancelBounds.contains(p)) || (closeBounds != null && closeBounds.contains(p)))
		{
			plugin.answer(false);
			return swallow(e);
		}
		if (boxBounds.contains(p))
		{
			// Click on the box itself: don't let it through to the game
			return swallow(e);
		}

		// Click somewhere else in the game: the dialog stays open, but the click goes through
		// so you can still eat, move or fight while it is showing
		return e;
	}

	private MouseEvent swallow(MouseEvent e)
	{
		swallowNextRelease = true;
		e.consume();
		return e;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		if (swallowNextRelease)
		{
			swallowNextRelease = false;
			e.consume();
		}
		return e;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent e)
	{
		if (plugin.isDialogOpen() && boxBounds != null && boxBounds.contains(e.getPoint()))
		{
			e.consume();
		}
		return e;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		return e;
	}
}
