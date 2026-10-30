# GIM Logout Reminder

A RuneLite plugin for Group Ironman players that stops you from logging out while you still have items that belong in group storage.

## Features

- **Group storage list.** With the bank open, right-click an item (in the bank or your inventory) and choose **Add to GIM list**.
  
  <img width="198" height="170" alt="image" src="https://github.com/user-attachments/assets/038d1257-0973-4616-a921-d97ffea98b99" />
  <img width="216" height="169" alt="image" src="https://github.com/user-attachments/assets/759b9258-5a91-4d68-aa77-1ff2152085fd" />
- **Sidebar panel.** The chest icon on the right opens the list. Remove items with the **X** button.
  
  <img width="28" height="22" alt="image" src="https://github.com/user-attachments/assets/ef0c68ad-bcb9-4af1-9667-ef05e1d1783f" />
  <img width="240" height="152" alt="image" src="https://github.com/user-attachments/assets/b53539e6-f6d2-48e3-a722-9e9e1444b859" />

- **Filtered bank.** While the panel is open, your bank only shows the items on the list (uses the Bank Tags tag `gim`).
- **Logout check.** When you click *Click here to logout* (or the world switcher's Logout button), the plugin checks your inventory, equipment and bank:
  - Nothing from the list on you: you log out normally, no questions asked.
  - Something left: a box shows the items as icons (hover for name and location).
    - **Logout anyway** lets your next Logout click through.
    - **Cancel** opens the list panel so you can go deposit them.
      
      <img width="286" height="128" alt="image" src="https://github.com/user-attachments/assets/eb6c94ca-cb2e-46ae-8df2-87cd736de7db" />

- **Exceptions** The plugin does nothing in the Wilderness, on PvP worlds (configurable) or on non-GIM accounts. 

  <img width="231" height="154" alt="image" src="https://github.com/user-attachments/assets/041c2d8b-8e55-444c-a853-9018f8b163d0" />


## Settings

| Setting | Default | Description |
|---|---|---|
| Logout window after "Logout anyway" | 15 s | How long the next Logout click goes through without asking |
| Skip on PvP worlds | On | Don't check on PvP worlds |

## Notes

- The plugin never logs you out by itself. You always click Logout yourself.
- Bank contents are only known after you open the bank. The plugin remembers the amounts of listed items per account, so the check also works right after logging in.
