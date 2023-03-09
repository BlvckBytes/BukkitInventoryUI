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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AnvilSearchUI<DataType> extends PageableInventoryUI<IAnvilSearchParameterProvider, AnvilSearchParameter<DataType>, DataType> {

  private static final String
    KEY_FILTER = "filter",
    KEY_BACK = "back",
    KEY_RESULT = "resultItem",
    KEY_SEARCH_ITEM = "searchItem";

  private final Map<String, Boolean> filterStates;
  private final int searchDebounceMs;

  private ISearchFilterEnum<?, DataType> currentFilter;
  private long searchTextUpdate;
  private String searchText;

  public AnvilSearchUI(IInventoryRegistry registry, AnvilSearchParameter<DataType> parameter, @Nullable AInventoryUI<?, ?> previousUi) {
    super(registry, parameter, previousUi);

    this.searchText = " ";
    this.filterStates = new LinkedHashMap<>();
    this.currentFilter = parameter.filterEnum;
    this.searchDebounceMs = parameter.provider.getSearchDebounceTicks() / 20 * 1000;

    this.setupFilterStates();
  }

  @Override
  protected Inventory createInventory() {
    return Bukkit.createInventory(null, InventoryType.ANVIL, title);
  }

  @Override
  protected void decorate() {
    super.decorate();

    for (Map.Entry<String, Set<Integer>> contentEntry : slotContents.entrySet()) {
      UISlot slotContent = null;

      switch (contentEntry.getKey()) {
        case KEY_FILTER: {
          IEvaluationEnvironment filterEnvironment = getFilterEnvironment();
          slotContent = new UISlot(() -> parameter.provider.getFilter().build(filterEnvironment), this::handleFilterClick);
          break;
        }

        case KEY_SEARCH_ITEM:
          slotContent = new UISlot(() -> parameter.provider.getSearchItem().build(inventoryEnvironment));
          break;

        case KEY_RESULT:
          IEvaluationEnvironment resultEnvironment = getResultEnvironment();
          slotContent = new UISlot(() -> parameter.provider.getResultItem().build(resultEnvironment));
          break;

        case KEY_BACK:
          if (parameter.backHandler != null) {
            slotContent = new UISlot(() -> parameter.provider.getBack().build(inventoryEnvironment), interaction -> {
              parameter.backHandler.accept(this);
              return null;
            });
          }
          break;
      }

      if (slotContent == null)
        continue;

      setSlots(slotContent, contentEntry.getValue());
    }

    invokeFilterFunctionAndUpdatePageSlots();
  }

  public void invokeFilterFunctionAndUpdatePageSlots() {
    List<DataBoundUISlot<DataType>> slots = parameter.filterFunction.applyFilter(currentFilter, searchText);
    setPageableSlots(slots);
  }

  @Override
  public void handleItemRename(String name) {
    super.handleItemRename(name);

    synchronized (this) {
      this.searchText = name;
      this.searchTextUpdate = System.currentTimeMillis();
      drawNamedSlot(KEY_RESULT);
    }
  }

  @Override
  protected boolean canInteractWithOwnInventory() {
    return false;
  }

  @Override
  public void show() {
    blockWindowItems();
    super.show();
  }

  @Override
  public void handleClose() {
    unblockWindowItems();
    super.handleClose();
  }

  @Override
  public void handleTick(long time) {
    super.handleTick(time);

    synchronized (this) {
      if (searchTextUpdate > 0 && System.currentTimeMillis() - searchTextUpdate >= searchDebounceMs) {
        invokeFilterFunctionAndUpdatePageSlots();
        searchTextUpdate = 0;
      }
    }
  }

  private void setupFilterStates() {
    for (ISearchFilterEnum<?, DataType> searchFilter : parameter.filterEnum.listValues())
      filterStates.put(searchFilter.name(), searchFilter == currentFilter);
  }

  private EnumSet<EClickResultFlag> handleFilterClick(UIInteraction action) {
    this.filterStates.put(this.currentFilter.name(), false);
    this.currentFilter = this.currentFilter.nextValue();
    this.filterStates.put(this.currentFilter.name(), true);

    drawNamedSlot(KEY_FILTER);

    synchronized (this) {
      searchTextUpdate = System.currentTimeMillis();
    }
    return null;
  }

  private IEvaluationEnvironment getResultEnvironment() {
    return new EvaluationEnvironmentBuilder()
      .withLiveVariable("search_text", () -> this.searchText.trim())
      .build();
  }

  private IEvaluationEnvironment getFilterEnvironment() {
    return new EvaluationEnvironmentBuilder()
      .withStaticVariable("filters", this.filterStates)
      .build();
  }
}
