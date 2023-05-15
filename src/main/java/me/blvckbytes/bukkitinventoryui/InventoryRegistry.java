/*
 * MIT License
 *
 * Copyright (c) 2023 BlvckBytes
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.blvckbytes.bukkitinventoryui;

import me.blvckbytes.autowirer.ICleanable;
import me.blvckbytes.autowirer.IInitializable;
import me.blvckbytes.bbreflect.packets.communicator.EInventoryClickType;
import me.blvckbytes.bbreflect.packets.communicator.IFakeSlotCommunicator;
import me.blvckbytes.bbreflect.packets.communicator.IItemNameCommunicator;
import me.blvckbytes.bukkitinventoryui.base.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InventoryRegistry implements IInventoryRegistry, IInitializable, ICleanable, Listener {

  private final Map<Inventory, IInventoryUI> uiByInventory;
  private final IItemNameCommunicator itemNameCommunicator;
  private final IFakeSlotCommunicator fakeSlotCommunicator;
  private final Plugin plugin;
  private final Logger logger;
  private @Nullable BukkitTask tickerTask;

  public InventoryRegistry(
    Plugin plugin,
    Logger logger,
    IFakeSlotCommunicator fakeSlotCommunicator,
    IItemNameCommunicator itemNameCommunicator
  ) {
    this.plugin = plugin;
    this.logger = logger;
    this.uiByInventory = new HashMap<>();
    this.fakeSlotCommunicator = fakeSlotCommunicator;
    this.itemNameCommunicator = itemNameCommunicator;
  }

  @Override
  public void cleanup() {
    this.itemNameCommunicator.unregisterReceiver(this::onAnvilItemRename);

    for (IInventoryUI inventory : new ArrayList<>(uiByInventory.values()))
      inventory.close();

    if (this.tickerTask != null) {
      this.tickerTask.cancel();
      this.tickerTask = null;
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    IInventoryUI inventoryUI = uiByInventory.get(event.getInventory());

    if (inventoryUI == null)
      return;

    inventoryUI.handleClose();
  }

  @EventHandler
  public void onDrag(InventoryDragEvent event) {
    Inventory topInventory = event.getView().getTopInventory();
    IInventoryUI inventoryUI = uiByInventory.get(topInventory);

    if (inventoryUI == null)
      return;

    for (int slot : event.getRawSlots()) {
      inventoryUI.handleInteraction(UIInteraction.fromDragEvent(inventoryUI, event, slot));

      if (event.isCancelled())
        break;
    }
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    HumanEntity clicker = event.getWhoClicked();

    if (!(clicker instanceof Player))
      return;

    Player player = (Player) clicker;

    Inventory clickedInventory = event.getClickedInventory();

    if (clickedInventory == null)
      return;

    Inventory topInventory = event.getView().getTopInventory();
    IInventoryUI inventoryUI = uiByInventory.get(topInventory);

    if (inventoryUI == null)
      return;

    inventoryUI.handleInteraction(UIInteraction.fromClickEvent(inventoryUI, event, decideActionOverride(player)));
  }

  /**
   * Decides a value which overrides the {@link InventoryAction} passed by the server, as the
   * server obscures these on fake slot interactions.
   */
  private @Nullable InventoryAction decideActionOverride(Player player) {
    EInventoryClickType lastClickType = fakeSlotCommunicator.getLastReceivedClickType(player);

    if (lastClickType == null)
      return null;

    switch (lastClickType) {
      case QUICK_MOVE:
        return InventoryAction.MOVE_TO_OTHER_INVENTORY;
      case PICKUP_ALL:
        return InventoryAction.COLLECT_TO_CURSOR;
      default:
        return null;
    }
  }

  private void onAnvilItemRename(Player player, String name) {
    Inventory topInventory = player.getOpenInventory().getTopInventory();
    IInventoryUI inventoryUI = uiByInventory.get(topInventory);

    if (!(inventoryUI instanceof IAnvilItemRenameHandler))
      return;

    ((IAnvilItemRenameHandler) inventoryUI).handleAnvilItemRename(name);
  }

  private Runnable makeTickerRunnable() {
    return new Runnable() {

      long time = 0;

      @Override
      public void run() {
        for (IInventoryUI inventoryUI : uiByInventory.values()) {
          if (!(inventoryUI instanceof ITickHandler))
            continue;

          ((ITickHandler) inventoryUI).handleTick(time);
        }
        ++time;
      }
    };
  }

  @Override
  public void initialize() {
    this.itemNameCommunicator.registerReceiver(this::onAnvilItemRename);
    this.tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, makeTickerRunnable(), 0L, 0L);
  }

  @Override
  public void registerUI(IInventoryUI ui) {
    if (this.uiByInventory.put(ui.getInventory(), ui) != null)
      this.logger.log(Level.SEVERE, "An inventory UI tried to register twice");
  }

  @Override
  public void unregisterUI(IInventoryUI ui) {
    if (this.uiByInventory.remove(ui.getInventory()) == null)
      this.logger.log(Level.SEVERE, "An inventory UI tried to unregister twice");
  }

  @Override
  public IFakeSlotCommunicator getFakeSlotCommunicator() {
    return this.fakeSlotCommunicator;
  }
}