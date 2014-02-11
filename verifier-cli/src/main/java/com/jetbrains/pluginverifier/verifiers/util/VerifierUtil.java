package com.jetbrains.pluginverifier.verifiers.util;

import com.jetbrains.pluginverifier.PluginVerifierOptions;
import com.jetbrains.pluginverifier.resolvers.Resolver;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class VerifierUtil {
  public static boolean classExists(PluginVerifierOptions opt, final Resolver resolver, final String className) {
    return classExists(opt, resolver, className, null);
  }

  public static boolean classExists(PluginVerifierOptions opt, final Resolver resolver, final String className, final Boolean isInterface) {
    if (isNativeType(className))
      return true;

    if (className.startsWith("[") || className.endsWith(";")) {
      final String name = prepareArrayName(className);
      // "" means it's a primitive type, validation can be skipped
      return "".equals(name) || isValidClassOrInterface(opt, resolver, name, isInterface);
    } else {
      return isValidClassOrInterface(opt, resolver, className, isInterface);
    }
  }

  private static boolean isValidClassOrInterface(PluginVerifierOptions opt, final Resolver resolver, final String name, final Boolean isInterface) {
    if (opt.isExternalClass(name)) {
      return true;
    }

    final ClassNode clazz = resolver.findClass(name);
    return clazz != null && (isInterface == null || isInterface == ((clazz.access & Opcodes.ACC_INTERFACE) != 0));
  }

  private static String prepareArrayName(final String className) {
    final String prefix = className.replaceAll("\\[", "");
    return prefix.substring(1, prefix.length() - (prefix.endsWith(";") ? 1 : 0));
  }

  public static boolean isNativeType(final String type) {
    if (type.length() != 1)
      return false;

    return "Z".equals(type) || "I".equals(type) || "J".equals(type) || "B".equals(type) ||
           "F".equals(type) || "S".equals(type) || "D".equals(type) || "C".equals(type); 
  }

  public static boolean isFinal(final MethodNode superMethod) {
    return (superMethod.access & Opcodes.ACC_FINAL) != 0;
  }

  public static boolean isAbstract(final MethodNode superMethod) {
    return (superMethod.access & Opcodes.ACC_ABSTRACT) != 0;
  }
}
