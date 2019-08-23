package com.jetbrains.plugin.structure.classes.resolvers

import org.objectweb.asm.tree.ClassNode

class FixedClassesResolver private constructor(
    private val classes: Map<String, ClassNode>,
    override val readMode: ReadMode,
    private val classFileOrigin: ClassFileOrigin
) : Resolver() {

  companion object {

    fun create(
        classes: Iterable<ClassNode>,
        classFileOrigin: ClassFileOrigin,
        readMode: ReadMode = ReadMode.FULL
    ): Resolver = FixedClassesResolver(
        classes.reversed().associateBy { it.name }, readMode, classFileOrigin
    )
  }

  private val packageSet = PackageSet()

  init {
    for (className in classes.keys) {
      packageSet.addPackagesOfClass(className)
    }
  }

  override fun processAllClasses(processor: (ClassNode) -> Boolean) =
      classes.values
          .asSequence()
          .all(processor)

  override fun resolveClass(className: String): ResolutionResult {
    val classNode = classes[className] ?: return ResolutionResult.NotFound
    return ResolutionResult.Found(classNode, classFileOrigin)
  }

  override val allClasses
    get() = classes.keys

  override val allPackages: Set<String>
    get() = packageSet.getAllPackages()

  override val isEmpty
    get() = classes.isEmpty()

  override fun containsClass(className: String) = className in classes

  override fun containsPackage(packageName: String) = packageSet.containsPackage(packageName)

  override fun close() = Unit

  override fun toString() = "Resolver of ${classes.size} predefined class" + (if (classes.size != 1) "es" else "")

}
