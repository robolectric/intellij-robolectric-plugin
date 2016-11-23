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
import java.util.*

class SdkLibraryManager(private val project: Project) {
  private val application = ApplicationManager.getApplication()

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