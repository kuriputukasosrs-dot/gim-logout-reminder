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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel listing the items that should go into group storage.
 * While this panel is open, the bank only shows those items.
 */
class GimListPanel extends PluginPanel
{
	/** One row in the list. */
	static class Entry
	{
		final int itemId;
		final String name;

		Entry(int itemId, String name)
		{
			this.itemId = itemId;
			this.name = name;
		}
	}

	private final GimLogoutReminderPlugin plugin;
	private final ItemManager itemManager;
	private final JPanel listContainer = new JPanel(new GridLayout(0, 1, 0, 4));

	GimListPanel(GimLogoutReminderPlugin plugin, ItemManager itemManager)
	{
		super(false);
		this.plugin = plugin;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout(0, 6));
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(0, 0, 10, 0));

		JLabel title = new JLabel("Group storage list");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);

		JLabel hint = new JLabel("<html>With the bank open, right-click an item and choose "
			+ "<font color='#ff981f'>Add to GIM list</font>.<br><br>"
			+ "While this panel is open, your bank only shows these items.</html>");
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(Color.LIGHT_GRAY);

		header.add(title, BorderLayout.NORTH);
		header.add(hint, BorderLayout.CENTER);

		listContainer.setOpaque(false);
		// Wrapper keeps rows at their natural height instead of stretching
		JPanel listWrapper = new JPanel(new BorderLayout());
		listWrapper.setOpaque(false);
		listWrapper.add(listContainer, BorderLayout.NORTH);

		add(header, BorderLayout.NORTH);
		add(listWrapper, BorderLayout.CENTER);

		update(List.of());
	}

	/** Must be called on the Swing thread. */
	void update(List<Entry> entries)
	{
		listContainer.removeAll();

		if (entries.isEmpty())
		{
			JLabel empty = new JLabel("The list is empty.");
			empty.setForeground(Color.GRAY);
			listContainer.add(empty);
		}

		for (Entry entry : entries)
		{
			listContainer.add(createRow(entry));
		}

		listContainer.revalidate();
		listContainer.repaint();
	}

	private JPanel createRow(Entry entry)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		itemManager.getImage(entry.itemId).addTo(icon);

		JLabel name = new JLabel(entry.name);
		name.setForeground(Color.WHITE);

		JButton remove = new JButton("X");
		remove.setToolTipText("Remove from list");
		remove.setFocusPainted(false);
		remove.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
		remove.addActionListener(e -> plugin.removeItemFromPanel(entry.itemId));

		row.add(icon, BorderLayout.WEST);
		row.add(name, BorderLayout.CENTER);
		row.add(remove, BorderLayout.EAST);
		return row;
	}

	@Override
	public void onActivate()
	{
		plugin.onPanelVisibilityChanged(true);
	}

	@Override
	public void onDeactivate()
	{
		plugin.onPanelVisibilityChanged(false);
	}
}
