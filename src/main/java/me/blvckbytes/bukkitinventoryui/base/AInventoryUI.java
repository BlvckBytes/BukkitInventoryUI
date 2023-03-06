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

import me.blvckbytes.bbconfigmapper.ScalarType;
import me.blvckbytes.bbreflect.packets.communicator.IFakeSlotCommunicator;
import me.blvckbytes.bukkitevaluable.IItemBuildable;
import me.blvckbytes.bukkitinventoryui.IInventoryRegistry;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;
import me.blvckbytes.gpeee.interpreter.IEvaluationEnvironment;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public abstract class AInventoryUI<Provider extends IInventoryUIParameterProvider, Parameter extends AUIParameter<Provider>> {

  private static final long COLLECT_TO_CURSOR_MAX_DELTA_MS = 400;
  private static final ItemStack ITEM_AIR = new ItemStack(Material.AIR);

  protected final Inventory inventory;
  protected final InventoryAnimator animator;
  protected final Parameter parameter;
  protected final Map<String, Set<Integer>> slotContents;
  protected final IEvaluationEnvironment inventoryEnvironment;
  protected final IInventoryRegistry registry;
  protected final @Nullable AInventoryUI<?, ?> previousUi;
  protected final String title;

  private final IFakeSlotCommunicator fakeSlotCommunicator;
  private final Map<Integer, ItemStack> fakeSlotItemCache;
  private final Map<Integer, UISlot> slots;

  private long lastLeftClick;

  public AInventoryUI(IInventoryRegistry registry, Parameter parameter, @Nullable AInventoryUI<?, ?> previousUi) {
    this.slots = new HashMap<>();

    this.previousUi = previousUi;
    this.registry = registry;
    this.fakeSlotCommunicator = registry.getFakeSlotCommunicator();

    this.parameter = parameter;

    IEvaluationEnvironment titleEnvironment = getTitleEnvironment();
    this.title = parameter.provider.getTitle().asScalar(ScalarType.STRING, titleEnvironment);
    this.inventory = createInventory();

    this.fakeSlotItemCache = new HashMap<>();

    this.animator = new InventoryAnimator(this::setItem);

    // If the inventory is not chest-like, skip it on the animator
    int inventorySize = this.inventory.getSize();
    if (inventorySize % 9 != 0)
      this.animator.setSlotOffset(inventorySize);

    this.inventoryEnvironment = getInventoryEnvironment(titleEnvironment);
    this.slotContents = parameter.provider.getSlotContents(this.inventoryEnvironment);
  }

  private IEvaluationEnvironment getTitleEnvironment() {
    return new EvaluationEnvironmentBuilder()
      .withStaticVariable("viewer_name", this.parameter.viewer.getName())
      .withStaticVariable("previous_title", this.previousUi == null ? "?" : this.previousUi.getTitle())
      .build();
  }

  private IEvaluationEnvironment getInventoryEnvironment(IEvaluationEnvironment titleEnvironment) {
    return new EvaluationEnvironmentBuilder()
      .withStaticVariable("inventory_size", this.inventory.getSize())
      .build(titleEnvironment);
  }

  public String getTitle() {
    return this.title;
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

  private @Nullable ItemStack getFakeSlotContent(int slot) {
    if (!this.fakeSlotItemCache.containsKey(slot))
      return null;

    ItemStack item = this.fakeSlotItemCache.get(slot);

    if (item == null)
      return ITEM_AIR;

    return item;
  }

  protected void blockWindowItems() {
    fakeSlotCommunicator.blockWindowItems(parameter.viewer, this::getFakeSlotContent);
  }

  protected void unblockWindowItems() {
    fakeSlotCommunicator.unblockWindowItems(parameter.viewer);
  }

  public void handleInteraction(UIInteraction interaction) {
    try {
      int slot = interaction.slot;

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

              fakeSlotCommunicator.setFakeSlot(parameter.viewer, fakeSlotEntry.getKey(), true, currentFakeItem);
            }
            updatedAllSimilarSlots = true;
          }

          lastLeftClick = now;
        }

        if (!updatedAllSimilarSlots)
          fakeSlotCommunicator.setFakeSlot(parameter.viewer, slot, true, fakeItem);

        // Also update the cursor, as it's not guaranteed that the server will clear the cursor after clicking
        // on a slot with a fake item, as it doesn't know that it's there. Without this, the fake item is stuck
        // to the cursor until the next interaction.
        getViewer().setItemOnCursor(getViewer().getItemOnCursor());
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
    } catch (Throwable e) {
      e.printStackTrace();
      // If any exceptions occurred anywhere down the line, it's better to be safe than sorry
      interaction.cancel.run();
    }
  }
}
