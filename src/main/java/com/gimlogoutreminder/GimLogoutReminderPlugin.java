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

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "GIM Logout Reminder",
	description = "Asks whether you put your items in group storage before logging out, and keeps a list of items to deposit",
	tags = {"group", "ironman", "gim", "storage", "logout", "reminder", "bank"}
)
@PluginDependency(BankTagsPlugin.class)
public class GimLogoutReminderPlugin extends Plugin
{
	static final String CONFIG_GROUP = "gimlogoutreminder";
	private static final String ITEMS_KEY = "storageItems";
	/** Last known bank contents, saved per account so the check works before the bank is opened. */
	private static final String BANK_KEY = "lastKnownBank";

	/** Bank tag used to filter the bank. Also usable by hand: search the bank for tag:gim */
	static final String BANK_TAG = "gim";

	private static final String ADD_OPTION = "Add to GIM list";
	private static final String REMOVE_OPTION = "Remove from GIM list";

	// Account type varbit values for the three Group Ironman modes
	private static final int ACCOUNT_TYPE_GIM = 4;
	private static final int ACCOUNT_TYPE_UNRANKED_GIM = 6;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private GimLogoutReminderConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private TagManager tagManager;

	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private GimLogoutDialogOverlay dialogOverlay;

	/** Canonical item ids on the list. Only touched on the client thread. */
	private final Set<Integer> listedItems = new LinkedHashSet<>();

	private GimListPanel panel;
	private NavigationButton navButton;
	private volatile boolean panelOpen;

	/** System time (ms) until which Logout works without asking. Set when the player answers Yes. */
	private volatile long unlockedUntil;

	private volatile boolean dialogOpen;

	/** Items shown in the logout dialog. */
	private volatile List<DialogItem> dialogItems = Collections.emptyList();

	/** A listed item the player still has, with the total quantity found. */
	static class DialogItem
	{
		final int itemId;
		final int quantity;
		final String name;
		/** Where it was found, e.g. "bank: 1, inventory: 2" */
		final String where;

