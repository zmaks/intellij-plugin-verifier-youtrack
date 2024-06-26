package com.jetbrains.plugin.structure.youtrack.mock

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jetbrains.plugin.structure.base.problems.*
import com.jetbrains.plugin.structure.base.utils.contentBuilder.buildDirectory
import com.jetbrains.plugin.structure.base.utils.contentBuilder.buildZipFile
import com.jetbrains.plugin.structure.base.utils.simpleName
import com.jetbrains.plugin.structure.mocks.BasePluginManagerTest
import com.jetbrains.plugin.structure.rules.FileSystemType
import com.jetbrains.plugin.structure.youtrack.YouTrackPlugin
import com.jetbrains.plugin.structure.youtrack.YouTrackPluginManager
import com.jetbrains.plugin.structure.youtrack.bean.YouTrackAppManifest
import com.jetbrains.plugin.structure.youtrack.bean.YouTrackAppWidget
import com.jetbrains.plugin.structure.youtrack.problems.*
import com.jetbrains.plugin.structure.youtrack.validateYouTrackManifest
import org.junit.Assert
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class YouTrackInvalidPluginTest(fileSystemType: FileSystemType) : BasePluginManagerTest<YouTrackPlugin, YouTrackPluginManager>(fileSystemType) {

  override fun createManager(extractDirectory: Path): YouTrackPluginManager {
    return YouTrackPluginManager.createManager(extractDirectory)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `file does not exist`() {
    assertProblematicPlugin(Paths.get("does-not-exist.zip"), emptyList())
  }

  @Test
  fun `invalid file extension`() {
    val incorrect = temporaryFolder.newFile("incorrect.txt")
    assertProblematicPlugin(incorrect, listOf(createIncorrectYouTrackPluginFileError(incorrect.simpleName)))
  }

  @Test
  fun `manifest json not found in directory`() {
    val pluginFile = buildDirectory(temporaryFolder.newFolder("app")) {
    }
    assertProblematicPlugin(pluginFile, listOf(PluginDescriptorIsNotFound(YouTrackPluginManager.DESCRIPTOR_NAME)))
  }

  @Test
  fun `manifest json not found in zip`() {
    val pluginFile = buildZipFile(temporaryFolder.newFolder().resolve("app.zip")) {
    }
    assertProblematicPlugin(pluginFile, listOf(PluginDescriptorIsNotFound(YouTrackPluginManager.DESCRIPTOR_NAME)))
  }

  @Test
  fun `invalid app name`() {
    checkInvalidPlugin(ManifestPropertyNotSpecified("name")) { it.copy(name = null) }
    checkInvalidPlugin(AppNameIsBlank()) { it.copy(name = "") }
    checkInvalidPlugin(UnsupportedSymbolsAppNameProblem()) { it.copy(name = "hello world") }
  }

  @Test
  fun `invalid app title`() {
    checkInvalidPlugin(ManifestPropertyNotSpecified("title")) { it.copy(title = null) }
    checkInvalidPlugin(ManifestPropertyNotSpecified("title")) { it.copy(title = "") }
  }

  @Test
  fun `app title is too long`() {
    var longTitle = "a"
    repeat(MAX_NAME_LENGTH) { longTitle += "a" }
    val expectedProblem = TooLongPropertyValue("manifest.json", "title", longTitle.length, MAX_NAME_LENGTH)
    checkInvalidPlugin(expectedProblem) { it.copy(title = longTitle) }
  }

  @Test
  fun `invalid app description`() {
    checkInvalidPlugin(ManifestPropertyNotSpecified("description")) { it.copy(description = null) }
    checkInvalidPlugin(ManifestPropertyNotSpecified("description")) { it.copy(description = "") }
  }

  @Test
  fun `invalid app version`() {
    checkInvalidPlugin(ManifestPropertyNotSpecified("version")) { it.copy(version = null) }
    checkInvalidPlugin(ManifestPropertyNotSpecified("version")) { it.copy(version = "") }
  }

  @Test
  fun `app changeNotes is too long`() {
    var longChangeNotes = "a"
    repeat(MAX_CHANGE_NOTES_LENGTH) { longChangeNotes += "a" }
    val expectedProblem = TooLongPropertyValue("manifest.json", "changeNotes", longChangeNotes.length, MAX_CHANGE_NOTES_LENGTH)
    checkInvalidPlugin(expectedProblem) { it.copy(changeNotes = longChangeNotes) }
  }

  @Test
  fun `null widget key`() {
    val widgets = listOf(widget.copy(key = null), widget)
    checkInvalidPlugin(WidgetKeyNotSpecified()) { it.copy(widgets = widgets) }
  }

  @Test
  fun `invalid widget key`() {
    val widgets = listOf(widget.copy(key = "AAA"), widget.copy(key = "???"), widget.copy("a.b_c-d~1"))
    checkInvalidPlugin(
      UnsupportedSymbolsWidgetKeyProblem("AAA"),
      UnsupportedSymbolsWidgetKeyProblem("???")
    ) { it.copy(widgets = widgets) }
  }

  @Test
  fun `widget key is not unique`() {
    val widgets = listOf(widget.copy(key = "a"), widget.copy(key = "b"), widget.copy("a"))
    checkInvalidPlugin(WidgetKeyIsNotUnique()) { it.copy(widgets = widgets) }
  }

  @Test
  fun `invalid widget indexPath`() {
    val widgets = listOf(widget.copy(key = "1", indexPath = null), widget.copy(key = "2", indexPath = null), widget)
    checkInvalidPlugin(
      WidgetManifestPropertyNotSpecified("indexPath", "1"),
      WidgetManifestPropertyNotSpecified("indexPath", "2")
    ) { it.copy(widgets = widgets) }
  }

  @Test
  fun `invalid widget extensionPoint`() {
    val widgets = listOf(widget.copy(key = "1", extensionPoint = null), widget.copy(key = "2", extensionPoint = null), widget)
    checkInvalidPlugin(
      WidgetManifestPropertyNotSpecified("extensionPoint", "1"),
      WidgetManifestPropertyNotSpecified("extensionPoint", "2")
    ) { it.copy(widgets = widgets) }
  }

  private fun checkInvalidPlugin(vararg expectedProblems: PluginProblem, modify: (YouTrackAppManifest) -> YouTrackAppManifest) {
    val manifestJson = getMockPluginFileContent("manifest.json")
    val manifest = modify(jacksonObjectMapper().readValue(manifestJson, YouTrackAppManifest::class.java))
    Assert.assertEquals(expectedProblems.toList(), validateYouTrackManifest(manifest))
  }

  private val widget: YouTrackAppWidget
    get() {
      val widgetJson = getMockPluginFileContent("widget.json")
      return jacksonObjectMapper().readValue(widgetJson, YouTrackAppWidget::class.java)
    }

}