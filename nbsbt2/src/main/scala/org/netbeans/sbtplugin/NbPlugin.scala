package org.netbeans.sbtplugin

import sbt._, Keys._

object NbPlugin extends Plugin {

  val genNetbeansClasspathFile = taskKey[Unit]("Write the .classpath_nb file that NetBeans picks up")

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
    val libsEntries = externalDependencyClasspath.in(Compile).value.map {f =>
      libEntry(f, "compile")
    } ++ unmanagedClasspath.in(Compile).value.map {f =>
      libEntry(f, "compile")
    } ++ unmanagedClasspath.in(Test).value.map {f =>
      libEntry(f, "test")
    } ++ externalDependencyClasspath.in(Test).value.map {f =>
      libEntry(f, "test")
    }
    
    val compileClassesDir = classDirectory.in(Compile).value
    val testClassesDir = classDirectory.in(Test).value
    val baseDir = extracted.currentProject.base.toPath
    def rel(f: File) = baseDir.relativize(f.toPath).toFile
    val projEntries = Seq(
      s"""<classpathentry scope="compile" output="${rel(compileClassesDir)}" path="${rel(scalaSource.in(Compile).value)}" kind="src" managed="false" />""",
      s"""<classpathentry scope="compile" output="${rel(compileClassesDir)}" path="${rel(javaSource.in(Compile).value)}" kind="src" managed="false" />""") ++
      managedSourceDirectories.in(Compile).value.map {f =>
        s"""<classpathentry scope="compile" output="${rel(compileClassesDir)}" path="${rel(f)}" kind="src" managed="true" />"""
      } ++ Seq(
      s"""<classpathentry scope="test" output="${rel(testClassesDir)}" path="${rel(scalaSource.in(Test).value)}" kind="src" managed="false" />""",
      s"""<classpathentry scope="test" output="${rel(testClassesDir)}" path="${rel(javaSource.in(Test).value)}" kind="src" managed="false" />""") ++
      managedSourceDirectories.in(Test).value.map {f =>
        s"""<classpathentry scope="test" output="${rel(testClassesDir)}" path="${rel(f)}" kind="src" managed="true" />"""
      }

    val sb = new StringBuilder()
    val id = extracted.currentProject.id
    sb append s"""<classpath name="$id" id="$id">\n"""
    projEntries ++ depProjsEntries ++ libsEntries foreach {e => 
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

  val nbsettings = Seq(genNetbeansClasspathFileSetting)
}
