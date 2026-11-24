package com.getmobilestack.kmp.pintar.shopping

/** Selection uses cart entry IDs; checkout uses the corresponding product IDs. */
fun selectedCartEntries(entries: List<ShoppingEntry>, selectedIds: Set<String>): List<ShoppingEntry> =
    entries.filter { it.id in selectedIds && it.item.isAvailable }

fun reconcileCartSelection(entries: List<ShoppingEntry>, selectedIds: Set<String>): Set<String> =
    selectedIds intersect entries.filter { it.item.isAvailable }.map { it.id }.toSet()
