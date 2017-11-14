package org.netbeans.sbtplugin

//import com.typesafe.sbt.SbtScalariform
import sbt._

object ScalariformSettings {
  def detect(extracted: Extracted, scope: Configuration): Seq[String] = try {
    Seq.empty
    // val preferences = extracted.get(SbtScalariform.ScalariformKeys.preferences.in(extracted.currentRef, scope))
    // preferences.preferencesMap.map { case (k, v) =>
    //     s"""<scalariform key="${k.key}" value="$v" scope="$scope"/>"""
    // }.toSeq
  } catch {
    case nce: NoClassDefFoundError => Seq.empty
    case e: Exception => 
      scala.Console.err.println(s"Failed to detect scalariform in $scope due to $e")
      Seq.empty
  }
}
