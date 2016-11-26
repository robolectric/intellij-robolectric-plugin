package org.robolectric.ideaplugin

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.impl.DebuggerManagerAdapter
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.PackageDirectoryCache
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.messages.MessageBusConnection
import java.util.*

class SdkClassFinder(private val project: Project, manager: DebuggerManagerEx)
  : NonClasspathClassFinder(project) {

  private fun sdkLibraryManager() = RobolectricProjectComponent.getInstance(project).sdkLibraryManager
  private var myConnection: MessageBusConnection
  private var currentAndroidSdk: AndroidSdk? = null

  init {
    myConnection = myProject.messageBus.connect()
    myConnection.subscribe(Notifier.Topics.DEBUG_TOPIC, object : Notifier.DebugListener {
      override fun sdkChanged(androidSdk: AndroidSdk?) {
        currentAndroidSdk = androidSdk
        clearCache()
      }
    })

    manager.addDebuggerManagerListener(object : DebuggerManagerAdapter() {
      override fun sessionCreated(session: DebuggerSession?) = clearCache()
      override fun sessionRemoved(session: DebuggerSession?) = clearCache()
    })

  }
  override fun calcClassRoots(): MutableList<VirtualFile> {
    val androidSdk = currentAndroidSdk
    if (androidSdk != null) {
      val library = sdkLibraryManager().getLibrary(androidSdk)
      if (library != null) {
        val classJars = library.rootProvider.getFiles(OrderRootType.CLASSES)
        return classJars.toMutableList()
      }
    }

    return ArrayList()
  }

  override fun getPackageFilesFilter(psiPackage: PsiPackage, scope: GlobalSearchScope): Condition<PsiFile>? {
    return super.getPackageFilesFilter(psiPackage, scope)
  }

  override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<out PsiClass> {
    val findClasses = super.findClasses(qualifiedName, scope)
    if (currentAndroidSdk != null && qualifiedName.startsWith("android")) {
      println("findClasses $qualifiedName -> $findClasses")
    }
    return findClasses
  }

  override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
    val findClass = super.findClass(qualifiedName, scope)
    if (currentAndroidSdk != null && qualifiedName.startsWith("android")) {
      println("findClass $qualifiedName -> $findClass")
    }
    return findClass
  }

  override fun processPackageDirectories(psiPackage: PsiPackage, scope: GlobalSearchScope, consumer: Processor<PsiDirectory>, includeLibrarySources: Boolean): Boolean {
    return super.processPackageDirectories(psiPackage, scope, consumer, includeLibrarySources)
  }

  override fun processPackageDirectories(psiPackage: PsiPackage, scope: GlobalSearchScope, consumer: Processor<PsiDirectory>): Boolean {
    return super.processPackageDirectories(psiPackage, scope, consumer)
  }

  override fun getClassesFilter(scope: GlobalSearchScope): Condition<PsiClass>? {
    return super.getClassesFilter(scope)
  }

  override fun findPackage(qualifiedName: String): PsiPackage? {
    val findPackage = super.findPackage(qualifiedName)
    if (currentAndroidSdk != null && qualifiedName.startsWith("android")) {
      println("findPackage $qualifiedName -> $findPackage")
    }
    return findPackage
  }

  override fun getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<out PsiClass> {
    val classes = super.getClasses(psiPackage, scope)
    val name = psiPackage.name ?: ""
    if (currentAndroidSdk != null && name.startsWith("android")) {
      println("findPackage $name -> $classes")
    }
    return classes
  }

  override fun getClasses(className: String?, psiPackage: PsiPackage, scope: GlobalSearchScope): Array<out PsiClass> {
    return super.getClasses(className, psiPackage, scope)
  }

  override fun getClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): MutableSet<String> {
    return super.getClassNames(psiPackage, scope)
  }

  override fun getClassRoots(scope: GlobalSearchScope?): MutableList<VirtualFile> {
    return super.getClassRoots(scope)
  }

  override fun getClassRoots(): MutableList<VirtualFile> {
    return super.getClassRoots()
  }

  override fun getPackageFiles(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<out PsiFile> {
    return super.getPackageFiles(psiPackage, scope)
  }

  override fun getSubPackages(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<out PsiPackage> {
    return super.getSubPackages(psiPackage, scope)
  }
}
