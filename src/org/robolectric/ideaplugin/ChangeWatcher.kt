package org.robolectric.ideaplugin

import com.intellij.psi.*

class ChangeWatcher : PsiTreeChangeAdapter() {
  override fun childAdded(event: PsiTreeChangeEvent) {
    if (event.child is PsiClass) {
      println("added: ${(event.child as PsiClass).fullName()} (childAdded)")
    }
  }

  override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
    if (event.child is PsiClass) {
      println("removed: ${(event.child as PsiClass).fullName()} (beforeChildRemoval)")
    }
  }

  override fun childRemoved(event: PsiTreeChangeEvent) {
    println("removed: ${event.element}")
  }

  override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
    if (event.oldChild is PsiClass) {
      println("removed: ${(event.oldChild as PsiClass).fullName()} (childReplaced)")
    }
  }

  override fun childReplaced(event: PsiTreeChangeEvent) {
    if (event.parent is PsiClass && event.oldChild is PsiIdentifier) {
      println("removed: ${event.parent.contextName() + event.oldChild.text} (childReplaced identifier)")
      println("added: ${event.parent.contextName() + event.newChild.text} (childReplaced identifier)")
    } else {
      if (event.newChild is PsiClass) {
        println("added: ${(event.newChild as PsiClass).fullName()} (childReplaced)")
      }
    }
  }

  fun PsiClass.fullName() = this.contextName() + name

  fun PsiElement.contextName(): String {
    var name = ""
    var cur: PsiElement? = parent
    while (cur != null) {
      when (cur) {
        is PsiClass -> {
          name = cur.name + "$" + name
          cur = cur.parent
        }
        is PsiJavaFile -> {
          name = cur.packageName + "." + name
          cur = null
        }
      }
    }
    return name
  }
}