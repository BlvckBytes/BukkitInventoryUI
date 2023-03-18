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

import me.blvckbytes.bbreflect.packets.communicator.EInventoryType;
import me.blvckbytes.bbreflect.packets.communicator.IFakeSlotCommunicator;
import me.blvckbytes.gpeee.interpreter.IEvaluationEnvironment;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FakeItemUI implements IInventoryUI {

  private static final int PLAYER_INVENTORY_SIZE = 9 * 4;
  private static final ItemStack ITEM_AIR = new ItemStack(Material.AIR);
  private static final long COLLECT_TO_CURSOR_MAX_DELTA_MS = 400;

  private final IInventoryUI handle;

  private final IFakeSlotCommunicator fakeSlotCommunicator;
  private final Map<Integer, ItemStack> fakeSlotItemCache;
  private final boolean requiresUpperInventoryFakeSlots;
  private final boolean usesPlayerInventory;

  private long lastLeftClick;

  public FakeItemUI(IInventoryUI handle, IFakeSlotCommunicator fakeSlotCommunicator, boolean usesPlayerInventory) {
    this.handle = handle;
    this.usesPlayerInventory = usesPlayerInventory;
    this.fakeSlotCommunicator = fakeSlotCommunicator;
    this.fakeSlotItemCache = new HashMap<>();
    this.requiresUpperInventoryFakeSlots = getInventory().getType() == InventoryType.ANVIL;
  }

  @Override
  public void setSlotById(int slot, @Nullable UISlot value) {
    this.handle.setSlotById(slot, value);
  }

  @Override
  public void setSlotByName(String name, UISlot value) {
    this.handle.setSlotByName(name, value);
  }

  @Override
  public void drawSlotById(int slot) {
    this.handle.drawSlotById(slot);
  }

  @Override
  public void drawSlotByName(String name) {
    this.handle.drawSlotByName(name);
  }

  @Override
  public void setItem(int slot, ItemStack item) {
    this.handle.setItem(slot, item);
  }

  @Override
  public @Nullable ItemStack getItem(int slot) {
    int inventorySize = this.handle.getInventory().getSize();

    if (slot >= inventorySize || requiresUpperInventoryFakeSlots)
      return this.fakeSlotItemCache.get(slot);

    return this.handle.getItem(slot);
  }

  @Override
  public void handleInteraction(UIInteraction interaction) {
    handleFakeSlotInteraction(interaction);
    this.handle.handleInteraction(interaction);
  }

  @Override
  public void handleClose() {
    unblockWindowItems();
    this.handle.handleClose();
    this.updatePlayerInventory();
  }

  @Override
  public void show() {
    blockWindowItems();
    this.handle.show();
  }

  @Override
  public void close() {
    this.handle.close();
  }

  @Override
  public Inventory getInventory() {
    return this.handle.getInventory();
  }

  @Override
  public Player getViewer() {
    return this.handle.getViewer();
  }

  @Override
  public IEvaluationEnvironment getInventoryEnvironment() {
    return this.handle.getInventoryEnvironment();
  }

  @Override
  public boolean isOpen() {
    return this.handle.isOpen();
  }

  private void updatePlayerInventory() {
    Player viewer = this.handle.getViewer();
    Inventory viewerInventory = viewer.getInventory();

    // Definition: loop all player inventory slots from top left to bottom right in rows
    for (int i = 0; i < PLAYER_INVENTORY_SIZE; i++) {
      // The inventory starts counting slots in the hotbar (0-8) and then continues in the
      // top left corner at 9. To compensate for this, add nine to i (0 lands at 9) and wrap around
      int inventorySlot = (i + 9) % PLAYER_INVENTORY_SIZE;
      ItemStack realItem = viewerInventory.getItem(inventorySlot);

      // When modifying the player inventory itself, slot 0 marks the 2x2 crafting result
      // slot, while 1-4 are the grid and 5-8 mark the armor slots. Thus, an offset of 9 is
      // required to shift 0 into the top left of the inventory
      fakeSlotCommunicator.setFakeSlot(viewer, i + 9, false, realItem);
    }
  }

  private @Nullable ItemStack getFakeSlotContent(int slot) {
    if (!this.fakeSlotItemCache.containsKey(slot))
      return null;

    ItemStack item = this.fakeSlotItemCache.get(slot);

    if (item == null)
      return ITEM_AIR;

    return item;
  }

  private void blockWindowItems() {
    EnumSet<EInventoryType> blockedInventories = EnumSet.noneOf(EInventoryType.class);

    if (requiresUpperInventoryFakeSlots)
      blockedInventories.add(EInventoryType.TOP);

    if (this.usesPlayerInventory)
      blockedInventories.add(EInventoryType.BOTTOM);

    this.fakeSlotCommunicator.blockWindowItems(this.handle.getViewer(), blockedInventories, this::getFakeSlotContent);
  }

  private void unblockWindowItems() {
    this.fakeSlotCommunicator.unblockWindowItems(this.handle.getViewer());
  }

  private void handleFakeSlotInteraction(UIInteraction interaction) {
    Player viewer = this.handle.getViewer();
    int slot = interaction.slot;

    // Clicked own inventory, which is used for fake slots - always deny for all slots
    if (!interaction.wasTopInventory && usesPlayerInventory)
      interaction.cancel.run();

    // Re-send fake items on interaction, as they could disappear otherwise (seldom,
    // but still). Happens if the server only clears the cursor but doesn't re-send the slot
    // Fake slots also always need to be cancelled
    ItemStack fakeItem = fakeSlotItemCache.get(slot);
    if (fakeItem != null) {
      interaction.cancel.run();

      boolean updatedAllSimilarSlots = false;

      // Update all similar slots if a left click has been performed twice within the max delta
      // time-span, in order to undo the clientside collect to cursor action
      if (interaction.clickType.isLeftClick()) {
        long now = System.currentTimeMillis();
        long delta = now - lastLeftClick;

        if (delta <= COLLECT_TO_CURSOR_MAX_DELTA_MS) {
          for (Map.Entry<Integer, ItemStack> fakeSlotEntry : fakeSlotItemCache.entrySet()) {
            ItemStack currentFakeItem = fakeSlotEntry.getValue();

            if (!fakeItem.isSimilar(currentFakeItem))
              continue;

            fakeSlotCommunicator.setFakeSlot(viewer, fakeSlotEntry.getKey(), true, currentFakeItem);
          }
          updatedAllSimilarSlots = true;
        }

        lastLeftClick = now;
      }

      if (!updatedAllSimilarSlots)
        fakeSlotCommunicator.setFakeSlot(viewer, slot, true, fakeItem);

      // Also update the cursor, as it's not guaranteed that the server will clear the cursor after clicking
      // on a slot with a fake item, as it doesn't know that it's there. Without this, the fake item is stuck
      // to the cursor until the next interaction.
      viewer.setItemOnCursor(getViewer().getItemOnCursor());
    }
  }

  public boolean handleSetFakeItem(int slot, ItemStack item) {
    Inventory inventory = this.handle.getInventory();
    int inventorySize = inventory.getSize();
    Player viewer = this.handle.getViewer();

    if (slot >= inventorySize || requiresUpperInventoryFakeSlots) {
      this.fakeSlotItemCache.put(slot, item);

      // Don't draw fake slots if the currently open inventory is not the UI instance
      if (isOpen())
        this.fakeSlotCommunicator.setFakeSlot(viewer, slot, true, fakeSlotItemCache.get(slot));

      return true;
    }

    return false;
  }
}
