// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere.core

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.searchEverywhere.SearchEverywhereItemPresentation
import com.intellij.searchEverywhere.SearchEverywhereItemsProvider
import com.intellij.searchEverywhere.SearchEverywhereListItem
import com.intellij.searchEverywhere.SearchEverywhereViewItemsProvider
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DefaultViewItemsProvider<Item, Presentation: SearchEverywhereItemPresentation, Params>(
  private val searchProvider: SearchEverywhereItemsProvider<Item, Params>,
  private val presentationRenderer: (Item) -> Presentation,
  private val dataContextRenderer: (Item) -> DataContext,
  private val descriptionRenderer: (Item) -> String? = { null },
) : SearchEverywhereViewItemsProvider<Item, Presentation, Params> {

  override suspend fun processViewItems(scope: CoroutineScope, searchParams: Params, processor: (SearchEverywhereListItem<Item, Presentation>) -> Boolean) {
    searchProvider.processItems(scope, searchParams) { item, weight ->
      val listItem = SearchEverywhereListItem<Item, Presentation>(item, presentationRenderer(item), weight, dataContextRenderer(item), descriptionRenderer(item))
      processor(listItem)
    }
  }
}