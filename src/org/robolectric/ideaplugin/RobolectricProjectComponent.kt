package org.robolectric.ideaplugin;

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.psi.*
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.java.stubs.impl.PsiMethodStubImpl
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import java.util.*

class RobolectricProjectComponent(val project: Project) : ProjectComponent {

  private val javaPsiFacade = JavaPsiFacade.getInstance(project)!!
  private val allScope = GlobalSearchScope.allScope(project)

  private val shadowedClasses: MutableMap<String, MutableSet<PsiClass>> = HashMap()
  private var initialized = false
  private var usedNs: Long = 0

  override fun initComponent() {
    // TODO: insert component initialization logic here

    val thread = Thread() {
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
    // called when project is opened
    val psiManagerEx = PsiManagerEx.getInstanceEx(project)
    psiManagerEx.addPsiTreeChangeListener(object: PsiTreeChangeAdapter() {
      override fun childAdded(event: PsiTreeChangeEvent) {
        println("added: ${event.element}")
      }

      override fun childRemoved(event: PsiTreeChangeEvent) {
        println("removed: ${event.element}")
      }
    })
  }

  override fun projectClosed() {
    // called when project is being closed
  }

  fun findShadowedClasses() {
    synchronized(this) {
      if (initialized) return

      time("findShadowedClasses for ${project.baseDir} ${this}") {
        val implementsPsiType = javaPsiFacade.findClass("org.robolectric.annotation.Implements", allScope)
        val query = implementsPsiType?.let { ReferencesSearch.search(it) }
        query?.forEach { psiReference: PsiReference? ->
          if (psiReference is PsiJavaCodeReferenceElement) {
            val parent = psiReference.parent
            if (parent is PsiAnnotation) {
              val implementsAnnotation = RobolectricLineMarkerProvider.ImplementsAnnotation(parent)
              val frameworkClassName = implementsAnnotation.frameworkType?.resolve()?.qualifiedName
              val shadowClass = implementsAnnotation.shadowType
              if (shadowClass != null && frameworkClassName != null) {
                shadowedClasses.getOrPut(frameworkClassName) { HashSet() }.add(shadowClass)
              }
            }
          }
        }
      }

      println(shadowedClasses)

      initialized = true
    }
  }

  fun shadowsFor(className: String) = shadowedClasses[className] ?: emptySet<PsiClass>()

  fun <T> time(activity: String, runnable: () -> T): T {
    val startedAt = System.nanoTime()
    try {
      return runnable.invoke()
    } catch (e : Exception) {
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
      println("${activity} took ${elapsedNs / 1000000.0}ms")
    }
  }
}