package org.netbeans.modules.scala.sbt.classpath

import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import java.net.URI
import org.netbeans.api.java.classpath.ClassPath
import org.netbeans.api.java.platform.JavaPlatformManager
import org.netbeans.api.project.FileOwnerQuery
import org.netbeans.api.project.Project
import org.netbeans.spi.java.classpath.ClassPathImplementation
import org.netbeans.spi.java.classpath.PathResourceImplementation
import org.netbeans.spi.java.classpath.support.ClassPathSupport
import org.openide.filesystems.FileStateInvalidException
import org.openide.filesystems.FileUtil
import org.openide.util.Exceptions


/**
 *
 * @author Caoyuan Deng
 */
final class SBTClassPath(project: Project, scope: String) extends ClassPathImplementation with PropertyChangeListener {

  private val pcs = new PropertyChangeSupport(this)
  private val sbtResolver = project.getLookup.lookup(classOf[SBTResolver])
  sbtResolver.addPropertyChangeListener(this)

  def getResources: java.util.List[PathResourceImplementation] = {
    if (!sbtResolver.isEnabled) {
      java.util.Collections.emptyList()
    } else {
      val result = new java.util.ArrayList[PathResourceImplementation]()
      if (scope == ClassPath.BOOT) {
        result.addAll(getJavaBootResources)
      }

      for (file <- sbtResolver.getResolvedLibraries(scope)) {
        val fo = FileUtil.toFileObject(file)
        try {
          val rootUrl = if (fo != null && FileUtil.isArchiveFile(fo)) {
            FileOwnerQuery.markExternalOwner(fo, project, FileOwnerQuery.EXTERNAL_ALGORITHM_TRANSIENT)
            FileUtil.getArchiveRoot(fo).toURL
          } else {
            // file is a classes *folder* and may not exist, we must add a slash at the end.
            URI.create(file.toURI + "/").toURL
          }
          result.add(ClassPathSupport.createResource(rootUrl))
        } catch {
          case ex: FileStateInvalidException => Exceptions.printStackTrace(ex)
        }
      }

      result
    }
  }

  private def getJavaBootResources: java.util.List[PathResourceImplementation] = {
    val result = new java.util.ArrayList[PathResourceImplementation]()
    val platformManager = JavaPlatformManager.getDefault
    val javaPlatform = platformManager.getDefaultPlatform

    // XXX todo cache it ?
    if (javaPlatform != null) {
      val cp = javaPlatform.getBootstrapLibraries
      assert(cp != null, javaPlatform)
      val entries = cp.entries.iterator
      while (entries.hasNext) {
        val entry = entries.next
        result.add(ClassPathSupport.createResource(entry.getURL))
      }
    }
    result
  }

  def removePropertyChangeListener(listener: PropertyChangeListener) {
    pcs.removePropertyChangeListener(listener)
  }

  def addPropertyChangeListener(listener: PropertyChangeListener) {
    pcs.addPropertyChangeListener(listener)
  }

  def propertyChange(evt: PropertyChangeEvent) {
    evt.getPropertyName match {
      case SBTResolver.SBT_LIBRARY_RESOLVED => pcs.firePropertyChange(ClassPathImplementation.PROP_RESOURCES, null, null)
      case _ =>
    }
  }

}