		DialogItem(int itemId, int quantity, String name, String where)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.name = name;
			this.where = where;
		}
	}

	@Provides
	GimLogoutReminderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GimLogoutReminderConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new GimListPanel(this, itemManager);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("GIM storage list")
			.icon(icon)
			.priority(10)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlayManager.add(dialogOverlay);
		mouseManager.registerMouseListener(dialogOverlay);

		clientThread.invokeLater(() ->
		{
			loadItems();
			// Make sure the bank tag matches the saved list
			for (int id : listedItems)
			{
				tagManager.addTag(id, BANK_TAG, false);
			}
			refreshPanel();
		});
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(dialogOverlay);
		mouseManager.unregisterMouseListener(dialogOverlay);
		clientThread.invokeLater(this::closeBankFilter);
		panelOpen = false;
		unlockedUntil = 0;
		dialogOpen = false;
	}

	// ---------------------------------------------------------------------
	// Logout dialog
	// ---------------------------------------------------------------------

	boolean isDialogOpen()
	{
		return dialogOpen;
	}

	List<DialogItem> getDialogItems()
	{
		return dialogItems;
	}

	/**
	 * Called from the overlay (Swing thread) when a dialog button is clicked.
	 * logoutAnyway = true: the next Logout click goes through. false (Cancel): open the list panel.
	 */
	void answer(boolean logoutAnyway)
	{
		dialogOpen = false;
		if (logoutAnyway)
		{
			unlockedUntil = System.currentTimeMillis() + config.unlockSeconds() * 1000L;
		}
		else
		{
			// Open the list panel on the right so the player sees what to deposit
			SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			dialogOpen = false;
			unlockedUntil = 0;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = Text.removeTags(event.getMenuOption());
		if (!"Logout".equalsIgnoreCase(option))
		{
			return;
		}

		// The door tab icon is also called "Logout" but only opens the panel.
		// Only react to the real logout buttons inside the logout panel / world switcher.
		int group = WidgetUtil.componentToInterface(event.getParam1());
		if (group != InterfaceID.LOGOUT && group != InterfaceID.WORLDSWITCHER)
		{
			return;
		}

		if (!isGroupIronman())
		{
			return;
		}

		// Never block an escape from PKers
		if (isInWilderness() || (config.skipOnPvpWorlds() && isPvpWorld()))
		{
			dialogOpen = false;
			return;
		}

		if (System.currentTimeMillis() < unlockedUntil)
		{
			// Player already answered Yes: this click logs out normally
			unlockedUntil = 0;
			return;
		}

		List<DialogItem> items = findUndepositedItems();
		if (items.isEmpty())
		{
			// Nothing on the list is in your inventory, equipment or bank: log out without asking
			return;
		}

		// Block this logout click and show what is left. The player clicks Logout again themselves.
		event.consume();
		dialogItems = items;
		dialogOpen = true;
	}

	/** Client thread only. Listed items found in inventory, equipment and bank, in list order. */
	private List<DialogItem> findUndepositedItems()
	{
		if (listedItems.isEmpty())
		{
			return Collections.emptyList();
		}

		Map<Integer, Long> inventory = new LinkedHashMap<>();
		Map<Integer, Long> equipment = new LinkedHashMap<>();
		Map<Integer, Long> bankItems = new LinkedHashMap<>();
		addListedItems(client.getItemContainer(InventoryID.INV), inventory);
		addListedItems(client.getItemContainer(InventoryID.WORN), equipment);

		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		String bankLabel = "bank";
		if (bank != null)
		{
			addListedItems(bank, bankItems);
		}
		else
		{
			// Bank not opened this session: use what we saw last time
			bankLabel = "bank (last seen)";
			for (Map.Entry<Integer, Long> e : loadLastKnownBank().entrySet())
			{
				if (listedItems.contains(e.getKey()))
				{
					bankItems.merge(e.getKey(), e.getValue(), Long::sum);
				}
			}
		}

		List<DialogItem> result = new ArrayList<>();
		for (int id : listedItems)
		{
			long inv = inventory.getOrDefault(id, 0L);
			long worn = equipment.getOrDefault(id, 0L);
			long banked = bankItems.getOrDefault(id, 0L);
			long total = inv + worn + banked;
			if (total <= 0)
			{
				continue;
			}

			List<String> parts = new ArrayList<>();
			if (inv > 0)
			{
				parts.add("inventory: " + inv);
			}
			if (worn > 0)
			{
				parts.add("worn: " + worn);
			}
			if (banked > 0)
			{
				parts.add(bankLabel + ": " + banked);
			}

			int q = (int) Math.min(total, Integer.MAX_VALUE);
			result.add(new DialogItem(id, q, itemManager.getItemComposition(id).getName(), String.join(", ", parts)));
		}
		return result;
	}

	private void addListedItems(ItemContainer container, Map<Integer, Long> totals)
	{
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0 || isPlaceholder(item.getId()))
			{
				continue; // empty slot or bank placeholder
			}
			int canonical = itemManager.canonicalize(item.getId());
			if (listedItems.contains(canonical))
			{
				totals.merge(canonical, (long) item.getQuantity(), Long::sum);
			}
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK)
		{
			return;
		}

		saveBankSnapshot(event.getItemContainer());
	}

	/**
	 * Remember how many of each listed item is in the bank, so the logout check works
	 * next session before the bank has been opened. Only listed items are stored.
	 */
	private void saveBankSnapshot(ItemContainer bankContainer)
	{
		Map<Integer, Long> bank = new LinkedHashMap<>();
		addListedItems(bankContainer, bank);
		String value = bank.entrySet().stream()
			.map(e -> e.getKey() + ":" + e.getValue())
			.collect(Collectors.joining(","));
		configManager.setRSProfileConfiguration(CONFIG_GROUP, BANK_KEY, value);
	}

	/** Bank placeholders are separate item ids whose composition points back to the real item. */
	private boolean isPlaceholder(int itemId)
	{
		return itemManager.getItemComposition(itemId).getPlaceholderTemplateId() != -1;
	}

	private Map<Integer, Long> loadLastKnownBank()
	{
		Map<Integer, Long> bank = new LinkedHashMap<>();
		String saved = configManager.getRSProfileConfiguration(CONFIG_GROUP, BANK_KEY);
		if (saved == null || saved.isEmpty())
		{
			return bank;
		}
		for (String part : saved.split(","))
		{
			String[] kv = part.split(":");
			if (kv.length != 2)
			{
				continue;
			}
			try
			{
				bank.put(Integer.parseInt(kv[0]), Long.parseLong(kv[1]));
			}
			catch (NumberFormatException ignored)
			{
			}
		}
		return bank;
	}

	// ---------------------------------------------------------------------
	// Item list
	// ---------------------------------------------------------------------

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		// Add our option next to "Examine" on items in the bank
		if (!"Examine".equals(Text.removeTags(event.getOption())))
		{
			return;
		}

		MenuEntry entry = event.getMenuEntry();
		Widget widget = entry.getWidget();
		if (widget == null)
		{
			return;
		}

		// Only while the bank is open: bank items and the inventory shown next to the bank
		int group = WidgetUtil.componentToInterface(widget.getId());
		if (group != InterfaceID.BANKMAIN && group != InterfaceID.BANKSIDE)
		{
			return;
		}

		int itemId = widget.getItemId();
		if (itemId <= 0)
		{
			return;
		}

		int canonical = itemManager.canonicalize(itemId);
		boolean listed = listedItems.contains(canonical);

		client.createMenuEntry(-1)
			.setOption(listed ? REMOVE_OPTION : ADD_OPTION)
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(e -> toggleItem(canonical));
	}

	/** Called from the panel's remove button (Swing thread). */
	void removeItemFromPanel(int itemId)
	{
		clientThread.invokeLater(() ->
		{
			if (listedItems.contains(itemId))
			{
				toggleItem(itemId);
			}
		});
	}

	/** Client thread only. */
	private void toggleItem(int itemId)
	{
		if (listedItems.remove(itemId))
		{
			tagManager.removeTag(itemId, BANK_TAG);
		}
		else
		{
			listedItems.add(itemId);
			tagManager.addTag(itemId, BANK_TAG, false);
		}
		saveItems();
		refreshPanel();

		// The list changed, so update the saved bank amounts too (the bank is open when items are added)
		ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
		if (bankContainer != null)
		{
			saveBankSnapshot(bankContainer);
		}

		// Refresh the filtered bank view so the change shows immediately
		if (panelOpen && isBankOpen())
		{
			openBankFilter();
		}
	}

	private void loadItems()
	{
		listedItems.clear();
		String saved = configManager.getConfiguration(CONFIG_GROUP, ITEMS_KEY);
		if (saved == null || saved.isEmpty())
		{
			return;
		}
		for (String part : saved.split(","))
		{
			try
			{
				listedItems.add(Integer.parseInt(part.trim()));
			}
			catch (NumberFormatException ignored)
			{
			}
		}
	}

	private void saveItems()
	{
		String value = listedItems.stream().map(String::valueOf).collect(Collectors.joining(","));
		configManager.setConfiguration(CONFIG_GROUP, ITEMS_KEY, value);
	}

	/** Client thread only: item names must be looked up there. */
	private void refreshPanel()
	{
		List<GimListPanel.Entry> entries = new ArrayList<>();
		for (int id : listedItems)
		{
			entries.add(new GimListPanel.Entry(id, itemManager.getItemComposition(id).getName()));
		}
		SwingUtilities.invokeLater(() -> panel.update(entries));
	}

	// ---------------------------------------------------------------------
	// Bank filter: only active while the panel is open
	// ---------------------------------------------------------------------

	/** Called by the panel (Swing thread) when it is shown or hidden. */
	void onPanelVisibilityChanged(boolean open)
	{
		panelOpen = open;
		clientThread.invokeLater(() ->
		{
			if (open)
			{
				if (isBankOpen())
				{
					openBankFilter();
				}
			}
			else
			{
				closeBankFilter();
			}
		});
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && panelOpen)
		{
			// Wait until the bank has finished building before filtering it
			clientThread.invokeLater(this::openBankFilter);
		}
	}

	private boolean isBankOpen()
	{
		return client.getWidget(InterfaceID.BANKMAIN, 0) != null;
	}

	private void openBankFilter()
	{
		bankTagsService.openBankTag(BANK_TAG, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
	}

	private void closeBankFilter()
	{
		bankTagsService.closeBankTag();
	}

	// ---------------------------------------------------------------------
	// Checks
	// ---------------------------------------------------------------------

	private boolean isGroupIronman()
	{
		int type = client.getVarbitValue(VarbitID.IRONMAN);
		return type >= ACCOUNT_TYPE_GIM && type <= ACCOUNT_TYPE_UNRANKED_GIM;
	}

	private boolean isPvpWorld()
	{
		return client.getWorldType().contains(WorldType.PVP);
	}

	private boolean isInWilderness()
	{
		if (client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1)
		{
			return true;
		}

		// Fallback: coordinate check in case the varbit ever changes
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return false;
		}
		WorldPoint p = player.getWorldLocation();
		int x = p.getX();
		int y = p.getY();
		boolean surface = x >= 2944 && x <= 3391 && y >= 3520 && y <= 3967;
		boolean underground = x >= 2944 && x <= 3455 && y >= 9920 && y <= 10879;
		return surface || underground;
	}
}
