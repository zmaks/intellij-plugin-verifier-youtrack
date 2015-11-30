package com.intellij.structure.domain;

import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.intellij.structure.errors.BrokenPluginException;
import com.intellij.structure.pool.ClassPool;
import com.intellij.structure.pool.ContainerClassPool;
import com.intellij.structure.pool.InMemoryJarClassPool;
import com.intellij.structure.pool.JarClassPool;
import com.intellij.structure.utils.ProductUpdateBuild;
import com.intellij.structure.utils.StringUtil;
import com.intellij.structure.utils.Util;
import com.intellij.structure.utils.xml.JDOMUtil;
import com.intellij.structure.utils.xml.JDOMXIncluder;
import com.intellij.structure.utils.xml.XIncludeException;
import org.apache.commons.io.IOUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class IdeaPlugin {

  private static final String META_INF_ENTRY = "META-INF/";
  private static final String PLUGIN_XML_ENTRY_NAME = META_INF_ENTRY + "plugin.xml";
  private static final String CLASS_SUFFIX = ".class";
  private static final Pattern XML_IN_ROOT_PATTERN = Pattern.compile("([^/]*/)?META-INF/.+\\.xml");

  private final ClassPool myPluginClassPool;
  private final ClassPool myLibraryClassPool;
  private final ClassPool myAllClassesClassPool;

  private final String myPluginName;
  private final String myPluginVersion;
  private final String myPluginId;
  private final String myPluginVendor;

  private final List<PluginDependency> myDependencies = new ArrayList<PluginDependency>();
  private final List<PluginDependency> myModuleDependencies = new ArrayList<PluginDependency>();
  private final Set<String> myDefinedModules;
  private final Document myPluginXml;
  private final Map<String, Document> myXmlDocumentsInRoot;
  private ProductUpdateBuild mySinceBuild;
  private ProductUpdateBuild myUntilBuild;

  private IdeaPlugin(@NotNull File pluginFile,
                     @NotNull ClassPool pluginClassPool,
                     @NotNull ClassPool libraryClassPool,
                     @NotNull Document pluginXml,
                     @NotNull Map<String, Document> xmlDocumentsInRoot) throws BrokenPluginException {
    myPluginXml = pluginXml;
    myXmlDocumentsInRoot = xmlDocumentsInRoot;
    myPluginClassPool = pluginClassPool;
    myLibraryClassPool = libraryClassPool;
    myAllClassesClassPool = ContainerClassPool.getUnion(pluginFile.getAbsolutePath(), Arrays.asList(pluginClassPool, libraryClassPool));

    myPluginId = getPluginId(pluginXml);
    if (myPluginId == null) {
      throw new BrokenPluginException("No id or name in plugin.xml for plugin: " + pluginFile);
    }

    String name = pluginXml.getRootElement().getChildTextTrim("name");
    if (Strings.isNullOrEmpty(name)) {
      name = myPluginId;
    }
    myPluginName = name;
    myPluginVersion = pluginXml.getRootElement().getChildTextTrim("version");
    myPluginVendor = pluginXml.getRootElement().getChildTextTrim("vendor");

    loadPluginDependencies(pluginXml);
    myDefinedModules = loadModules(pluginXml);

    getIdeaVersion(pluginXml);
  }

  @Nullable
  private static String getPluginId(@NotNull Document pluginXml) {
    String id = pluginXml.getRootElement().getChildText("id");
    if (id == null || id.isEmpty()) {
      String name = pluginXml.getRootElement().getChildText("name");
      if (name == null || name.isEmpty()) {
        return null;
      }
      return name;
    }
    return id;
  }

  @NotNull
  public static IdeaPlugin createIdeaPlugin(@NotNull File pluginFile) throws IOException, BrokenPluginException {
    if (!pluginFile.exists()) {
      throw new IOException("Plugin is not found: " + pluginFile);
    }

    if (pluginFile.isFile()) {
      String fileName = pluginFile.getName();
      if (fileName.endsWith(".zip")) {
        return createFromZip(pluginFile);
      }
      if (fileName.endsWith(".jar")) {
        return createFromJar(pluginFile);
      }
      throw new BrokenPluginException("Unknown input file type: " + pluginFile);
    }

    File[] pluginRootFiles = pluginFile.listFiles();
    if (pluginRootFiles == null || pluginRootFiles.length == 0) {
      throw new BrokenPluginException("Plugin root directory " + pluginFile + " is empty");
    }
    if (pluginRootFiles.length > 1) {
      throw new BrokenPluginException("Plugin root directory " + pluginFile + " contains more than one child \"lib\"");
    }
    return createFromDirectory(pluginFile);
  }

  @NotNull
  public static IdeaPlugin createIdeaPlugin(@NotNull String pathToPlugin) throws IOException, BrokenPluginException {
    return createIdeaPlugin(new File(pathToPlugin));
  }

  @NotNull
  private static ClassNode getClassFromInputStream(InputStream inputStream) throws IOException {
    BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
    ClassNode result = new ClassNode();
    new ClassReader(bufferedInputStream).accept(result, 0);
    return result;
  }

  @NotNull
  public static IdeaPlugin createFromZip(@NotNull File zipFile) throws IOException, BrokenPluginException {
    byte[] pluginXmlBytes = null;
    ClassPool pluginClassPool = null;
    List<ClassPool> libraryPool = new ArrayList<ClassPool>();
    URL pluginXmlUrl = null;
    Map<String, Document> allXmlInRoot = new HashMap<String, Document>();

    InMemoryJarClassPool zipRootPool = new InMemoryJarClassPool("PLUGIN_ROOT: " + zipFile.getAbsolutePath());

    final ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));

    try {
      ZipEntry entry;
      while ((entry = zipInputStream.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;

        String entryName = entry.getName();

        if (entryName.endsWith(CLASS_SUFFIX)) {
          //simply .class-file
          ClassNode node = new ClassNode();
          new ClassReader(zipInputStream).accept(node, 0);
          zipRootPool.addClass(node);

        } else if (isXmlInRoot(entryName)) {
          //some *.xml file (maybe META-INF/plugin.xml)

          final byte[] data = ByteStreams.toByteArray(zipInputStream);
          final URL xmlUrl = new URL(
              "jar:" + StringUtil.replace(zipFile.toURI().toASCIIString(), "!", "%21") + "!/" + entryName
          );

          boolean isPluginXml = entryName.endsWith(PLUGIN_XML_ENTRY_NAME);

          if (isPluginXml) {
            //this is the main plugin.xml i.e. META-INF/plugin.xml

            if (pluginXmlBytes != null && !Arrays.equals(data, pluginXmlBytes)) {
              throw new BrokenPluginException("Plugin has more than one jars with plugin.xml");
            }
            pluginXmlBytes = data;
            pluginClassPool = zipRootPool;
            pluginXmlUrl = xmlUrl;
          }

          //if it is the first .xml-file
          tryAddXmlInRoot(allXmlInRoot, entryName, data, xmlUrl, isPluginXml);

        } else if (entryName.matches("([^/]+/)?lib/[^/]+\\.jar")) {
          ZipInputStream innerJar = new ZipInputStream(zipInputStream);

          final InMemoryJarClassPool innerPool = new InMemoryJarClassPool(entryName);
          final Map<String, Document> innerDocuments = new HashMap<String, Document>();

          ZipEntry innerEntry;
          while ((innerEntry = innerJar.getNextEntry()) != null) {
            String name = innerEntry.getName();

            if (name.startsWith(META_INF_ENTRY) && name.endsWith(".xml")) {
              //some xml

              final byte[] data = ByteStreams.toByteArray(innerJar);
              final URL xmlUrl = new URL("jar:" + StringUtil.replace(zipFile.toURI().toASCIIString(), "!", "%21") + "!/" + entryName + "!/META-INF/plugin.xml");

              boolean isPluginXml = name.equals(PLUGIN_XML_ENTRY_NAME);
              if (isPluginXml) {
                if (pluginXmlBytes != null && !Arrays.equals(data, pluginXmlBytes)) {
                  throw new BrokenPluginException("Plugin has more than one jars with plugin.xml");
                }

                pluginXmlBytes = data; //the main plugin.xml
                pluginClassPool = innerPool; //pluginPool is in this .jar exactly
                allXmlInRoot = innerDocuments; //and documents are based on this META-INF/* directory

                pluginXmlUrl = xmlUrl;
              }

              //parse a document and add to list of all xml-s
              tryAddXmlInRoot(innerDocuments, name, data, xmlUrl, isPluginXml);

            } else if (name.endsWith(CLASS_SUFFIX)) {
              innerPool.addClass(name.substring(0, name.length() - CLASS_SUFFIX.length()), ByteStreams.toByteArray(innerJar));
            }
          }

          if (innerPool != pluginClassPool) {
            libraryPool.add(innerPool);
          }
        }
      }
    } finally {
      IOUtils.closeQuietly(zipInputStream);
    }

    if (pluginXmlBytes == null) {
      throw new BrokenPluginException("No plugin.xml found for plugin " + zipFile.getPath());
    }

    Document pluginXml;

    try {
      pluginXml = JDOMUtil.loadDocument(new ByteArrayInputStream(pluginXmlBytes));
      pluginXml = JDOMXIncluder.resolve(pluginXml, pluginXmlUrl.toExternalForm());
    } catch (JDOMException e) {
      throw new BrokenPluginException("Invalid plugin.xml", e);
    } catch (XIncludeException e) {
      throw new BrokenPluginException("Failed to read plugin.xml", e);
    }

    if (!zipRootPool.isEmpty()) {
      if (pluginClassPool != zipRootPool) {
        throw new BrokenPluginException("Plugin contains .class files in the root, but has no META-INF/plugin.xml");
      }
    }

    return new IdeaPlugin(zipFile, pluginClassPool, ContainerClassPool.getUnion(zipFile.getPath(), libraryPool), pluginXml, allXmlInRoot);
  }

  private static void tryAddXmlInRoot(@NotNull Map<String, Document> container,
                                      @NotNull String entryName,
                                      @NotNull byte[] data,
                                      @NotNull URL xmlUrl,
                                      boolean isPluginXml) throws BrokenPluginException {
    try {
      Document document = JDOMUtil.loadDocument(new ByteArrayInputStream(data));
      document = JDOMXIncluder.resolve(document, xmlUrl.toExternalForm());
      container.put(entryName, document);
    } catch (Exception e) {
      if (isPluginXml) {
        //plugin.xml should be correct!
        throw new BrokenPluginException("Failed to read and parse META-INF/plugin.xml", e);
      }
      //for non-main .xml it's not critical
      System.out.println("Couldn't parse " + entryName);
    }
  }

  private static boolean isXmlInRoot(@NotNull String entryName) {
    return XML_IN_ROOT_PATTERN.matcher(entryName).matches();
  }

  private static boolean isPluginXmlInRoot(@NotNull String entryName) {
    if (entryName.equals(PLUGIN_XML_ENTRY_NAME)) {
      return true;
    }
    int slashIndex = entryName.indexOf('/'); //first slash
    int maybeSlashIndex = entryName.length() - PLUGIN_XML_ENTRY_NAME.length() - 1;
    return entryName.endsWith(PLUGIN_XML_ENTRY_NAME) && slashIndex == maybeSlashIndex;
  }

  @NotNull
  private static IdeaPlugin createFromJar(@NotNull File jarFile) throws IOException, BrokenPluginException {
    return createFromJars(jarFile, Collections.singletonList(new JarFile(jarFile)));
  }

  @NotNull
  public static IdeaPlugin createFromDirectory(@NotNull File directoryPath) throws BrokenPluginException, IOException {
    return createFromJars(directoryPath, getPluginJars(directoryPath));
  }

  @NotNull
  private static List<JarFile> getPluginJars(File pluginDirectory) throws IOException, BrokenPluginException {
    final File lib = new File(pluginDirectory, "lib");
    if (!lib.isDirectory()) {
      throw new BrokenPluginException("Plugin lib is not found: " + lib);
    }

    final List<JarFile> jars = Util.getJars(lib, Predicates.<File>alwaysTrue());
    if (jars.size() == 0) {
      throw new BrokenPluginException("No jar files found under " + lib);
    }

    return jars;
  }

  private static IdeaPlugin createFromJars(@NotNull File pluginFile,
                                           @NotNull List<JarFile> jarFiles) throws BrokenPluginException, IOException {
    Document pluginXml = null;
    ClassPool pluginClassPool = null;
    List<ClassPool> libraryPools = new ArrayList<ClassPool>();

    try {
      for (JarFile jar : jarFiles) {
        ZipEntry pluginXmlEntry = jar.getEntry(PLUGIN_XML_ENTRY_NAME);
        if (pluginXmlEntry != null) {
          if (pluginClassPool != null) {
            throw new BrokenPluginException("Plugin has more than one .jar with plugin.xml: " + pluginFile);
          }

          pluginClassPool = new JarClassPool(jar);
          URL jarURL = new URL(
              "jar:" + StringUtil.replace(new File(jar.getName()).toURI().toASCIIString(), "!", "%21") + "!/META-INF/plugin.xml"
          );

          try {
            pluginXml = JDOMUtil.loadDocument(jarURL);
            pluginXml = JDOMXIncluder.resolve(pluginXml, jarURL.toExternalForm());
          } catch (JDOMException e) {
            throw new BrokenPluginException("Invalid plugin.xml", e);
          } catch (XIncludeException e) {
            throw new BrokenPluginException("Failed to read plugin.xml", e);
          }
        } else {
          libraryPools.add(new JarClassPool(jar));
        }
      }
    } catch (Exception exc) {
      throw new BrokenPluginException(exc);
    }

    if (pluginXml == null) {
      throw new BrokenPluginException("No plugin.xml found for plugin: " + pluginFile);
    }

    final Map<String, Document> xmlDocumentsInRoot = new HashMap<String, Document>();
    //this is a correct plugin (and only one META-INF/plugin.xml) found for it
    for (JarFile jarFile : jarFiles) {
      ZipEntry pluginXmlEntry = jarFile.getEntry(PLUGIN_XML_ENTRY_NAME);
      if (pluginXmlEntry != null) {
        //this is the very JAR for which META-INF/plugin.xml found so let's take the other .xml-s

        for (JarEntry jarEntry : Collections.list(jarFile.entries())) {
          String name = jarEntry.getName();
          if (name.startsWith(META_INF_ENTRY) && name.endsWith(".xml")) {
            try {
              URL url = new URL(
                  "jar:" + StringUtil.replace(new File(jarFile.getName()).toURI().toASCIIString(), "!", "%21") + "!/" + name
              );

              Document document = JDOMUtil.loadDocument(url);
              document = JDOMXIncluder.resolve(document, url.toExternalForm());

              xmlDocumentsInRoot.put(name, document);
            } catch (JDOMException e) {
              System.out.println("Cannot load .xml document " + name);
            } catch (IOException e) {
              System.out.println("Cannot load .xml document " + name);
            }
          }
        }
      }
    }

    ClassPool libraryPoolsUnion = ContainerClassPool.getUnion(pluginFile.toString(), libraryPools);
    return new IdeaPlugin(pluginFile, pluginClassPool, libraryPoolsUnion, pluginXml, xmlDocumentsInRoot);
  }

  @NotNull
  public Map<String, Document> getXmlDocumentsInRoot() {
    return myXmlDocumentsInRoot;
  }

  @NotNull
  public List<PluginDependency> getDependencies() {
    return myDependencies;
  }

  @NotNull
  public List<PluginDependency> getModuleDependencies() {
    return myModuleDependencies;
  }

  @NotNull
  public Document getPluginXml() {
    return myPluginXml;
  }

  @Nullable
  public ProductUpdateBuild getSinceBuild() {
    return mySinceBuild;
  }

  @Nullable
  public ProductUpdateBuild getUntilBuild() {
    return myUntilBuild;
  }

  private void loadPluginDependencies(@NotNull Document pluginXml) {
    final List dependsElements = pluginXml.getRootElement().getChildren("depends");

    for (Object dependsObj : dependsElements) {
      Element dependsElement = (Element) dependsObj;

      final boolean optional = Boolean.parseBoolean(dependsElement.getAttributeValue("optional", "false"));
      final String pluginId = dependsElement.getTextTrim();

      PluginDependency dependency = new PluginDependency(pluginId, optional);
      if (pluginId.startsWith("com.intellij.modules.")) {
        myModuleDependencies.add(dependency);
      } else {
        myDependencies.add(dependency);
      }
    }
  }

  public boolean isCompatibleWithIde(@NotNull String ideVersion) {
    ProductUpdateBuild ide = new ProductUpdateBuild(ideVersion);
    if (!ide.isOk()) {
      return false;
    }

    //noinspection SimplifiableIfStatement
    if (mySinceBuild == null) return true;

    return ProductUpdateBuild.VERSION_COMPARATOR.compare(mySinceBuild, ide) <= 0
        && (myUntilBuild == null || ProductUpdateBuild.VERSION_COMPARATOR.compare(ide, myUntilBuild) <= 0);
  }

  @NotNull
  private Set<String> loadModules(@NotNull Document pluginXml) {
    LinkedHashSet<String> modules = new LinkedHashSet<String>();
    @SuppressWarnings("unchecked")
    Iterable<? extends Element> children = (Iterable<? extends Element>) pluginXml.getRootElement().getChildren("module");
    for (Element module : children) {
      modules.add(module.getAttributeValue("value"));
    }
    return modules;
  }

  @NotNull
  public String getPluginName() {
    return myPluginName;
  }

  @NotNull
  public String getPluginVersion() {
    return myPluginVersion;
  }

  @NotNull
  public String getPluginId() {
    return myPluginId;
  }

  @NotNull
  public String getPluginVendor() {
    return myPluginVendor;
  }

  @NotNull
  public ClassPool getLibraryClassPool() {
    return myLibraryClassPool;
  }

  @NotNull
  public Set<String> getDefinedModules() {
    return Collections.unmodifiableSet(myDefinedModules);
  }

  private void getIdeaVersion(@NotNull Document pluginXml) throws BrokenPluginException {
    Element ideaVersion = pluginXml.getRootElement().getChild("idea-version");
    if (ideaVersion != null && ideaVersion.getAttributeValue("min") == null) { // min != null in legacy plugins.
      String sinceBuildString = ideaVersion.getAttributeValue("since-build");
      mySinceBuild = new ProductUpdateBuild(sinceBuildString);
      if (!mySinceBuild.isOk()) {
        throw new BrokenPluginException("<idea version since-build = /> attribute has incorrect value: " + sinceBuildString);
      }

      String untilBuildString = ideaVersion.getAttributeValue("until-build");
      if (!Strings.isNullOrEmpty(untilBuildString)) {
        if (untilBuildString.endsWith(".*") || untilBuildString.endsWith(".999") || untilBuildString.endsWith(".9999") || untilBuildString.endsWith(".99999")) {
          int idx = untilBuildString.lastIndexOf('.');
          untilBuildString = untilBuildString.substring(0, idx + 1) + Integer.MAX_VALUE;
        }

        myUntilBuild = new ProductUpdateBuild(untilBuildString);
        if (!myUntilBuild.isOk()) {
          throw new BrokenPluginException("<idea-version until-build= /> attribute has incorrect value: " + untilBuildString);
        }
      }
    }
  }

  @NotNull
  public ClassPool getPluginClassPool() {
    return myPluginClassPool;
  }

  @NotNull
  public ClassPool getCommonClassPool() {
    return myAllClassesClassPool;
  }
}
