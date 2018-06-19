package com.jetbrains.pluginverifier.parameters.filtering

import java.util.*

/**
 * Condition to ignore compatibility problems:
 * - ID and version of the plugin to ignore problems of
 * - pattern - RegExp pattern of the short description
 */
data class IgnoreCondition(
    val pluginId: String?,
    val version: String?,
    val pattern: Regex
) {

  companion object {
    private val USAGE = """
      Ignoring line must be in the form: [<plugin_xml_id>[:<plugin_version>]:]<problem_description_regexp_pattern>
    Examples:
    org.some.plugin:3.4.0:access to unresolved class org.foo.Foo.*                    --- ignore for plugin 'org.some.plugin' of version 3.4.0
    org.jetbrains.kotlin::access to unresolved class org.jetbrains.kotlin.compiler.*  --- ignore for all versions of Kotlin plugin
    access to unresolved class org.jetbrains.kotlin.compiler.*                        --- ignore for all plugins
    """.trimIndent()

    /**
     * Parses [IgnoreCondition] from this [line], which must be in one of the following forms:
     * 1) "<plugin-id>:<version>:<ignoring-pattern>"
     * 2) "<plugin-id>:<ignoring-pattern>"
     * 3) "<ignoring-pattern>"
     */
    fun parseCondition(line: String): IgnoreCondition {
      val tokens = line.split(":").map { it.trim() }
      val parseRegexp = { s: String -> Regex(s, RegexOption.IGNORE_CASE) }
      return when {
        tokens.size == 1 -> IgnoreCondition(null, null, parseRegexp(tokens[0]))
        tokens.size == 2 -> IgnoreCondition(tokens[0], null, parseRegexp(tokens[1]))
        tokens.size == 3 -> IgnoreCondition(tokens[0].takeIf { it.isNotEmpty() }, tokens[1].takeIf { it.isNotEmpty() }, parseRegexp(tokens[2]))
        else -> throw IllegalArgumentException("Incorrect problem ignoring line\n$line\n$USAGE")
      }
    }
  }

  /**
   * Serializes this [IgnoreCondition] to [String].
   * It can be later deserialized via [parseCondition].
   */
  fun serializeCondition() = buildString {
    if (pluginId != null) {
      append(pluginId).append(":")
    }
    if (version != null) {
      append(version).append(":")
    }
    append(pattern.pattern)
  }

  override fun equals(other: Any?) = other is IgnoreCondition
      && pluginId == other.pluginId
      && version == other.version
      && pattern.pattern == other.pattern.pattern

  override fun hashCode() = Objects.hash(pluginId, version, pattern.pattern)
}