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

package me.blvckbytes.bukkitinventoryui.base;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class UIInteraction {

  public final int slot;
  public final boolean wasTopInventory;
  public final Runnable cancel;
  public final InventoryAction action;
  public final ClickType clickType;
  public final IInventoryUI ui;

  public UIInteraction(IInventoryUI ui, int slot, boolean wasTopInventory, Runnable cancel, InventoryAction action, ClickType clickType) {
    this.ui = ui;
    this.slot = slot;
    this.wasTopInventory = wasTopInventory;
    this.cancel = cancel;
    this.action = action;
    this.clickType = clickType;
  }

  public static UIInteraction fromDragEvent(IInventoryUI ui, InventoryDragEvent event, int slot) {
    Inventory topInventory = event.getView().getTopInventory();
    int topInventorySize = topInventory.getSize();
    boolean wasTopInventory = slot < topInventorySize;

    return new UIInteraction(
      ui,
      slot,
      wasTopInventory,
      () -> event.setCancelled(true),
      InventoryAction.PLACE_SOME,
      ClickType.DROP
    );
  }

  public static UIInteraction fromClickEvent(IInventoryUI ui, InventoryClickEvent event) {
    return new UIInteraction(
      ui,
      event.getRawSlot(),
      event.getClickedInventory() == event.getView().getTopInventory(),
      () -> event.setCancelled(true),
      event.getAction(), event.getClick()
    );
  }

  @Override
  public String toString() {
    return "UIInteraction{" +
      "slot=" + slot +
      ", wasTopInventory=" + wasTopInventory +
      ", action=" + action +
      ", clickType=" + clickType +
    '}';
  }
}
