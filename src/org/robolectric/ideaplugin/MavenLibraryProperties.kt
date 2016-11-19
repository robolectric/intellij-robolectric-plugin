package org.robolectric.ideaplugin

import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.util.Comparing
import com.intellij.util.xmlb.annotations.Attribute

class MavenLibraryProperties(groupId: String?, artifactId: String?, version: String?) : LibraryProperties<MavenLibraryProperties>() {
  private var mavenId: String = "$groupId:$artifactId:$version"
  var groupId: String? = groupId
    private set
  var artifactId: String? = artifactId
    private set
  var version: String? = version
    private set

  @Attribute("maven-id")
  fun getMavenId(): String {
    return mavenId
  }

  fun setMavenId(mavenId: String) {
    this.mavenId = mavenId
    val parts = mavenId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    groupId = parts[0]
    artifactId = parts[1]
    version = parts[2]
  }

  override fun getState(): MavenLibraryProperties? {
    return this
  }

  override fun loadState(state: MavenLibraryProperties) {
    setMavenId(state.mavenId)
  }

  override fun equals(obj: Any?): Boolean {
    if (obj !is MavenLibraryProperties) {
      return false
    }
    return Comparing.equal(mavenId, obj.mavenId)

  }

  override fun hashCode(): Int {
    return Comparing.hashcode(getMavenId())
  }
}
