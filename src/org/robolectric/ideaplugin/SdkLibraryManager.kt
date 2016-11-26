package org.robolectric.ideaplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.*
import java.io.File
import java.util.*

class SdkLibraryManager(private val project: Project) {
  private val application = ApplicationManager.getApplication()

  private val SDKs = Sdks()
  private val androidAllSdkLibraries : MutableMap<AndroidSdk, Library> = HashMap()

  fun findSdkLibraries(): LibraryTable? {
    val libraryTable = ProjectLibraryTable.getInstance(project)
    libraryTable.libraries.forEach {
      val name = it.name ?: ""
      if (name.startsWith("org.robolectric:android-all:", false)) {
        val version = name.split(":").last()
        val thisSdk = SDKs.sdksByVersion[version]!!
        androidAllSdkLibraries[thisSdk] = it
        println("Found $name")
      }
    }
    return libraryTable
  }

  fun download(): Unit {

  }

  fun addMissingLibraries() {
    val libraryTable = ProjectLibraryTable.getInstance(project)
    application.runWriteAction {
      val toAdd = HashMap<AndroidSdk, Library>()
      val missingSdks = SDKs.sdksByApiLevel.values - androidAllSdkLibraries.keys
      if (missingSdks.any()) {
        val libTableTx = libraryTable.modifiableModel
        missingSdks.forEach { missingSdk ->
          if (missingSdk.exists()) {
            val lib: LibraryEx = createLibrary(missingSdk, libTableTx)
            toAdd[missingSdk] = lib

            //            val module = ModuleManager.getInstance(project).modules.first()
            //            addLibraryToModule(lib, module)
          } else {
            LOGGER.warn("Couldn't find sources for Android SDK $missingSdk at ${missingSdk.sourceJarFile.path}.")
          }
        }

        libTableTx.commit()

        androidAllSdkLibraries.putAll(toAdd)
      }
    }
  }

  private fun createLibrary(androidSdk: AndroidSdk, libTableTx: LibraryTable.ModifiableModel): LibraryEx {
    val jarFile = androidSdk.jarFile
    val srcJarFile = androidSdk.sourceJarFile
    val libraryType = libType()
    val lib: LibraryEx = libTableTx.createLibrary(androidSdk.coordinates, libraryType) as LibraryEx
    val libTx = lib.modifiableModel
    val properties = libraryType?.createDefaultProperties()
    properties?.javaClass?.getMethod("setMavenId", String::class.java)?.invoke(properties, "org.robolectric:android-all:$androidSdk")
    libTx.properties = properties
    libTx.addRoot("jar://${jarFile.path}!/", OrderRootType.CLASSES)
    libTx.addRoot("jar://${srcJarFile.path}!/", OrderRootType.SOURCES)
    LOGGER.info("Adding Android SDK library: ${lib.name}")
    libTx.commit()
    return lib
  }

  private fun libType() : PersistentLibraryKind<out LibraryProperties<*>>? {
    return LibraryKind.findById("repository") as PersistentLibraryKind<out LibraryProperties<*>>
  }

  fun getLibrary(androidSdk: AndroidSdk): Library? {
    return androidAllSdkLibraries[androidSdk]
  }

  fun getSdk(sdkLevel: Int): AndroidSdk? = SDKs.sdksByApiLevel[sdkLevel]

  fun addLibraryToModule(library: Library, module: Module): Boolean {
    println("Adding ${library.name} to module ${module.name}")
    val moduleRootManager = ModuleRootManager.getInstance(module)
    val model = moduleRootManager.modifiableModel

    val containsLibraryAlready = moduleRootManager.orderEntries.any {
      it is LibraryOrderEntry && it.library == library
    }

    if (!containsLibraryAlready) {
      model.addLibraryEntry(library).scope = DependencyScope.PROVIDED
      model.commit()
    }

    return !containsLibraryAlready
  }

  fun removeLibraryFromModule(library: Library, module: Module) {
    println("Removing ${library.name} from module ${module.name}")
    val moduleRootManager = ModuleRootManager.getInstance(module)
    val model = moduleRootManager.modifiableModel
    for (orderEntry in model.orderEntries) {
      when (orderEntry) {
        is LibraryOrderEntry -> if (orderEntry.library == library) {
          model.removeOrderEntry(orderEntry)
        }
      }
    }
    model.commit()
  }
}


private val m2RepoDir = "${System.getProperty("user.home")}/.m2/repository"

data class AndroidSdk(val apiLevel: Int, val sdkVersion: String, val build: String) {
  val groupId = "org.robolectric"
  val artifactId = "android-all"
  val version = "$sdkVersion-robolectric-$build"
  val coordinates = "$groupId:$artifactId:$version"

  private val m2partial = "${groupId.replace('.', '/')}/$artifactId/$version/$artifactId-$version"

  val jarFile: File
    get() = File("$m2RepoDir/$m2partial.jar")
  val sourceJarFile: File
    get() = File("$m2RepoDir/$m2partial-sources.jar")

  fun exists() = jarFile.exists() && sourceJarFile.exists()
}

class Sdks {
  val sdksByApiLevel = HashMap<Int, AndroidSdk>()
  val sdksByVersion = HashMap<String, AndroidSdk>()

  val sdkVersions = listOf(
      AndroidSdk(16, "4.1.2_r1", "0"),
      AndroidSdk(17, "4.2.2_r1.2", "0"),
      AndroidSdk(18, "4.3_r2", "0"),
      AndroidSdk(19, "4.4_r1", "1"),
      AndroidSdk(21, "5.0.0_r2", "1"),
      AndroidSdk(22, "5.1.1_r9", "1"),
      AndroidSdk(23, "6.0.0_r1", "0"),
      AndroidSdk(24, "7.0.0_r1", "0")
  )

  init {
    sdkVersions.forEach {
      sdksByApiLevel[it.apiLevel] = it
      sdksByVersion[it.version] = it
    }
  }
}
