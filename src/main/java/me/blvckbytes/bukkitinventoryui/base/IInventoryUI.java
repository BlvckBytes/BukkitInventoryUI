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

import me.blvckbytes.gpeee.interpreter.IEvaluationEnvironment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface IInventoryUI {

  /**
   * Set a UI slot's content by numeric index
   *
   * @param slot  Index of the target slot
   * @param value Slot value to set, use {@code null} to clear the slot
   */
  void setSlotById(int slot, @Nullable UISlot value);

  /**
   * Set a UI slot#s content by it's assigned name, determined by the map
   * {@link IInventoryUIParameterProvider#getSlotContents(IEvaluationEnvironment)}
   * @param name Name of the target slot
   * @param value Slot value to set
   */
  void setSlotByName(String name, UISlot value);

  /**
   * Draw a slot by it's numeric index
   *
   * @param slot Index of the target slot
   */
  void drawSlotById(int slot);

  /**
   * Draw a slot by it's assigned name
   *
   * @param name Name of the target slot
   */
  void drawSlotByName(String name);

  /**
   * Set an item to a certain slot
   */
  void setItem(int slot, ItemStack item);

  /**
   * Get an item by it's slot
   */
  @Nullable ItemStack getItem(int slot);

  /**
   * Handle a users interaction
   */
  void handleInteraction(UIInteraction interaction);

  /**
   * Handle the closing of this inventory
   */
  void handleClose();

  /**
   * Opens and draws the inventory for it's viewer
   */
  void show();

  /**
   * Closes the inventory for it's viewer
   */
  void close();

  /**
   * Get the underlying inventory instance
   */
  Inventory getInventory();

  /**
   * Get the viewing player
   */
  Player getViewer();

  /**
   * Get the evaluation environment which contains inventory parameters as members
   */
  IEvaluationEnvironment getInventoryEnvironment();

  /**
   * Checks whether the viewer currently has this inventory instance up
   */
  boolean isOpen();

}

