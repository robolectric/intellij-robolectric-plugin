package org.robolectric.ideaplugin

import java.io.File

data class AndroidSdk(val apiLevel: Int, val sdkVersion: String, val build: String) {
  val groupId = "org.robolectric"
  val artifactId = "android-all"
  val version = "$sdkVersion-robolectric-$build"
  val coordinates = "$groupId:$artifactId:$version"

  private val m2RepoDir = "${System.getProperty("user.home")}/.m2/repository"
  private val m2partial = "${groupId.replace('.', '/')}/$artifactId/$version/$artifactId-$version"

  val jarFile: File
    get() = File("$m2RepoDir/$m2partial.jar")
  val sourceJarFile: File
    get() = File("$m2RepoDir/$m2partial-sources.jar")

  fun exists() = jarFile.exists() && sourceJarFile.exists()
}