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
import me.blvckbytes.bbreflect.packets.communicator.IFakeSlotCommunicator;
import me.blvckbytes.bbreflect.packets.communicator.IItemNameCommunicator;
import me.blvckbytes.bukkitinventoryui.base.AInventoryUI;
import me.blvckbytes.bukkitinventoryui.base.UIInteraction;
import me.blvckbytes.utilitytypes.EIterationDecision;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class InventoryRegistry implements IInventoryRegistry, IInitializable, ICleanable, Listener {

  private final Map<Inventory, AInventoryUI<?, ?>> uiByInventory;
  private final IItemNameCommunicator itemNameCommunicator;
  private final IFakeSlotCommunicator fakeSlotCommunicator;
  private final Plugin plugin;
  private @Nullable BukkitTask tickerTask;

  public InventoryRegistry(Plugin plugin, IFakeSlotCommunicator fakeSlotCommunicator, IItemNameCommunicator itemNameCommunicator) {
    this.uiByInventory = new HashMap<>();
    this.fakeSlotCommunicator = fakeSlotCommunicator;
    this.itemNameCommunicator = itemNameCommunicator;
    this.plugin = plugin;
  }

  @Override
  public void cleanup() {
    this.itemNameCommunicator.unregisterReceiver(this::onAnvilItemRename);

    for (AInventoryUI<?, ?> inventory : uiByInventory.values())
      inventory.close();

    if (this.tickerTask != null) {
      this.tickerTask.cancel();
      this.tickerTask = null;
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    AInventoryUI<?, ?> inventoryUI = uiByInventory.get(event.getInventory());

    if (inventoryUI == null)
      return;

    inventoryUI.handleClose();
  }

  @EventHandler
  public void onDrag(InventoryDragEvent event) {
    Inventory topInventory = event.getView().getTopInventory();
    AInventoryUI<?, ?> inventoryUI = uiByInventory.get(topInventory);

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
    Inventory clickedInventory = event.getClickedInventory();

    if (clickedInventory == null)
      return;

    Inventory topInventory = event.getView().getTopInventory();
    AInventoryUI<?, ?> inventoryUI = uiByInventory.get(topInventory);

    if (inventoryUI == null)
      return;

    inventoryUI.handleInteraction(UIInteraction.fromClickEvent(inventoryUI, event));
  }

  private void onAnvilItemRename(Player player, String name) {
    Inventory topInventory = player.getOpenInventory().getTopInventory();
    AInventoryUI<?, ?> inventoryUI = uiByInventory.get(topInventory);

    if (inventoryUI == null)
      return;

    inventoryUI.handleItemRename(name);
  }

  @Override
  public void initialize() {
    this.itemNameCommunicator.registerReceiver(this::onAnvilItemRename);

    tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {

      long time = 0;

      @Override
      public void run() {
        for (AInventoryUI<?, ?> inventory : uiByInventory.values())
          inventory.handleTick(time);
        ++time;
      }
    }, 0L, 0L);
  }

  @Override
  public void register(AInventoryUI<?, ?> ui) {
    this.uiByInventory.put(ui.getInventory(), ui);
  }

  @Override
  public void unregister(AInventoryUI<?, ?> ui) {
    this.uiByInventory.remove(ui.getInventory());
  }

  @Override
  public boolean isRegistered(AInventoryUI<?, ?> ui) {
    return this.uiByInventory.containsKey(ui.getInventory());
  }

  @Override
  public IFakeSlotCommunicator getFakeSlotCommunicator() {
    return this.fakeSlotCommunicator;
  }

  @Override
  public <T extends AInventoryUI<?, ?>> void forEachRegisteredOfType(Class<T> type, Function<T, EIterationDecision> consumer) {
    for (AInventoryUI<?, ?> ui : this.uiByInventory.values()) {
      if (!type.isInstance(ui))
        continue;

      if (consumer.apply(type.cast(ui)) == EIterationDecision.BREAK)
        break;
    }
  }
}