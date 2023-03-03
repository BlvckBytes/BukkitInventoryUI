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

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class InventoryAnimator {

  private final BiConsumer<Integer, ItemStack> setter;

  private ItemStack @Nullable [] fromLayout, toLayout;
  private @Nullable EAnimationType animationType;
  private List<Integer> mask;
  private int numberOfFrames;
  private int numberOfRows;
  private int currentFrame;

  private int slotOffset;

  public InventoryAnimator(BiConsumer<Integer, ItemStack> setter) {
    this.setter = setter;
  }

  public void setSlotOffset(int slotOffset) {
    this.slotOffset = slotOffset;
  }

  public void animateTo(EAnimationType animationType, List<Integer> mask, int inventorySize, Function<Integer, ItemStack> itemGetter) {
    inventorySize = Math.max(0, inventorySize - slotOffset);

    if (inventorySize % 9 != 0)
      return;

    if (this.toLayout == null || this.toLayout.length >= inventorySize)
      this.toLayout = new ItemStack[inventorySize];

    for (int i = 0; i < inventorySize; i++)
      this.toLayout[i] = itemGetter.apply(i + slotOffset);

    this.animationType = animationType;
    this.mask = mask;
    this.numberOfRows = inventorySize / 9;
    this.numberOfFrames = getNumberOfFrames(animationType);
    this.currentFrame = 0;

    drawCurrentFrame();
  }

  public void fastForward() {
    this.currentFrame = this.numberOfFrames - 1;
    drawCurrentFrame();
    this.animationType = null;
  }

  public void saveLayout(int inventorySize, Function<Integer, ItemStack> itemGetter) {
    inventorySize = Math.max(0, inventorySize - slotOffset);

    if (this.fromLayout == null || this.fromLayout.length >= inventorySize)
      this.fromLayout = new ItemStack[inventorySize];

    for (int i = 0; i < inventorySize; i++)
      this.fromLayout[i] = itemGetter.apply(i + slotOffset);
  }

  public void tick() {
    if (this.animationType == null)
      return;

    if (++currentFrame < numberOfFrames) {
      drawCurrentFrame();
      return;
    }

    this.animationType = null;
  }

  private void drawCurrentFrame() {
    if (this.animationType == null || this.fromLayout == null || this.toLayout == null)
      return;

    switch (this.animationType) {
      // Drawing columns
      case SLIDE_LEFT:
      case SLIDE_RIGHT:
      {
        for (int drawCol = 0; drawCol < 9; drawCol++) {
          ItemStack[] origin;
          int readCol;

          if (this.animationType == EAnimationType.SLIDE_LEFT) {
            if (drawCol < (numberOfFrames - currentFrame - 1)) {
              origin = fromLayout;
              readCol = drawCol + currentFrame + 1;
            } else {
              origin = toLayout;
              readCol = drawCol - (8 - currentFrame);
            }
          }

          else {
            if (drawCol > currentFrame) {
              origin = fromLayout;
              readCol = drawCol - currentFrame - 1;
            }
            else {
              origin = toLayout;
              readCol = 8 - currentFrame + drawCol;
            }
          }

          for (int i = 0; i < numberOfRows * 9; i += 9) {
            int destinationSlot = drawCol + i;
            int sourceSlot = readCol + i;

            if (mask == null || (mask.contains(destinationSlot + slotOffset) && mask.contains(sourceSlot + slotOffset)))
              this.setter.accept(destinationSlot + slotOffset, getItem(origin, sourceSlot));
          }
        }
        break;
      }

      // Drawing rows
      case SLIDE_DOWN:
      case SLIDE_UP:
      {
        for (int drawRow = 0; drawRow < numberOfRows; drawRow++) {
          ItemStack[] origin;
          int readRow;

          if (this.animationType == EAnimationType.SLIDE_DOWN) {
            if (drawRow > currentFrame) {
              origin = fromLayout;
              readRow = drawRow - (currentFrame + 1);
            } else {
              origin = toLayout;
              readRow = drawRow + (numberOfRows - currentFrame - 1);
            }
          }

          else {
            if (drawRow < (numberOfFrames - currentFrame - 1)) {
              origin = fromLayout;
              readRow = drawRow + (currentFrame + 1);
            } else {
              origin = toLayout;
              readRow = drawRow - (numberOfRows - currentFrame - 1);
            }
          }

          for (int i = 0; i < 9; i++) {
            int destinationSlot = drawRow * 9 + i;
            int sourceSlot = readRow * 9 + i;

            if (mask == null || (mask.contains(destinationSlot + slotOffset) && mask.contains(sourceSlot + slotOffset)))
              this.setter.accept(destinationSlot + slotOffset, getItem(origin, sourceSlot));
          }
        }
        break;
      }
    }
  }

  private ItemStack getItem(ItemStack[] contents, int slot) {
    if (slot >= contents.length)
      return null;
    return contents[slot];
  }

  private int getNumberOfFrames(EAnimationType animationType) {
    switch (animationType) {
      // Bottom and top will both take as many frames as there are rows
      case SLIDE_UP:
      case SLIDE_DOWN:
        return this.numberOfRows;

      // Left and right take as many frames as there are horizontal slots
      case SLIDE_RIGHT:
      case SLIDE_LEFT:
        return 9;

      default:
        return 0;
    }
  }
}
