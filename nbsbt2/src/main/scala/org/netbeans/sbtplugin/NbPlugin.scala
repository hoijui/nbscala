package org.netbeans.sbtplugin

import sbt._, Keys._

object NbPlugin extends AutoPlugin {
  object autoImport {
    val genNetbeansClasspathFile = taskKey[Unit]("Write the .classpath_nb file that NetBeans picks up")
  }
  import autoImport._

  private val genNetbeansClasspathFileSetting = genNetbeansClasspathFile := {
    val log = streams.value.log
    val global = Project.extract(state.value)
    import global.showKey
    val extracted = new Extracted(buildStructure.value, Project.session(state.value), thisProjectRef.value)
    log.info("Generating classpath file for " + extracted.currentProject.id)
    val depProjsEntries = (for {
        classpathDep <- extracted.currentProject.dependencies
        project = classpathDep.project
      } yield {
        val projExtracted = new Extracted(extracted.structure, extracted.session, project)
        val projName = projExtracted.currentProject.id
        val projBaseDir = projExtracted.currentProject.base
        log.info(s"Detected dependency project $projName at $projBaseDir")
        val compileDepStr = if (classpathDep.configuration.map(_.contains("compile->compile")).getOrElse(true)) {
          val classesDir = projExtracted.get(classDirectory in Compile)
          Seq(s"""<classpathentry scope="compile" base="$projBaseDir" output="$classesDir" exported="true" kind="src" combineaccessrules="false" />""")
        } else Seq.empty[String]
        val testDepStr = if (classpathDep.configuration.map(_.contains("test->test")).getOrElse(false)) {
          val classesDir = projExtracted.get(classDirectory in Test)
          Seq(s"""<classpathentry scope="test" base="$projBaseDir" output="$classesDir" exported="true" kind="src" combineaccessrules="false" />""")
        } else Seq.empty[String]
        compileDepStr ++ testDepStr
      }).flatten
  
    val toIgnoreConfigurations = Set(Configurations.Runtime, Configurations.Provided, Configurations.Optional)
    val possibleConfigurations = extracted.currentProject.configurations.filterNot(toIgnoreConfigurations).filter{c => 
      val key = externalDependencyClasspath.in(extracted.currentRef, c)
      extracted.structure.data.get(key.scopedKey.scope, key.key).isDefined
    }
    
    val libsEntries = possibleConfigurations.map(conf =>
      extracted.runTask(externalDependencyClasspath.in(extracted.currentRef, conf), state.value)._2.map(libEntry(_, conf.toString)) ++
      extracted.runTask(unmanagedClasspath.in(extracted.currentRef, conf), state.value)._2.map(libEntry(_, conf.toString))).flatten
    
    
    val baseDir = extracted.currentProject.base.toPath
    def rel(f: File) = baseDir.relativize(f.toPath).toFile
    
    val projEntries = possibleConfigurations.map { conf =>
      val classesDir = extracted.get(classDirectory.in(extracted.currentRef, conf))
      val scalaSourcesPath = extracted.get(scalaSource.in(extracted.currentRef, conf))
      val javaSourcesPath = extracted.get(javaSource.in(extracted.currentRef, conf))
      val managedSourceDirs = extracted.get(managedSourceDirectories.in(extracted.currentRef, conf))
      val resourceDir = extracted.get(resourceDirectory.in(extracted.currentRef, conf))
      val resourceManagedDir = extracted.get(resourceManaged.in(extracted.currentRef, conf))
      Seq(
        s"""<classpathentry scope="$conf" output="${rel(classesDir)}" path="${rel(scalaSourcesPath)}" kind="src" managed="false" />""",
        s"""<classpathentry scope="$conf" output="${rel(classesDir)}" path="${rel(javaSourcesPath)}" kind="src" managed="false" />""",
        s"""<classpathentry scope="$conf" output="${rel(classesDir)}" path="${rel(resourceDir)}" kind="src" managed="false" />"""
      ) ++
      managedSourceDirs.map(f => s"""<classpathentry scope="$conf" output="${rel(classesDir)}" path="${rel(f)}" kind="src" managed="true" />""") :+
      s"""<classpathentry scope="$conf" output="${rel(classesDir)}" path="${rel(resourceManagedDir)}" kind="src" managed="true" />"""
    }.flatten

    val scalariformEntries = possibleConfigurations.flatMap { conf =>
      ScalariformSettings.detect(extracted, conf)
    }
    
    val sb = new StringBuilder()
    val id = extracted.currentProject.id
    sb append s"""<classpath name="$id" id="$id">\n"""
    projEntries ++ depProjsEntries ++ libsEntries ++ scalariformEntries foreach {e => 
      sb.append("  ").append(e).append("\n")
    }
    sb.append("""  <classpathentry path="org.eclipse.jdt.launching.JRE_CONTAINER" kind="con"></classpathentry>""").append("\n")
    sb.append("""  <classpathentry path="bin" kind="output"></classpathentry>""").append("\n")
    sb.append("</classpath>")

    java.nio.file.Files.write(extracted.currentProject.base.toPath.resolve(".classpath_nb"), sb.toString.getBytes("utf-8"))
    log.info("File successfully generated")
  }

  def libEntry(f: Attributed[File], scope: String): String = {
    val file = f.data
    s"""<classpathentry scope="$scope" path="$file" kind="lib" />"""
  }

  override def trigger = allRequirements
  override val projectSettings = Seq(genNetbeansClasspathFileSetting)
}
