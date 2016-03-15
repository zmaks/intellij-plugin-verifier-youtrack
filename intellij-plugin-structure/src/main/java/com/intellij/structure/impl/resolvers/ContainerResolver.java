package com.intellij.structure.impl.resolvers;

import com.intellij.structure.resolvers.Resolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dennis.Ushakov
 */
public class ContainerResolver extends Resolver {

  private final List<Resolver> myResolvers = new ArrayList<Resolver>();
  private final String myPresentableName;

  private ContainerResolver(@NotNull String presentableName, @NotNull List<Resolver> resolvers) {
    myPresentableName = presentableName;
    myResolvers.addAll(resolvers);
  }

  @NotNull
  public static Resolver createFromList(@NotNull String presentableName, @NotNull List<Resolver> resolvers) {
    Resolver nonEmpty = null;
    for (Resolver pool : resolvers) {
      if (!pool.isEmpty()) {
        if (nonEmpty == null) {
          nonEmpty = pool;
        } else {
          return new ContainerResolver(presentableName, resolvers);
        }
      }
    }
    if (nonEmpty == null) {
      return EmptyResolver.INSTANCE;
    }
    return nonEmpty;
  }

  @NotNull
  @Override
  public Set<String> getAllClasses() {
    Set<String> result = new HashSet<String>();
    for (Resolver pool : myResolvers) {
      result.addAll(pool.getAllClasses());
    }
    return result;
  }

  @Override
  public String toString() {
    return myPresentableName;
  }

  @Override
  public boolean isEmpty() {
    for (Resolver pool : myResolvers) {
      if (!pool.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Override
  @Nullable
  public ClassNode findClass(@NotNull String className) throws IOException {
    for (Resolver pool : myResolvers) {
      ClassNode node = pool.findClass(className);
      if (node != null) {
        return node;
      }
    }
    return null;
  }

  @Override
  @Nullable
  public Resolver getClassLocation(@NotNull String className) {
    for (Resolver pool : myResolvers) {
      Resolver inner = pool.getClassLocation(className);
      if (inner != null) {
        return inner;
      }
    }
    return null;
  }
}
