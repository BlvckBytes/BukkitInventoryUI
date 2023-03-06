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

package me.blvckbytes.bukkitinventoryui.pageable;

import me.blvckbytes.bukkitinventoryui.IInventoryRegistry;
import me.blvckbytes.bukkitinventoryui.base.*;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;
import me.blvckbytes.gpeee.interpreter.IEvaluationEnvironment;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class PageableInventoryUI<Provider extends IPageableParameterProvider, Parameter extends AUIParameter<Provider>, PaginationDataType> extends AInventoryUI<Provider, Parameter> {

  private static final String
    KEY_PREVIOUS_PAGE = "previousPage",
    KEY_CURRENT_PAGE = "currentPage",
    KEY_NEXT_PAGE = "nextPage";

  private final List<Integer> paginationSlotIndices;
  private final int pageSize;
  private final long animationPeriod;
  private final boolean animationsEnabled;

  private List<DataBoundUISlot<PaginationDataType>> pageableSlots;
  private int numberOfPageables;
  private boolean isFirstPageRender;

  private int currentPage;
  private int numberOfPages;

  public PageableInventoryUI(IInventoryRegistry registry, Parameter parameter, @Nullable AInventoryUI<?, ?> previousUi) {
    super(registry, parameter, previousUi);

    this.pageableSlots = new ArrayList<>();
    this.paginationSlotIndices = parameter.provider.getPaginationSlots(inventoryEnvironment);
    this.animationPeriod = parameter.provider.getAnimationPeriod();
    this.pageSize = this.paginationSlotIndices.size();
    this.isFirstPageRender = true;
    this.animationsEnabled = parameter.provider.isAnimating();
  }

  @Override
  protected void decorate() {
    super.decorate();

    IEvaluationEnvironment paginationEnvironment = getPaginationEnvironment();

    for (Map.Entry<String, Set<Integer>> contentEntry : slotContents.entrySet()) {
      Set<Integer> slots = contentEntry.getValue();
      UISlot slotContent = null;

      switch (contentEntry.getKey()) {
        case KEY_PREVIOUS_PAGE:
          slotContent = new UISlot(() -> parameter.provider.getPreviousPage().build(paginationEnvironment), this::handlePreviousPageClick);
          break;

        case KEY_NEXT_PAGE:
          slotContent = new UISlot(() -> parameter.provider.getNextPage().build(paginationEnvironment), this::handleNextPageClick);
          break;

        case KEY_CURRENT_PAGE:
          slotContent = new UISlot(() -> parameter.provider.getCurrentPage().build(paginationEnvironment));
          break;
      }

      if (slotContent == null)
        continue;

      setSlots(slotContent, slots);
    }

    drawPagination(null);
  }

  public void setPageableSlots(Collection<DataBoundUISlot<PaginationDataType>> items) {
    this.pageableSlots = new ArrayList<>(items);
    this.numberOfPageables = this.pageableSlots.size();

    if (this.pageSize == 0)
      this.numberOfPages = 0;
    else
      this.numberOfPages = (int) Math.ceil(this.numberOfPageables / (float) this.pageSize);

    setCurrentPage(0, null);
  }

  private void drawCurrentPage() {
    for (int i = 0; i < pageSize; i++) {
      int slot = paginationSlotIndices.get(i);
      int pageableSlotsIndex = this.currentPage * this.pageSize + i;

      UISlot slotValue;

      if (pageableSlotsIndex >= this.numberOfPageables)
        slotValue = null;
      else
        slotValue = pageableSlots.get(pageableSlotsIndex);

      setSlot(slot, slotValue);
      drawSlot(slot);
    }
  }

  private void drawPagination(@Nullable EAnimationType animationType) {
    int inventorySize = inventory.getSize() + 9 * 4;

    if (animationsEnabled)
      animator.saveLayout(inventorySize, this::getItem);

    this.drawCurrentPage();
    this.drawNamedSlot(KEY_PREVIOUS_PAGE);
    this.drawNamedSlot(KEY_CURRENT_PAGE);
    this.drawNamedSlot(KEY_NEXT_PAGE);

    if (animationsEnabled && !isFirstPageRender && animationType != null)
      animator.animateTo(animationType, paginationSlotIndices, inventorySize, this::getItem);

    isFirstPageRender = false;
  }

  private void setCurrentPage(int slot, @Nullable EAnimationType animationType) {
    this.currentPage = slot;

    if (isRegistered())
      this.drawPagination(animationType);
  }

  private EnumSet<EClickResultFlag> handlePreviousPageClick(UIInteraction action) {
    if (this.currentPage == 0)
      return null;

    if (action.clickType.isRightClick()) {
      setCurrentPage(0, EAnimationType.SLIDE_RIGHT);
      return null;
    }

    setCurrentPage(this.currentPage - 1, EAnimationType.SLIDE_RIGHT);
    return null;
  }

  private EnumSet<EClickResultFlag> handleNextPageClick(UIInteraction action) {
    if (this.currentPage >= numberOfPages - 1)
      return null;

    if (action.clickType.isRightClick()) {
      setCurrentPage(this.numberOfPages - 1, EAnimationType.SLIDE_LEFT);
      return null;
    }

    setCurrentPage(this.currentPage + 1, EAnimationType.SLIDE_LEFT);
    return null;
  }

  private IEvaluationEnvironment getPaginationEnvironment() {
    return new EvaluationEnvironmentBuilder()
      .withLiveVariable("viewer_name", parameter.viewer::getName)
      .withLiveVariable("current_page", () -> this.currentPage + 1)
      .withLiveVariable("page_size", () -> this.pageSize)
      .withLiveVariable("number_of_pages", () -> this.numberOfPages)
      .withLiveVariable("number_of_pageables", () -> this.numberOfPageables)
      .build();
  }

  @Override
  public void handleTick(long time) {
    if (time % animationPeriod == 0)
      this.animator.tick();
  }

  @Override
  public void handleInteraction(UIInteraction interaction) {
    this.animator.fastForward();
    super.handleInteraction(interaction);
  }
}
