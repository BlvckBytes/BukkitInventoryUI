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

import me.blvckbytes.bukkitinventoryui.base.*;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;
import me.blvckbytes.gpeee.interpreter.IEvaluationEnvironment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PageableInventoryUI<DataType> implements IPageableInventoryUI<DataType> {

  private static final String
    KEY_PREVIOUS_PAGE = "previousPage",
    KEY_CURRENT_PAGE = "currentPage",
    KEY_NEXT_PAGE = "nextPage";

  private final InventoryAnimator animator;
  private final IInventoryUI handle;
  private final List<Integer> paginationSlotIndices;
  private final int pageSize;
  private final long animationPeriod;
  private final boolean animationsEnabled;

  private List<DataBoundUISlot<DataType>> pageableSlots;
  private int numberOfPageables;
  private boolean isFirstPageRender;

  private int currentPage;
  private int numberOfPages;

  private final IPageableParameterProvider parameterProvider;

  public PageableInventoryUI(IPageableParameterProvider parameterProvider, IInventoryUI handle) {
    this.pageableSlots = new ArrayList<>();
    this.handle = handle;
    this.parameterProvider = parameterProvider;
    this.paginationSlotIndices = parameterProvider.getPaginationSlots(handle.getInventoryEnvironment());
    this.animationPeriod = parameterProvider.getAnimationPeriod();
    this.animationsEnabled = parameterProvider.isAnimating();
    this.pageSize = this.paginationSlotIndices.size();
    this.isFirstPageRender = true;
    this.animator = new InventoryAnimator(handle::setItem);
  }

  @Override
  public void setPageableSlots(Collection<DataBoundUISlot<DataType>> items) {
    this.pageableSlots = new ArrayList<>(items);
    this.numberOfPageables = this.pageableSlots.size();

    if (this.pageSize == 0)
      this.numberOfPages = 0;
    else
      this.numberOfPages = (int) Math.ceil(this.numberOfPageables / (float) this.pageSize);

    setCurrentPage(0, null);
  }

  public void setSlotOffset(int offset) {
    this.animator.setSlotOffset(offset);
  }

  @Override
  public void handleTick(long time) {
    if (time % animationPeriod == 0)
      this.animator.tick();
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
    this.animator.fastForward();
    this.handle.handleInteraction(interaction);
  }

  @Override
  public void handleClose() {
    this.handle.handleClose();
  }

  @Override
  public void show() {
    this.handle.show();
    this.setPaginationSlots();
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

  private void drawCurrentPage() {
    for (int i = 0; i < pageSize; i++) {
      int slot = paginationSlotIndices.get(i);
      int pageableSlotsIndex = this.currentPage * this.pageSize + i;

      UISlot slotValue;

      if (pageableSlotsIndex >= this.numberOfPageables)
        slotValue = null;
      else
        slotValue = pageableSlots.get(pageableSlotsIndex);

      setSlotById(slot, slotValue);
      drawSlotById(slot);
    }
  }

  private void drawPagination(@Nullable EAnimationType animationType) {
    int inventorySize = handle.getInventory().getSize() + 9 * 4;

    if (animationsEnabled)
      animator.saveLayout(inventorySize, this.handle::getItem);

    this.drawCurrentPage();
    this.handle.drawSlotByName(KEY_PREVIOUS_PAGE);
    this.handle.drawSlotByName(KEY_CURRENT_PAGE);
    this.handle.drawSlotByName(KEY_NEXT_PAGE);

    if (animationsEnabled && !isFirstPageRender && animationType != null)
      animator.animateTo(animationType, paginationSlotIndices, inventorySize, this.handle::getItem);

    isFirstPageRender = false;
  }

  private void setCurrentPage(int slot, @Nullable EAnimationType animationType) {
    this.currentPage = slot;
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
      .withLiveVariable("viewer_name", getViewer()::getName)
      .withLiveVariable("current_page", () -> this.currentPage + 1)
      .withLiveVariable("page_size", () -> this.pageSize)
      .withLiveVariable("number_of_pages", () -> this.numberOfPages)
      .withLiveVariable("number_of_pageables", () -> this.numberOfPageables)
      .build();
  }

  private void setPaginationSlots() {
    IEvaluationEnvironment paginationEnvironment = getPaginationEnvironment();

    handle.setSlotByName(KEY_PREVIOUS_PAGE, new UISlot(() -> parameterProvider.getPreviousPage().build(paginationEnvironment), this::handlePreviousPageClick));
    handle.setSlotByName(KEY_NEXT_PAGE, new UISlot(() -> parameterProvider.getNextPage().build(paginationEnvironment), this::handleNextPageClick));
    handle.setSlotByName(KEY_CURRENT_PAGE, new UISlot(() -> parameterProvider.getCurrentPage().build(paginationEnvironment)));

    drawPagination(null);
  }
}
