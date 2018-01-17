package org.jetbrains.plugins.verifier.service.service.features

import com.jetbrains.intellij.feature.extractor.ExtensionPointFeatures
import com.jetbrains.intellij.feature.extractor.FeaturesExtractor
import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.pluginverifier.ide.IdeDescriptor
import com.jetbrains.pluginverifier.ide.IdeDescriptorsCache
import com.jetbrains.pluginverifier.plugin.PluginDetailsCache
import com.jetbrains.pluginverifier.repository.UpdateInfo
import org.jetbrains.plugins.verifier.service.tasks.ProgressIndicator
import org.jetbrains.plugins.verifier.service.tasks.ServiceTask
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * [ServiceTask] that runs the [feature extractor] [FeaturesExtractor] for the [updateInfo].
 */
class ExtractFeaturesTask(val updateInfo: UpdateInfo,
                          val ideDescriptorsCache: IdeDescriptorsCache,
                          val pluginDetailsCache: PluginDetailsCache) : ServiceTask<ExtractFeaturesTask.Result>("Features of $updateInfo") {

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(ExtractFeaturesTask::class.java)
  }

  /**
   * The result of the feature extractor service [task] [FeatureExtractorService].
   */
  data class Result(val updateInfo: UpdateInfo,
                    val resultType: ResultType,
                    val features: List<ExtensionPointFeatures>) {
    enum class ResultType {
      NOT_FOUND,
      BAD_PLUGIN,
      EXTRACTED_ALL,
      EXTRACTED_PARTIALLY
    }
  }

  override fun execute(progress: ProgressIndicator) = getSomeCompatibleIde().use {
    val ideDescriptor = it.resource
    pluginDetailsCache.getPluginDetailsCacheEntry(updateInfo).use {
      with(it) {
        when (this) {
          is PluginDetailsCache.Result.Provided -> runFeatureExtractor(ideDescriptor, pluginDetails.plugin)
          is PluginDetailsCache.Result.FileNotFound -> Result(updateInfo, Result.ResultType.NOT_FOUND, emptyList())
          is PluginDetailsCache.Result.InvalidPlugin -> Result(updateInfo, Result.ResultType.BAD_PLUGIN, emptyList())
          is PluginDetailsCache.Result.Failed -> {
            LOG.info("Unable to get plugin details for $updateInfo", error)
            Result(updateInfo, Result.ResultType.NOT_FOUND, emptyList())
          }
        }
      }
    }
  }

  private fun runFeatureExtractor(ideDescriptor: IdeDescriptor, plugin: IdePlugin): Result {
    val extractorResult = FeaturesExtractor.extractFeatures(ideDescriptor.ide, ideDescriptor.ideResolver, plugin)
    val resultType = when {
      extractorResult.extractedAll -> Result.ResultType.EXTRACTED_ALL
      else -> Result.ResultType.EXTRACTED_PARTIALLY
    }
    return Result(updateInfo, resultType, extractorResult.features)
  }

  /**
   * Selects any IDE compatible with the [updateInfo] among all the IDEs available in the cache now.
   */
  private fun getSomeCompatibleIde() =
      ideDescriptorsCache.getIdeDescriptor { availableIdes ->
        availableIdes.find { updateInfo.isCompatibleWith(it) }!!
      }

}