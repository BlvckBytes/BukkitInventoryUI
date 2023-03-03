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

import me.blvckbytes.bbconfigmapper.ScalarType;
import me.blvckbytes.bukkitinventoryui.IInventoryRegistry;
import me.blvckbytes.bukkitinventoryui.anvilsearch.AnvilSearchUI;
import me.blvckbytes.bukkitinventoryui.base.DataBoundUISlot;
import me.blvckbytes.bukkitinventoryui.base.UISlot;
import me.blvckbytes.bukkitinventoryui.pageable.PageableInventoryUI;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;
import me.blvckbytes.gpeee.interpreter.IEvaluationEnvironment;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public abstract class SingleChoiceUI<DataType> extends PageableInventoryUI<ISingleChoiceParameterProvider, SingleChoiceParameter<DataType>, DataType> {

  private static final String KEY_SEARCH = "search";
  private @Nullable AnvilSearchUI<DataType> searchUI;

  public SingleChoiceUI(IInventoryRegistry registry, SingleChoiceParameter<DataType> parameter) {
    super(registry, parameter);
  }

  @Override
  protected void decorate() {
    super.decorate();

    for (Map.Entry<String, Set<Integer>> contentEntry : slotContents.entrySet()) {
      UISlot slotContent = null;

      switch (contentEntry.getKey()) {
        case KEY_SEARCH:
          slotContent = new UISlot(() -> parameter.provider.getSearch().build(), interaction -> {

            // Create the search UI on demand
            if (this.searchUI == null) {
              this.searchUI = new AnvilSearchUI<>(
                registry, parameter.makeAnvilSearchParameter(ui -> this.show())
              );
            }

            this.searchUI.show();
            return null;
          });
          break;
      }

      if (slotContent == null)
        continue;

      setSlots(slotContent, contentEntry.getValue());
    }
  }

  @Override
  protected Inventory createInventory() {
    IEvaluationEnvironment titleEnvironment = getTitleEnvironment();
    String title = parameter.provider.getTitle().asScalar(ScalarType.STRING, titleEnvironment);
    return Bukkit.createInventory(null, parameter.provider.getNumberOfRows() * 9, title);
  }

  @Override
  public void handleClose() {
    super.handleClose();
  }

  @Override
  protected boolean canInteractWithOwnInventory() {
    return true;
  }

  @Override
  public void setPageableSlots(Collection<DataBoundUISlot<DataType>> items) {
    super.setPageableSlots(items);

    // Only invoke the update if the search UI is actually active
    if (this.searchUI != null && this.searchUI.isRegistered())
      this.searchUI.invokeFilterFunctionAndUpdatePageSlots();
  }

  private IEvaluationEnvironment getTitleEnvironment() {
    return new EvaluationEnvironmentBuilder()
      .withLiveVariable("name", parameter.viewer::getName)
      .build();
  }
}
