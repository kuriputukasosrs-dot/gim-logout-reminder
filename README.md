# GIM Logout Reminder

A RuneLite plugin for Group Ironman players that stops you from logging out while you still have items that belong in group storage.

## Features

- **Group storage list.** With the bank open, right-click an item (in the bank or your inventory) and choose **Add to GIM list**.
- **Sidebar panel.** The chest icon on the right opens the list. Remove items with the **X** button.
- **Filtered bank.** While the panel is open, your bank only shows the items on the list (uses the Bank Tags tag `gim`).
- **Logout check.** When you click *Click here to logout* (or the world switcher's Logout button), the plugin checks your inventory, equipment and bank:
  - Nothing from the list on you: you log out normally, no questions asked.
  - Something left: a box shows the items as icons (hover for name and location).
    - **Logout anyway** lets your next Logout click through.
    - **Cancel** opens the list panel so you can go deposit them.
- **Never in the way when it matters.** The plugin does nothing in the Wilderness, on PvP worlds (configurable) or on non-GIM accounts. Clicks outside the box still reach the game, so you can eat, move and fight while it is open.

## Settings

| Setting | Default | Description |
|---|---|---|
| Logout window after "Logout anyway" | 15 s | How long the next Logout click goes through without asking |
| Skip on PvP worlds | On | Don't check on PvP worlds |

## Notes

- The plugin never logs you out by itself. You always click Logout yourself.
- Bank contents are only known after you open the bank. The plugin remembers the amounts of listed items per account, so the check also works right after logging in.
