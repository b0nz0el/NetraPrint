package com.netraprint.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrintHistoryManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testAddAndRetrieveHistory() {
        val manager = PrintHistoryManager(tempFolder.root, maxItems = 10)
        manager.addEntry(
            title = "Resi Pengiriman #001",
            printType = "FORM",
            targetName = "MPT-II",
            targetInfo = "00:11:22:33:44:55",
            status = PrintHistoryItem.Status.SUCCESS,
            copies = 1
        )
        manager.addEntry(
            title = "Struk Pembelian #002",
            printType = "FREE_TEXT",
            targetName = "192.168.1.100",
            targetInfo = "9100",
            status = PrintHistoryItem.Status.QUEUED_OFFLINE,
            copies = 2
        )

        val list = manager.getAll()
        assertEquals(2, list.size)
        // Newest first
        assertEquals("Struk Pembelian #002", list[0].title)
        assertEquals(PrintHistoryItem.Status.QUEUED_OFFLINE, list[0].status)
        assertEquals(2, list[0].copies)

        assertEquals("Resi Pengiriman #001", list[1].title)
        assertEquals(PrintHistoryItem.Status.SUCCESS, list[1].status)
    }

    @Test
    fun testMaxItemsLimit() {
        val manager = PrintHistoryManager(tempFolder.root, maxItems = 3)
        manager.addEntry("Doc 1", "PDF", "Printer A")
        manager.addEntry("Doc 2", "PDF", "Printer A")
        manager.addEntry("Doc 3", "PDF", "Printer A")
        manager.addEntry("Doc 4", "PDF", "Printer A")

        val list = manager.getAll()
        assertEquals(3, list.size)
        assertEquals("Doc 4", list[0].title)
        assertEquals("Doc 3", list[1].title)
        assertEquals("Doc 2", list[2].title)
    }

    @Test
    fun testPersistenceReload() {
        val storageDir = tempFolder.newFolder("history_storage")
        val manager1 = PrintHistoryManager(storageDir, maxItems = 10)
        manager1.addEntry("Nota Transaksi", "IMAGE", "Printer BT", status = PrintHistoryItem.Status.SUCCESS)

        // Buat instance baru untuk memverifikasi loadFromDisk
        val manager2 = PrintHistoryManager(storageDir, maxItems = 10)
        val list = manager2.getAll()
        assertEquals(1, list.size)
        assertEquals("Nota Transaksi", list[0].title)
        assertEquals("IMAGE", list[0].printType)
    }

    @Test
    fun testClearAndDeleteItem() {
        val manager = PrintHistoryManager(tempFolder.root, maxItems = 10)
        val item1 = manager.addEntry("Doc 1", "PDF", "Printer A")
        val item2 = manager.addEntry("Doc 2", "PDF", "Printer B")

        assertEquals(2, manager.getCount())

        val deleted = manager.deleteItem(item1.id)
        assertTrue(deleted)
        assertEquals(1, manager.getCount())
        assertEquals("Doc 2", manager.getAll()[0].title)

        manager.clearAll()
        assertEquals(0, manager.getCount())
        assertTrue(manager.getAll().isEmpty())
    }
}
