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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

/**
 * Sidebar panel listing the items that should go into group storage.
 * Items can be added by searching here or by right-clicking them in the bank.
 * While this panel is open, the bank only shows the listed items.
 */
class GimListPanel extends PluginPanel
{
	/** Minimum number of characters before search results are shown. */
	static final int MIN_SEARCH_LENGTH = 3;

	/** One row in the list or in the search results. */
	static class Entry
	{
		final int itemId;
		final String name;
		/** Only used for search results: whether the item is already on the list. */
		final boolean listed;

		Entry(int itemId, String name)
		{
			this(itemId, name, true);
		}

		Entry(int itemId, String name, boolean listed)
		{
			this.itemId = itemId;
			this.name = name;
			this.listed = listed;
		}
	}

	private final GimLogoutReminderPlugin plugin;
	private final ItemManager itemManager;
	private final IconTextField searchField = new IconTextField();
	private final JPanel resultsContainer = new JPanel(new GridLayout(0, 1, 0, 4));
	private final JPanel listContainer = new JPanel(new GridLayout(0, 1, 0, 4));

	GimListPanel(GimLogoutReminderPlugin plugin, ItemManager itemManager)
	{
		// true = RuneLite wraps the panel in a scroll pane, so a long list scrolls instead of making the window taller
		super(true);
		this.plugin = plugin;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);

		JLabel title = new JLabel("Group storage list");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);

		JLabel hint = new JLabel("<html>Search below, or with the bank open right-click an item and choose "
				+ "<font color='#ff981f'>Add to GIM list</font>.<br><br>"
				+ "While this panel is open, your bank only shows these items.</html>");
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(Color.LIGHT_GRAY);

		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setPreferredSize(new Dimension(PANEL_WIDTH - 20, 30));
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}
		});

		resultsContainer.setOpaque(false);
		listContainer.setOpaque(false);

		JLabel listTitle = new JLabel("On your list");
		listTitle.setFont(FontManager.getRunescapeBoldFont());
		listTitle.setForeground(Color.WHITE);

		content.add(leftAligned(title));
		content.add(Box.createVerticalStrut(6));
		content.add(leftAligned(hint));
		content.add(Box.createVerticalStrut(10));
		content.add(leftAligned(searchField));
		content.add(Box.createVerticalStrut(6));
		content.add(leftAligned(resultsContainer));
		content.add(Box.createVerticalStrut(12));
		content.add(leftAligned(listTitle));
		content.add(Box.createVerticalStrut(6));
		content.add(leftAligned(listContainer));

		// Wrapper keeps everything at the top instead of stretching
		add(content, BorderLayout.NORTH);

		update(List.of());
	}

	/** Wraps a component so every row in the BoxLayout uses the same left edge and full width. */
	private static JPanel leftAligned(Component c)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.add(c, BorderLayout.CENTER);
		wrapper.setAlignmentX(LEFT_ALIGNMENT);
		return wrapper;
	}

	private void onSearchChanged()
	{
		String query = searchField.getText().trim();
		if (query.length() < MIN_SEARCH_LENGTH)
		{
			showResults(List.of(), query);
			return;
		}
		plugin.searchItems(query);
	}

	/** Re-run the current search, e.g. after the list changed. Swing thread. */
	void refreshSearch()
	{
		onSearchChanged();
	}

	/** Must be called on the Swing thread. Ignores results for an outdated query. */
	void showResults(List<Entry> results, String query)
	{
		if (!query.equals(searchField.getText().trim()))
		{
			return;
		}

		resultsContainer.removeAll();

		if (query.length() > 0 && query.length() < MIN_SEARCH_LENGTH)
		{
			resultsContainer.add(grayLabel("Type at least " + MIN_SEARCH_LENGTH + " letters."));
		}
		else if (query.length() >= MIN_SEARCH_LENGTH && results.isEmpty())
		{
			resultsContainer.add(grayLabel("No items found."));
		}

		for (Entry entry : results)
		{
			resultsContainer.add(createRow(entry, true));
		}

		resultsContainer.revalidate();
		resultsContainer.repaint();
	}

	/** Must be called on the Swing thread. */
	void update(List<Entry> entries)
	{
		listContainer.removeAll();

		if (entries.isEmpty())
		{
			listContainer.add(grayLabel("The list is empty."));
		}

		for (Entry entry : entries)
		{
			listContainer.add(createRow(entry, false));
		}

		listContainer.revalidate();
		listContainer.repaint();
	}

	private static JLabel grayLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(Color.GRAY);
		return label;
	}

	private JPanel createRow(Entry entry, boolean searchResult)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		itemManager.getImage(entry.itemId).addTo(icon);

		JLabel name = new JLabel(entry.name);
		name.setForeground(Color.WHITE);
		// Full name on hover, since long names get cut off
		name.setToolTipText(entry.name);
		// Don't let a long name ask for more width than the sidebar has. Otherwise the
		// sidebar grows and RuneLite shrinks the game area to make room.
		name.setPreferredSize(new Dimension(0, 32));

		// Search results show a plain "+" or check mark; only list rows get a real remove button
		JComponent action;
		if (searchResult)
		{
			JLabel mark = new JLabel(entry.listed ? "\u2713" : "+");
			mark.setFont(FontManager.getRunescapeBoldFont());
			mark.setForeground(entry.listed ? Color.GRAY : new Color(255, 152, 31));
			mark.setBorder(new EmptyBorder(0, 4, 0, 4));
			if (entry.listed)
			{
				mark.setToolTipText("Already on the list");
			}
			action = mark;
		}
		else
		{
			JButton remove = new JButton("X");
			remove.setToolTipText("Remove from list");
			remove.setFocusPainted(false);
			remove.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
			remove.addActionListener(e -> plugin.removeItemFromPanel(entry.itemId));
			action = remove;
		}

		row.add(icon, BorderLayout.WEST);
		row.add(name, BorderLayout.CENTER);
		row.add(action, BorderLayout.EAST);

		if (searchResult && !entry.listed)
		{
			// The whole row adds the item, not only the "+" button
			MouseAdapter click = new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					plugin.addItemFromPanel(entry.itemId);
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					row.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				}
			};
			// Labels with a tooltip swallow mouse events, so listen on them too
			for (Component c : new Component[]{row, icon, name, action})
			{
				c.addMouseListener(click);
				c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}
			row.setToolTipText("Add to list");
		}
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