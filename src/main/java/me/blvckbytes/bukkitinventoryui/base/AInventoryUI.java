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

import me.blvckbytes.bbreflect.packets.communicator.IFakeSlotCommunicator;
import me.blvckbytes.bukkitevaluable.IItemBuildable;
import me.blvckbytes.bukkitinventoryui.IInventoryRegistry;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;
import me.blvckbytes.gpeee.interpreter.IEvaluationEnvironment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public abstract class AInventoryUI<Provider extends IInventoryUIParameterProvider, Parameter extends AUIParameter<Provider>> {

  protected final Inventory inventory;
  protected final InventoryAnimator animator;
  protected final Parameter parameter;
  protected final Map<String, Set<Integer>> slotContents;
  protected final IEvaluationEnvironment inventoryEnvironment;
  protected final IInventoryRegistry registry;

  private final IFakeSlotCommunicator fakeSlotCommunicator;
  private final Map<Integer, ItemStack> fakeSlotItemCache;
  private final Map<Integer, UISlot> slots;

  public AInventoryUI(IInventoryRegistry registry, Parameter parameter) {
    this.slots = new HashMap<>();

    this.registry = registry;
    this.fakeSlotCommunicator = registry.getFakeSlotCommunicator();

    this.parameter = parameter;
    this.inventory = createInventory();
    this.fakeSlotItemCache = new HashMap<>();

    this.animator = new InventoryAnimator(this::setItem);

    // If the inventory is not chest-like, skip it on the animator
    int inventorySize = this.inventory.getSize();
    if (inventorySize % 9 != 0)
      this.animator.setSlotOffset(inventorySize);

    this.inventoryEnvironment = getInventoryEnvironment();
    this.slotContents = parameter.provider.getSlotContents(this.inventoryEnvironment);
  }

  private IEvaluationEnvironment getInventoryEnvironment() {
    return new EvaluationEnvironmentBuilder()
      .withStaticVariable("inventory_size", this.inventory.getSize())
      .withStaticVariable("viewer_name", this.parameter.viewer.getName())
      .build();
  }

  private void setItem(int slot, ItemStack item) {
    if (!isRegistered()) {
      try {
        throw new IllegalStateException("An unregistered UI (" + getClass() + ") tried to call setItem");
      } catch (Exception e) {
        e.printStackTrace();
      }
      return;
    }

    if (slot < 0)
      return;

    int inventorySize = this.inventory.getSize();
    if (
      slot >= inventorySize ||
      this.inventory.getType() == InventoryType.ANVIL
    ) {
      this.fakeSlotItemCache.put(slot, item);
      this.fakeSlotCommunicator.setFakeSlot(parameter.viewer, slot, true, fakeSlotItemCache.get(slot));
      return;
    }

    this.inventory.setItem(slot, item);
  }

  public boolean isRegistered() {
    return registry.isRegistered(this);
  }

  private void setSuppliedItem(int slot, Supplier<ItemStack> item) {
    this.setItem(slot, item.get());
  }

  protected void drawAll() {
    for (Map.Entry<Integer, UISlot> slotEntry : this.slots.entrySet())
      setSuppliedItem(slotEntry.getKey(), slotEntry.getValue().itemSupplier);
  }

  protected void drawNamedSlot(String name) {
    Set<Integer> slots = slotContents.get(name);

    if (slots == null)
      return;

    for (int slot : slots)
      drawSlot(slot);
  }

  protected void drawSlot(int slot) {
    UISlot targetSlot = this.slots.get(slot);

    if (targetSlot == null) {
      setItem(slot, null);
      return;
    }

    setSuppliedItem(slot, targetSlot.itemSupplier);
  }

  protected void setSlots(UISlot value, Collection<? extends Number> slots) {
    for (Number slot : slots)
      this.slots.put(slot.intValue(), value);
  }

  protected void setSlot(int slot, @Nullable UISlot value) {
    if (value == null) {
      this.slots.remove(slot);
      return;
    }

    this.slots.put(slot, value);
  }

  public void show() {
    this.registry.register(this);

    // Open the inventory before decorating, so that the fake slot
    // communicator takes effect (has a target window ID), if applicable
    this.parameter.viewer.openInventory(this.inventory);
    this.decorate();
    this.drawAll();
  }

  public void close() {
    parameter.viewer.closeInventory();
    updatePlayerInventory();
  }

  protected void decorate() {
    for (Map.Entry<String, IItemBuildable> customItemEntry : parameter.provider.getCustomItems().entrySet()) {
      String customItemName = customItemEntry.getKey();
      Set<Integer> customItemSlots = slotContents.get(customItemName);

      if (customItemSlots == null)
        continue;

      ItemStack customItem = customItemEntry.getValue().build(this.inventoryEnvironment);
      UISlot customItemSlot = new UISlot(() -> customItem);

      for (int slot : customItemSlots)
        setSlot(slot, customItemSlot);
    }
  }

  protected abstract Inventory createInventory();

  public Inventory getInventory() {
    return this.inventory;
  }

  protected ItemStack getItem(int slot) {
    if (slot < 0)
      return null;

    int inventorySize = this.inventory.getSize();
    if (
      slot >= inventorySize ||
      this.inventory.getType() == InventoryType.ANVIL
    )
      return this.fakeSlotItemCache.get(slot);

    return this.inventory.getItem(slot);
  }

  public Player getViewer() {
    return this.parameter.viewer;
  }

  public void handleClose() {
    updatePlayerInventory();
    this.registry.unregister(this);
  }

  private void updatePlayerInventory() {
    if (fakeSlotItemCache.size() == 0)
      return;

    // FIXME: Revise this...

    Player viewer = parameter.viewer;
    Inventory viewerInventory = viewer.getInventory();
    int playerInventorySize = viewerInventory.getSize();
    int inventorySize = inventory.getSize();

    for (Integer fakeSlot : fakeSlotItemCache.keySet()) {
      int inventorySlot = fakeSlot - inventorySize + 9;

      if (inventorySlot < 9 || inventorySlot >= playerInventorySize + 9)
        continue;

      ItemStack realItem;
      if (inventorySlot >= 36)
        realItem = viewerInventory.getItem(inventorySlot - 36);
      else
        realItem = viewerInventory.getItem(inventorySlot);

      fakeSlotCommunicator.setFakeSlot(parameter.viewer, inventorySlot, false, realItem);
    }
  }

  protected abstract boolean canInteractWithOwnInventory();

  public void handleTick(long time) {}

  private boolean isAllowedToInteractWithEmptySlot(UIInteraction interaction) {
    if (interaction.wasTopInventory)
      return false;

    if (!canInteractWithOwnInventory())
      return false;

    return interaction.action != InventoryAction.MOVE_TO_OTHER_INVENTORY;
  }

  public void handleItemRename(String name) {}

  protected @Nullable ItemStack getFakeSlotItem(int slot) {
    return this.fakeSlotItemCache.get(slot);
  }

  protected void blockWindowItems() {
    fakeSlotCommunicator.blockWindowItems(parameter.viewer, this::getFakeSlotItem);
  }

  protected void unblockWindowItems() {
    fakeSlotCommunicator.unblockWindowItems(parameter.viewer);
  }

  public void handleInteraction(UIInteraction interaction) {
    int slot = interaction.slot;

    // Re-send fake items on interaction, as they could disappear otherwise (seldom,
    // but still). Happens if the server only clears the cursor but doesn't re-send the slot
    // Fake slots also always need to be cancelled
    ItemStack fakeItem = fakeSlotItemCache.get(slot);
    if (fakeItem != null) {
      interaction.cancel.run();
      fakeSlotCommunicator.setFakeSlot(parameter.viewer, slot, true, fakeItem);
    }

    UISlot targetSlot = slots.get(slot);
    if (targetSlot == null || targetSlot.interactionHandler == null) {
      if (!isAllowedToInteractWithEmptySlot(interaction))
        interaction.cancel.run();

      return;
    }

    EnumSet<EClickResultFlag> resultFlags = targetSlot.interactionHandler.handle(interaction);

    if (resultFlags == null) {
      interaction.cancel.run();
      return;
    }

    for (EClickResultFlag resultFlag : resultFlags) {
      if (resultFlag.isCancelling(interaction)) {
        interaction.cancel.run();
        return;
      }
    }
  }
}
