package com.dexform.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalAiTest {

    @Test
    fun testModelStatusDataClasses() {
        val notDownloaded = ModelStatus.NotDownloaded
        assertEquals(ModelStatus.NotDownloaded, notDownloaded)

        val downloading = ModelStatus.Downloading(progressPct = 45, downloadedBytes = 500L, totalBytes = 1000L)
        assertEquals(45, downloading.progressPct)

        val downloaded = ModelStatus.Downloaded(fileSizeBytes = 1000L, filePath = "/tmp/model.bin")
        assertEquals(1000L, downloaded.fileSizeBytes)

        val loaded = ModelStatus.LoadedInRam(fileSizeBytes = 1000L, ramUsageMb = 1400L)
        assertEquals(1400L, loaded.ramUsageMb)
    }
}
