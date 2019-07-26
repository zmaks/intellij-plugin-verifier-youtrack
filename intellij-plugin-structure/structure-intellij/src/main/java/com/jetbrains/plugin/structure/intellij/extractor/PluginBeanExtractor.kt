package com.jetbrains.plugin.structure.intellij.extractor

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.jetbrains.plugin.structure.intellij.beans.PluginBean
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.transform.JDOMSource
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException

object PluginBeanExtractor {

  private val jaxbContext by lazy {
    /**
     * Explicitly specify class loader for loading implementation classes.
     *
     * It is necessary for applications that have complex hierarchy of class loaders.
     * If we don't specify the class loader, a thread's context class loader would be used,
     * which may not found necessary classes in some scenarios.
     *
     * JAXB has only this option to pass class loader. Resource file 'jaxb.index' is used
     * to specify top-level classes.
     */
    JAXBContext.newInstance("com.jetbrains.plugin.structure.intellij.beans", PluginBeanExtractor.javaClass.classLoader)
  }

  @Throws(JAXBException::class)
  fun extractPluginBean(document: Document): PluginBean {
    val unmarshaller = jaxbContext.createUnmarshaller()

    val rootElement = document.rootElement
    val bean = unmarshaller.unmarshal(JDOMSource(document)) as PluginBean
    bean.extensions = extractExtensions(rootElement)
    bean.applicationListeners = extractApplicationListeners(rootElement)
    return bean
  }

  private fun extractApplicationListeners(rootElement: Element): List<Element> {
    val applicationListeners = arrayListOf<Element>()
    for (applicationListenersRoot in rootElement.getChildren("applicationListeners")) {
      applicationListeners += applicationListenersRoot.children
    }
    return applicationListeners
  }

  private fun extractExtensions(rootElement: Element): Multimap<String, Element> {
    val extensions = ArrayListMultimap.create<String, Element>()
    for (extensionsRoot in rootElement.getChildren("extensions")) {
      for (element in extensionsRoot.children) {
        extensions.put(extractEPName(element), element)
      }
    }
    return extensions
  }

  private fun extractEPName(extensionElement: Element): String {
    val point = extensionElement.getAttributeValue("point")
    if (point != null) {
      return point
    }

    val parentNs = extensionElement.parentElement?.getAttributeValue("defaultExtensionNs")
    return if (parentNs != null) {
      parentNs + '.' + extensionElement.name
    } else {
      extensionElement.namespace.uri + '.' + extensionElement.name
    }
  }

}
