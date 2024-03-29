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

import me.blvckbytes.bukkitevaluable.IItemBuildable;
import me.blvckbytes.bukkitinventoryui.pageable.PageableUISection;

import java.lang.reflect.Field;
import java.util.List;

public class AnvilSearchUISection extends PageableUISection implements IAnvilSearchParameterProvider {

  private IItemBuildable filter;
  private IItemBuildable back;
  private IItemBuildable searchItem;
  private IItemBuildable resultItem;
  private IItemBuildable newButton;

  private int searchDebounceTicks;

  @Override
  public void afterParsing(List<Field> fields) throws Exception {
    super.afterParsing(fields);
    this.searchDebounceTicks = Math.max(0, searchDebounceTicks);
  }

  @Override
  public IItemBuildable getFilter() {
    return this.filter;
  }

  @Override
  public IItemBuildable getSearchItem() {
    return this.searchItem;
  }

  @Override
  public IItemBuildable getResultItem() {
    return this.resultItem;
  }

  @Override
  public IItemBuildable getBack() {
    return this.back;
  }

  @Override
  public IItemBuildable getNewButton() {
    return this.newButton;
  }

  @Override
  public int getSearchDebounceTicks() {
    return searchDebounceTicks;
  }
}
