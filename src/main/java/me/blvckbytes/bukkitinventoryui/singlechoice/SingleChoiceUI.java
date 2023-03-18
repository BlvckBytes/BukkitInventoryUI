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

package me.blvckbytes.bukkitinventoryui.singlechoice;

import me.blvckbytes.bukkitinventoryui.IInventoryRegistry;
import me.blvckbytes.bukkitinventoryui.anvilsearch.AnvilSearchUI;
import me.blvckbytes.bukkitinventoryui.base.*;
import me.blvckbytes.bukkitinventoryui.pageable.IPageableInventoryUI;
import me.blvckbytes.bukkitinventoryui.pageable.PageableInventoryUI;
import me.blvckbytes.gpeee.interpreter.IEvaluationEnvironment;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class SingleChoiceUI<DataType>  implements IPageableInventoryUI<DataType> {

  private static final String KEY_SEARCH = "search";

  private @Nullable AnvilSearchUI<DataType> searchUI;
  private final IInventoryRegistry registry;
  private final PageableInventoryUI<DataType> handle;
  private final SingleChoiceParameter<DataType> parameter;

  public SingleChoiceUI(SingleChoiceParameter<DataType> parameter, IInventoryRegistry registry) {
    this.parameter = parameter;
    this.registry = registry;

    BaseInventoryUI baseUI = new BaseInventoryUI(parameter.provider, this::createInventory, parameter.viewer, null);
    this.handle = new PageableInventoryUI<>(parameter.provider, baseUI);
  }

  private void setSingleChoiceSlots() {
    setSlotByName(KEY_SEARCH, new UISlot(() -> parameter.provider.getSearch().build(), interaction -> {

      // Create the search UI on demand
      if (this.searchUI == null) {
        this.searchUI = new AnvilSearchUI<>(
          parameter.makeAnvilSearchParameter(ui -> this.show()), registry
        );
      }

      this.searchUI.show();
      return null;
    }));
    drawSlotByName(KEY_SEARCH);
  }

  private Inventory createInventory(String title) {
    return Bukkit.createInventory(null, parameter.provider.getNumberOfRows() * 9, title);
  }

  @Override
  public void setPageableSlots(Collection<DataBoundUISlot<DataType>> items) {
    this.handle.setPageableSlots(items);

    // Only invoke the update if the search UI is actually active
    if (this.searchUI != null && this.searchUI.isOpen())
      this.searchUI.invokeFilterFunctionAndUpdatePageSlots();
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
    return this.handle.getItem(slot);
  }

  @Override
  public void handleInteraction(UIInteraction interaction) {
    this.handle.handleInteraction(interaction);
  }

  @Override
  public void handleClose() {
    this.handle.handleClose();
    this.registry.unregisterUI(this);
  }

  @Override
  public void show() {
    this.registry.registerUI(this);
    this.handle.show();
    this.setSingleChoiceSlots();
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

  @Override
  public void handleTick(long time) {
    this.handle.handleTick(time);
  }
}
