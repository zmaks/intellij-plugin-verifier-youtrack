package org.jetbrains.plugins.verifier.service.setting

import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Provides handy access to the settings and system properties.
 */
enum class Settings(val key: String,
                    private val default: (() -> String?)? = null,
                    val encrypted: Boolean = false) {
  APP_HOME_DIRECTORY("verifier.service.home.dir"),
  JDK_8_HOME("verifier.service.jdk.8.dir"),
  MAX_DISK_SPACE_MB("verifier.service.max.disk.space.mb", { (50 * 1024).toString() }),
  IDE_REPOSITORY_URL("verifier.service.ide.repository.url", { "https://www.jetbrains.com" }),

  PLUGINS_REPOSITORY_URL("verifier.service.plugins.repository.url", { PUBLIC_PLUGIN_REPOSITORY_URL }),

  ENABLE_FEATURE_EXTRACTOR_SERVICE("verifier.service.enable.feature.extractor.service", { "false" }),
  ENABLE_PLUGIN_VERIFIER_SERVICE("verifier.service.enable.plugin.verifier.service", { "false" }),
  ENABLE_IDE_LIST_UPDATER("verifier.service.enable.ide.list.updater", { "false" }),
  PLUGIN_REPOSITORY_VERIFIER_USERNAME("verifier.service.plugin.repository.verifier.username", encrypted = true),
  PLUGIN_REPOSITORY_VERIFIER_PASSWORD("verifier.service.plugin.repository.verifier.password", encrypted = true),

  TASK_MANAGER_CONCURRENCY("verifier.service.task.manager.concurrency", { "4" }),

  SERVICE_ADMIN_PASSWORD("verifier.service.admin.password", encrypted = true);

  fun get(): String {
    val property = System.getProperty(key)
    if (property != null) {
      return property
    }
    return default?.invoke() ?: throw IllegalStateException("The property $key should be set")
  }

  fun getAsURL(): URL = URL(get().trimEnd('/'))

  fun getAsPath(): Path = Paths.get(get())

  fun getAsBoolean(): Boolean = get().toBoolean()

  fun getAsInt(): Int = get().toInt()

  fun getAsLong(): Long = get().toLong()

  companion object {
    private val PUBLIC_PLUGIN_REPOSITORY_URL = "https://plugins.jetbrains.com"
  }
}