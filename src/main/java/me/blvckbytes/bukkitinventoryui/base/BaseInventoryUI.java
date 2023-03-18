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
import me.blvckbytes.bukkitevaluable.IItemBuildable;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;
import me.blvckbytes.gpeee.interpreter.IEvaluationEnvironment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public class BaseInventoryUI implements IInventoryUI {

  private final Inventory inventory;
  private final IEvaluationEnvironment inventoryEnvironment;
  private final Player viewer;
  private final @Nullable FSetSlotHandler setSlotHandler;

  private final Map<Integer, UISlot> slotByIndex;
  private final Map<String, Set<Integer>> slotIndicesByName;
  private final Map<String, IItemBuildable> customItemByName;

  public BaseInventoryUI(
    IInventoryUIParameterProvider parameterProvider,
    Function<String, Inventory> inventoryFactory,
    Player viewer,
    @Nullable FSetSlotHandler setSlotHandler
  ) {
    this.slotByIndex = new HashMap<>();
    this.setSlotHandler = setSlotHandler;

    IEvaluationEnvironment titleEnvironment = buildTitleEnvironment(viewer);
    String title = parameterProvider.getTitle().asScalar(ScalarType.STRING, titleEnvironment);
    this.inventory = inventoryFactory.apply(title);

    this.inventoryEnvironment = buildInventoryEnvironment(this.inventory, titleEnvironment);
    this.slotIndicesByName = parameterProvider.getSlotContents(this.inventoryEnvironment);

    this.customItemByName = parameterProvider.getCustomItems();
    this.viewer = viewer;
  }

  @Override
  public void setSlotById(int slot, @Nullable UISlot value) {
    if (value == null) {
      this.slotByIndex.remove(slot);
      return;
    }

    this.slotByIndex.put(slot, value);
  }

  @Override
  public void setSlotByName(String name, UISlot value) {
    Set<Integer> slotIndices = slotIndicesByName.get(name);

    if (slotIndices == null)
      return;

    for (Number slot : slotIndices)
      this.slotByIndex.put(slot.intValue(), value);
  }

  @Override
  public void drawSlotById(int slot) {
    UISlot targetSlot = this.slotByIndex.get(slot);

    if (targetSlot == null) {
      setItem(slot, null);
      return;
    }

    setItem(slot, targetSlot.itemSupplier.get());
  }

  @Override
  public void drawSlotByName(String name) {
    Set<Integer> slots = slotIndicesByName.get(name);

    if (slots == null)
      return;

    for (int slot : slots)
      drawSlotById(slot);
  }

  @Override
  public void handleInteraction(UIInteraction interaction) {
    try {
      int slot = interaction.slot;

      UISlot targetSlot = slotByIndex.get(slot);
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

  @Override
  public void handleClose() {}

  @Override
  public void show() {
    // Open the inventory before decorating, so that the fake slot
    // communicator takes effect (has a target window ID), if applicable
    this.viewer.openInventory(this.inventory);
    this.setCustomItemSlots();
  }

  @Override
  public void close() {
    this.viewer.closeInventory();
  }

  @Override
  public Inventory getInventory() {
    return this.inventory;
  }

  @Override
  public Player getViewer() {
    return this.viewer;
  }

  @Override
  public IEvaluationEnvironment getInventoryEnvironment() {
    return this.inventoryEnvironment;
  }

  @Override
  public boolean isOpen() {
    return viewer.getOpenInventory().getTopInventory() == inventory;
  }

  @Override
  public void setItem(int slot, ItemStack item) {
    if (setSlotHandler != null && setSlotHandler.apply(slot, item))
      return;

    if (slot < 0 || slot >= this.inventory.getSize())
      return;

    this.inventory.setItem(slot, item);
  }

  @Override
  public @Nullable ItemStack getItem(int slot) {
    if (slot < 0 || slot >= this.inventory.getSize())
      return null;

    return this.inventory.getItem(slot);
  }

  private boolean isAllowedToInteractWithEmptySlot(UIInteraction interaction) {
    if (interaction.wasTopInventory)
      return false;

    return interaction.action != InventoryAction.MOVE_TO_OTHER_INVENTORY;
  }

  private void setCustomItemSlots() {
    for (Map.Entry<String, IItemBuildable> customItemEntry : this.customItemByName.entrySet()) {
      String customItemName = customItemEntry.getKey();
      Set<Integer> customItemSlots = slotIndicesByName.get(customItemName);

      if (customItemSlots == null)
        continue;

      ItemStack customItem = customItemEntry.getValue().build(this.inventoryEnvironment);
      UISlot customItemSlot = new UISlot(() -> customItem);

      for (int slot : customItemSlots) {
        setSlotById(slot, customItemSlot);
        drawSlotById(slot);
      }
    }
  }

  private IEvaluationEnvironment buildTitleEnvironment(Player viewer) {
    return new EvaluationEnvironmentBuilder()
      .withStaticVariable("viewer_name", viewer.getName())
      .build();
  }

  private IEvaluationEnvironment buildInventoryEnvironment(Inventory inventory, IEvaluationEnvironment titleEnvironment) {
    return new EvaluationEnvironmentBuilder()
      .withStaticVariable("inventory_size", inventory.getSize())
      .build(titleEnvironment);
  }
}
