package com.jetbrains.pluginverifier.repository

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import com.jetbrains.pluginverifier.repository.repositories.marketplace.MarketplaceRepository
import com.jetbrains.pluginverifier.repository.repositories.marketplace.UpdateInfo
import org.junit.Assert.*
import org.junit.Test
import java.net.URL

class PublicPluginRepositoryTest : BaseRepositoryTest<MarketplaceRepository>() {

  companion object {
    val repositoryURL = URL("https://plugins.jetbrains.com")
  }

  override fun createRepository() = MarketplaceRepository(repositoryURL)

  @Test
  fun `last compatible plugins for IDE`() {
    val plugins = repository.getLastCompatiblePlugins(IdeVersion.createIdeVersion("173.3727.127"))
    assertFalse(plugins.isEmpty())
  }

  @Test
  fun `browser url`() {
    val versions = repository.getAllVersionsOfPlugin("Mongo Plugin")
    assertTrue(versions.isNotEmpty())
    val updateInfo = versions.first()
    assertEquals(URL(repositoryURL, "/plugin/7141"), updateInfo.browserUrl)
  }

  @Test
  fun updatesOfExistentPlugin() {
    val updates = repository.getAllVersionsOfPlugin("Pythonid")
    assertNotNull(updates)
    assertFalse(updates.isEmpty())
    val update = updates[0]
    assertEquals("Pythonid", update.pluginId)
    assertEquals("JetBrains", update.vendor)

  }

  @Test
  fun updatesOfNonExistentPlugin() {
    val updates = repository.getAllVersionsOfPlugin("NON_EXISTENT_PLUGIN")
    assertEquals(emptyList<UpdateInfo>(), updates)
  }

  @Test
  fun lastUpdate() {
    val info = repository.getLastCompatibleVersionOfPlugin(ideVersion, "org.jetbrains.kotlin")
    assertNotNull(info)
    assertTrue(info!!.updateId > 20000)
  }

  private val ideVersion: IdeVersion
    get() = IdeVersion.createIdeVersion("182.3040")

  @Test
  fun `find non existent plugin by update id`() {
    val updateInfo = repository.getPluginInfoById(-1000)
    assertNull(updateInfo)
  }

  @Test
  fun `request update info`() {
    //Plugin ".ignore 2.3.2"
    val updateInfo = repository.getPluginInfoById(40625)!!
    assertEquals(".ignore", updateInfo.pluginName)
    assertEquals("2.3.2", updateInfo.version)

    val updateIdToInfo = repository.getPluginInfosForManyIds(listOf(7495 to 40625))
    assertEquals(mapOf(40625 to updateInfo), updateIdToInfo)
  }

  @Test
  fun `download existing plugin`() {
    //Plugin ".ignore 2.3.2"
    val updateInfo = repository.getPluginInfoById(40625)!!
    checkDownloadPlugin(updateInfo)
  }

}
