package org.robolectric.ideaplugin

import com.intellij.psi.*

fun PsiClass.implementsAnnotation(): ImplementsAnnotation? {
  val annotation = modifierList?.findAnnotation("org.robolectric.annotation.Implements")
  return if (annotation == null) null else ImplementsAnnotation(annotation)
}

class ImplementsAnnotation(val annotation: PsiAnnotation) {
  val frameworkType: PsiClassType?
    get() {
      val valueAttr = annotation.findAttributeValue("value")
      if (valueAttr is PsiClassObjectAccessExpression) {
        val shadowedClassType = valueAttr.operand.type;
        if (shadowedClassType is PsiClassType) {
          return shadowedClassType
        }
      }
      // todo: check className and find via JavaPsiFacade.findClass()

      return null
    }

  val shadowType: PsiClass?
    get() = (annotation.owner as PsiModifierList).parent as PsiClass?
}

class ImplementationAnnotation(val annotation: PsiAnnotation) {
}

val sdkSources = hashMapOf(
    16 to "/usr/local/google/home/christianw/.m2/repository/org/robolectric/android-all/4.1.2_r1-robolectric-0/android-all-4.1.2_r1-robolectric-0-sources.jar",
    17 to "/usr/local/google/home/christianw/.m2/repository/org/robolectric/android-all/4.2.2_r1.2-robolectric-0/android-all-4.2.2_r1.2-robolectric-0-sources.jar",
    18 to "/usr/local/google/home/christianw/.m2/repository/org/robolectric/android-all/4.3_r2-robolectric-0/android-all-4.3_r2-robolectric-0-sources.jar",
    19 to "/usr/local/google/home/christianw/.m2/repository/org/robolectric/android-all/4.4_r1-robolectric-1/android-all-4.4_r1-robolectric-1-sources.jar",
    21 to "/usr/local/google/home/christianw/.m2/repository/org/robolectric/android-all/5.0.0_r2-robolectric-1/android-all-5.0.0_r2-robolectric-1-sources.jar",
    22 to "/usr/local/google/home/christianw/.m2/repository/org/robolectric/android-all/5.1.1_r9-robolectric-1/android-all-5.1.1_r9-robolectric-1-sources.jar",
    23 to "/usr/local/google/home/christianw/.m2/repository/org/robolectric/android-all/6.0.0_r1-robolectric-0/android-all-6.0.0_r1-robolectric-0-sources.jar",
    24 to "/usr/local/google/home/christianw/.m2/repository/org/robolectric/android-all/7.0.0_r1-robolectric-0/android-all-7.0.0_r1-robolectric-0-sources.jar"
)