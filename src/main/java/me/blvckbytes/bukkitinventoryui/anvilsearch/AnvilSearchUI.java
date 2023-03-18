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

package me.blvckbytes.bukkitinventoryui.anvilsearch;

import me.blvckbytes.bukkitinventoryui.IInventoryRegistry;
import me.blvckbytes.bukkitinventoryui.base.*;
import me.blvckbytes.bukkitinventoryui.pageable.PageableInventoryUI;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;
import me.blvckbytes.gpeee.interpreter.IEvaluationEnvironment;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AnvilSearchUI<DataType> implements IInventoryUI, IAnvilItemRenameHandler, ITickHandler {

  private static final String
    KEY_FILTER = "filter",
    KEY_BACK = "back",
    KEY_RESULT = "resultItem",
    KEY_SEARCH_ITEM = "searchItem";

  private final Map<String, Boolean> filterStates;
  private final int searchDebounceMs;
  private final PageableInventoryUI<DataType> handle;

  private ISearchFilterEnum<?, DataType> currentFilter;
  private long searchTextUpdate;
  private String searchText;

  private final AnvilSearchParameter<DataType> parameter;
  private final IInventoryRegistry registry;
  private final FakeItemUI fakeItemUI;

  public AnvilSearchUI(AnvilSearchParameter<DataType> parameter, IInventoryRegistry registry) {
    this.parameter = parameter;
    this.registry = registry;

    BaseInventoryUI baseUI = new BaseInventoryUI(parameter.provider, this::createInventory, parameter.viewer, this::handleSetSlot);
    this.fakeItemUI = new FakeItemUI(baseUI, registry.getFakeSlotCommunicator(), true);
    this.handle = new PageableInventoryUI<>(parameter.provider, fakeItemUI);
    this.handle.setSlotOffset(getInventory().getSize());

    this.searchText = " ";
    this.filterStates = new LinkedHashMap<>();
    this.currentFilter = parameter.filterEnum;
    this.searchDebounceMs = parameter.provider.getSearchDebounceTicks() / 20 * 1000;

    this.setupFilterStates();
  }

  public void invokeFilterFunctionAndUpdatePageSlots() {
    List<DataBoundUISlot<DataType>> slots = parameter.filterFunction.applyFilter(currentFilter, searchText);
    this.handle.setPageableSlots(slots);
  }

  @Override
  public void handleAnvilItemRename(String name) {
    synchronized (this) {
      this.searchText = name;
      this.searchTextUpdate = System.currentTimeMillis();
      this.handle.drawSlotByName(KEY_RESULT);
    }
  }

  @Override
  public void handleTick(long time) {
    this.handle.handleTick(time);

    synchronized (this) {
      if (searchTextUpdate > 0 && System.currentTimeMillis() - searchTextUpdate >= searchDebounceMs) {
        invokeFilterFunctionAndUpdatePageSlots();
        searchTextUpdate = 0;
      }
    }
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
    this.setAnvilSearchSlots();
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

  private void setupFilterStates() {
    for (ISearchFilterEnum<?, DataType> searchFilter : parameter.filterEnum.listValues())
      filterStates.put(searchFilter.name(), searchFilter == currentFilter);
  }

  private EnumSet<EClickResultFlag> handleFilterClick(UIInteraction action) {
    this.filterStates.put(this.currentFilter.name(), false);
    this.currentFilter = this.currentFilter.nextValue();
    this.filterStates.put(this.currentFilter.name(), true);

    this.handle.drawSlotByName(KEY_FILTER);

    synchronized (this) {
      searchTextUpdate = System.currentTimeMillis();
    }
    return null;
  }

  private IEvaluationEnvironment buildResultEnvironment() {
    return new EvaluationEnvironmentBuilder()
      .withLiveVariable("search_text", () -> this.searchText.trim())
      .build();
  }

  private IEvaluationEnvironment buildFilterEnvironment() {
    return new EvaluationEnvironmentBuilder()
      .withStaticVariable("filters", this.filterStates)
      .build();
  }

  private void setAnvilSearchSlots() {
    IEvaluationEnvironment filterEnvironment = buildFilterEnvironment();
    IEvaluationEnvironment resultEnvironment = buildResultEnvironment();
    IEvaluationEnvironment inventoryEnvironment = handle.getInventoryEnvironment();

    this.handle.setSlotByName(KEY_FILTER, new UISlot(() -> parameter.provider.getFilter().build(filterEnvironment), this::handleFilterClick));
    this.handle.drawSlotByName(KEY_FILTER);

    this.handle.setSlotByName(KEY_SEARCH_ITEM, new UISlot(() -> parameter.provider.getSearchItem().build(filterEnvironment), this::handleFilterClick));
    this.handle.drawSlotByName(KEY_SEARCH_ITEM);

    this.handle.setSlotByName(KEY_RESULT, new UISlot(() -> parameter.provider.getResultItem().build(resultEnvironment), this::handleFilterClick));
    this.handle.drawSlotByName(KEY_RESULT);

    if (parameter.backHandler != null) {
      this.handle.setSlotByName(KEY_BACK, new UISlot(() -> parameter.provider.getBack().build(inventoryEnvironment), interaction -> {
        parameter.backHandler.accept(this);
        return null;
      }));
      this.handle.drawSlotByName(KEY_BACK);
    }

    invokeFilterFunctionAndUpdatePageSlots();
  }

  private boolean handleSetSlot(int slot, ItemStack item) {
    return this.fakeItemUI.handleSetFakeItem(slot, item);
  }

  private Inventory createInventory(String title) {
    return Bukkit.createInventory(null, InventoryType.ANVIL, title);
  }
}
