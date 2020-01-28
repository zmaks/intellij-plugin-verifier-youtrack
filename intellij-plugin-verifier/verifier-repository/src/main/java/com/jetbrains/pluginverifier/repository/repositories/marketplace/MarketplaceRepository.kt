package com.jetbrains.pluginverifier.repository.repositories.marketplace

import com.google.common.collect.ImmutableMap
import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import com.jetbrains.pluginverifier.repository.PluginRepository
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import org.jetbrains.intellij.pluginRepository.model.IntellijUpdateMetadata
import java.net.MalformedURLException
import java.net.URL

class MarketplaceRepository(val repositoryURL: URL = DEFAULT_URL) : PluginRepository {

  private val pluginRepositoryInstance: org.jetbrains.intellij.pluginRepository.PluginRepository

  init {
    System.setProperty("MARKETPLACE_MAX_PARALLEL_CONNECTIONS", "512")
    pluginRepositoryInstance = PluginRepositoryFactory.create(host = repositoryURL.toExternalForm())
  }

  override fun getLastCompatiblePlugins(ideVersion: IdeVersion): List<UpdateInfo> {
    val pluginManager = pluginRepositoryInstance.pluginManager
    val result = arrayListOf<UpdateInfo>()
    val pluginsXmlIds = pluginManager.getCompatiblePluginsXmlIds(ideVersion.asString(), 10000, 0)
    val updates = pluginManager.searchCompatibleUpdates(pluginsXmlIds, ideVersion.asString())
    val updateIdToPluginId = updates.associateBy({ it.id }, { it.pluginId })
    val updateIdToMetadata = pluginRepositoryInstance.pluginUpdateManager.getIntellijUpdateMetadataBatch(
      updates.map { it.pluginId to it.id }
    )
    for ((updateId, metadata) in updateIdToMetadata) {
      result += createUpdateInfo(metadata, updateIdToPluginId.getValue(updateId))
    }
    return result
  }

  override fun getLastCompatibleVersionOfPlugin(ideVersion: IdeVersion, pluginId: String): UpdateInfo? {
    val compatibleUpdates = pluginRepositoryInstance.pluginManager.searchCompatibleUpdates(listOf(pluginId), ideVersion.asString())
    val compatibleUpdate = compatibleUpdates.firstOrNull() ?: return null
    return requestAndCreateUpdateInfo(compatibleUpdate.pluginId, compatibleUpdate.id)
  }

  override fun getAllVersionsOfPlugin(pluginId: String): List<UpdateInfo> {
    val pluginBean = pluginRepositoryInstance.pluginManager.getPluginByXmlId(pluginId) ?: return emptyList()
    val pluginVersions = pluginRepositoryInstance.pluginManager.getPluginVersions(pluginBean.id)
    val metadataList = pluginRepositoryInstance.pluginUpdateManager.getIntellijUpdateMetadataBatch(
      pluginVersions.map { pluginBean.id to it.id }
    ).values
    return metadataList.map { createUpdateInfo(it, pluginBean.id) }
  }

  override fun getIdOfPluginDeclaringModule(moduleId: String): String? =
    INTELLIJ_MODULE_TO_CONTAINING_PLUGIN[moduleId]

  private fun requestAndCreateUpdateInfo(pluginId: Int, updateId: Int): UpdateInfo? {
    val updateMetadata = pluginRepositoryInstance.pluginUpdateManager.getIntellijUpdateMetadata(pluginId, updateId)
    return updateMetadata?.let { createUpdateInfo(it, pluginId) }
  }

  private fun createUpdateInfo(
    metadata: IntellijUpdateMetadata,
    pluginId: Int
  ) = UpdateInfo(
    metadata.xmlId,
    metadata.name,
    metadata.version,
    metadata.since.prepareIdeVersion(),
    metadata.until.prepareIdeVersion(),
    metadata.vendor,
    parseSourceCodeUrl(metadata.sourceCodeUrl),
    getDownloadUrl(metadata.id),
    metadata.id,
    getBrowserUrl(pluginId),
    metadata.tags,
    pluginId
  )

  fun getPluginInfoById(updateId: Int): UpdateInfo? {
    val pluginUpdateBean = pluginRepositoryInstance.pluginUpdateManager.getUpdateById(updateId) ?: return null
    return requestAndCreateUpdateInfo(pluginUpdateBean.pluginId, updateId)
  }

  fun getPluginInfosForManyIds(pluginAndUpdateIds: List<Pair<Int, Int>>): Map<Int, UpdateInfo> {
    val updateIdToPluginId = pluginAndUpdateIds.associateBy({ it.second }, { it.first })
    val result = hashMapOf<Int, UpdateInfo>()
    for ((updateId, metadata) in pluginRepositoryInstance.pluginUpdateManager.getIntellijUpdateMetadataBatch(pluginAndUpdateIds)) {
      val pluginId = updateIdToPluginId[updateId] ?: continue
      result[updateId] = createUpdateInfo(metadata, pluginId)
    }
    return result
  }

  private fun getBrowserUrl(pluginId: Int) =
    URL("${repositoryURL.toExternalForm().trimEnd('/')}/plugin/$pluginId")

  private fun getDownloadUrl(updateId: Int) =
    URL("${repositoryURL.toExternalForm().trimEnd('/')}/plugin/download/?noStatistic=true&updateId=$updateId")

  private fun String?.prepareIdeVersion(): IdeVersion? =
    if (this == null || this == "" || this == "0.0") {
      null
    } else {
      IdeVersion.createIdeVersionIfValid(this)
    }

  private fun parseSourceCodeUrl(url: String?): URL? {
    if (url.isNullOrBlank()) {
      return null
    }
    return try {
      URL(url)
    } catch (e: MalformedURLException) {
      null
    }
  }

  private companion object {

    private val DEFAULT_URL = URL("https://plugins.jetbrains.com")

    /**
     * TODO: implement this mapping on the Plugins Repository: MP-1152.
     * Currently, the Plugin Repository doesn't know about what modules are declared in what plugins.
     *
     * The list of IntelliJ plugins which define some modules
     * (e.g. the plugin "org.jetbrains.plugins.ruby" defines a module "com.intellij.modules.ruby")
     */
    private val INTELLIJ_MODULE_TO_CONTAINING_PLUGIN = ImmutableMap.of(
      "com.intellij.modules.ruby", "org.jetbrains.plugins.ruby",
      "com.intellij.modules.php", "com.jetbrains.php",
      "com.intellij.modules.python", "Pythonid",
      "com.intellij.modules.swift.lang", "com.intellij.clion-swift"
    )
  }
}