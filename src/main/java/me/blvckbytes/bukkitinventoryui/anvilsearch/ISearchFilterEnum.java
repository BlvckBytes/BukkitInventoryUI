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

import java.util.function.Function;

public interface ISearchFilterEnum<T extends Enum<?>, M> {

  String name();

  int ordinal();

  ISearchFilterEnum<T, M>[] listValues();

  /**
   * Get a sequence of normalized words (trimmed, lower-case) to search
   * through when provided a model's instance
   */
  Function<M, String[]> getWords();

  /**
   * Get the next enum value in the enum's ordinal sequence
   * and wrap around if performed on the last value
   * @return Next enum value
   */
  default ISearchFilterEnum<T, M> nextValue() {
    ISearchFilterEnum<T, M>[] values = listValues();
    return values[(ordinal() + 1) % values.length];
  }
}
