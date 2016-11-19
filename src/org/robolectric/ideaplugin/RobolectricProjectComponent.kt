package org.robolectric.ideaplugin

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import java.util.*
import kotlin.concurrent.thread

class RobolectricProjectComponent(val project: Project) : ProjectComponent {

  private val javaPsiFacade = JavaPsiFacade.getInstance(project)!!
  private val allScope = GlobalSearchScope.allScope(project)

  private val shadowedClasses: MutableMap<String, MutableSet<PsiClass>> = HashMap()
  private var initialized = false
  private var usedNs: Long = 0

  private var statusBarManager = StatusBarManager(project)

  override fun initComponent() {
    // TODO: insert component initialization logic here

    val thread = Thread {
      while (true) {
        Thread.sleep(1000)
        synchronized(this) {
          if (usedNs > 0) {
            println("usedMs = ${usedNs / 1000000.0}")
            usedNs = 0
          }
        }
      }
    }
    thread.isDaemon = true
    thread.start()
  }

  override fun disposeComponent() {
    // TODO: insert component disposal logic here
  }

  override fun getComponentName(): String {
    return "RobolectricProjectComponent"
  }

  override fun projectOpened() {
    statusBarManager.opened()

    // called when project is opened
    val psiManagerEx = PsiManagerEx.getInstanceEx(project)
    psiManagerEx.addPsiTreeChangeListener(ChangeWatcher())

    val debuggerManagerEx = DebuggerManagerEx.getInstanceEx(project)
    debuggerManagerEx.addDebuggerManagerListener(DebugListener(project))

    thread(name = RobolectricProjectComponent::findShadowedClasses.name) {
      ApplicationManager.getApplication().runReadAction {
        println("findShadowedClasses start...")
        findShadowedClasses()
      }
    }
  }

  override fun projectClosed() {
    // called when project is being closed

    statusBarManager.closed()
  }

  fun findShadowedClasses() {
    synchronized(this) {
      if (initialized) return

      val implementsAnnotationName = "org.robolectric.annotation.Implements"
//      time("findShadowedClasses with Searcher") {
//        val implementsPsiType = javaPsiFacade.findClass(implementsAnnotationName, allScope)
//        val parameters = AnnotatedElementsSearch.Parameters(implementsPsiType, allScope, PsiClass::class.java)
//        AnnotatedElementsSearcher().execute(parameters) { shadowClass ->
//          val implAnnotation = shadowClass.modifierList!!.findAnnotation(implementsAnnotationName)!!
//          val implementsAnnotation = ImplementsAnnotation(implAnnotation)
//          val frameworkClassName = implementsAnnotation.frameworkType?.resolve()?.qualifiedName
//          if (frameworkClassName != null) {
//            shadowedClasses.getOrPut(frameworkClassName) { HashSet() }.add(shadowClass as PsiClass)
//          }
//          true
//        }
//      }

      time("findShadowedClasses for ${project.baseDir} ${this}") {
        val implementsPsiType = javaPsiFacade.findClass(implementsAnnotationName, allScope)
        val query = implementsPsiType?.let { ReferencesSearch.search(it) }
        query?.forEach { psiReference: PsiReference? ->
          if (psiReference is PsiJavaCodeReferenceElement) {
            val parent = psiReference.parent
            if (parent is PsiAnnotation) {
              val implementsAnnotation = ImplementsAnnotation(parent)
              val frameworkClassName = implementsAnnotation.frameworkType?.resolve()?.qualifiedName
              val shadowClass = implementsAnnotation.shadowType
              if (shadowClass != null && frameworkClassName != null) {
                shadowedClasses.getOrPut(frameworkClassName) { HashSet() }.add(shadowClass)
              }
            }
          }
        }
      }

      println("Found ${shadowedClasses.size} shadowed classes.")

      initialized = true
    }
  }

  fun shadowsFor(className: String) = shadowedClasses[className] ?: emptySet<PsiClass>()

  fun <T> time(activity: String, runnable: () -> T): T {
    val startedAt = System.nanoTime()
    try {
      return runnable.invoke()
    } catch (e: Exception) {
      e.printStackTrace()
      throw e
    } finally {
      used(System.nanoTime() - startedAt, activity)
    }
  }

  fun used(elapsedNs: Long, activity: String) {
    synchronized(this) {
      usedNs += elapsedNs
    }

    if (elapsedNs > 1000000) {
      println("$activity took ${elapsedNs / 1000000.0}ms")
    }
  }
}