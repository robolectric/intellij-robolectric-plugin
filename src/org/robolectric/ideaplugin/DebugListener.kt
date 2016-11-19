package org.robolectric.ideaplugin

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerManagerListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.ModuleLibraryOrderEntryImpl
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.*
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.sun.jdi.ClassType
import com.sun.jdi.IntegerValue
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
import java.util.*
import javax.swing.SwingUtilities

class DebugListener(private val project: Project) : DebuggerManagerListener {
  private val application = ApplicationManager.getApplication()
  private val javaPsiFacade = JavaPsiFacade.getInstance(project)!!
  private val virtualFileManager = VirtualFileManager.getInstance()
  private val psiManager = PsiManager.getInstance(project)

  private var currentSdkLevel = 0

  private val androidAllSdkLibraries : MutableMap<AndroidSdk, Library> = HashMap()

  init {
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

  private fun addLibraryToModule(library: Library, module: Module): Boolean {
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

  private fun removeLibraryFromModule(library: Library, module: Module) {
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

  private fun libType() : PersistentLibraryKind<out LibraryProperties<*>>? {
    return LibraryKind.findById("repository") as PersistentLibraryKind<out LibraryProperties<*>>
  }

  override fun sessionRemoved(session: DebuggerSession?) {
    println("session = $session")
  }

  override fun sessionAttached(session: DebuggerSession?) {
    val process = session!!.process
    process.managerThread.invokeAndWait(object: DebuggerCommandImpl() {
      override fun action() {
        process.appendPositionManager(MyPositionManager(process))
      }
    })

    session.contextManager.addListener { debuggerContextImpl, event ->
      println("${event.name} on $session")
      if (event == DebuggerSession.Event.PAUSE && debuggerContextImpl.isEvaluationPossible) {

        println("pause!")
//        process.xdebugProcess.custom step manager thingy?




        process.managerThread.invokeAndWait(object: DebuggerCommandImpl() {
          override fun action() {
            val evaluationContext = debuggerContextImpl.createEvaluationContext()
            if (evaluationContext == null) {
              println("huh, no evaluationContext!")
            } else {
              val vm = process.virtualMachineProxy
              val classClasses = vm.classesByName("org.robolectric.RuntimeEnvironment")
              val runtimeEnvVmClass = classClasses.first() as ClassType
              val getApiLevelVmMethod = DebuggerUtils.findMethod(runtimeEnvVmClass, "getApiLevel", "()I")!!
              val value = process.invokeMethod(evaluationContext, runtimeEnvVmClass, getApiLevelVmMethod, emptyList<Any>())

              println("value = $value")
              println(DebuggerUtils.getValueAsString(evaluationContext, value))

              val sdkLevel = (value as IntegerValue).value()

              // here do a thing?
              notifyActiveSdkLevel(sdkLevel,
                  ModuleUtilCore.findModuleForPsiElement(debuggerContextImpl.contextElement))
            }


//            application.runReadAction {
//              val builder = EvaluatorBuilderImpl.getInstance()
//              val psiFileFactory = PsiFileFactory.getInstance(project)
//              val psiFile: PsiJavaFile = (psiFileFactory.createFileFromText(JavaLanguage.INSTANCE,
//                  "class A { String apiLevel() { return org.robolectric.RuntimeEnvironment.getApiLevel(); } }") as PsiJavaFile?)!!
//              val psiCodeBlock = psiFile.classes[0].methods[0].body
//              println(psiCodeBlock)
//              val evaluator = builder.build(psiCodeBlock, SourcePosition.createFromLine(psiFile, 1))
//              val value = evaluator.evaluate(evaluationContext)
//              println("value = $value")
//              println(DebuggerUtils.getValueAsString(evaluationContext, value))
//
//              val sdkLevel = (value as IntegerValue).value()
//
//              // here do a thing?
//              notifyActiveSdkLevel(sdkLevel)
//            }
          }
        })
      }
    }
  }

  private fun laterOnUiThread(function: () -> Unit) {
    SwingUtilities.invokeLater(function)
  }

  override fun sessionCreated(session: DebuggerSession?) {
    println("session = $session")
  }

  override fun sessionDetached(session: DebuggerSession?) {
    println("session = $session")
    notifyActiveSdkLevel(0, null)
    project.messageBus.syncPublisher(Notifier.Topics.DEBUG_TOPIC).sdkChanged(null)
  }

  var undoAddLibrary: Runnable? = null

  private fun notifyActiveSdkLevel(sdkLevel: Int, module: Module?) {
    if (sdkLevel != currentSdkLevel) {
      currentSdkLevel = sdkLevel

      if (undoAddLibrary != null) {
        application.runWriteAction {
          undoAddLibrary!!.run()
        }
        undoAddLibrary = null
      }

      val androidSdk = getSdk(sdkLevel)
      if (androidSdk != null) {
        val library = androidAllSdkLibraries[androidSdk]
        if (library != null) {
          application.invokeLater {
            application.runWriteAction {
              if (addLibraryToModule(library, module!!)) {
                undoAddLibrary = Runnable {
                  removeLibraryFromModule(library, module)
                }
              }
            }
          }
        }
      }

      laterOnUiThread {
        val publisher = project.messageBus.syncPublisher(Notifier.Topics.DEBUG_TOPIC)
        publisher.sdkChanged(if (sdkLevel == 0) null else sdkLevel)
      }
    }
  }

  inner class MyPositionManager(process: DebugProcessImpl) : PositionManagerImpl(process) {
    override fun locationsOfLine(type: ReferenceType, position: SourcePosition): MutableList<Location> {
      val locationsOfLine = super.locationsOfLine(type, position)
      println("locations of line $type $position -> $locationsOfLine")
      return locationsOfLine
    }

    override fun getAllClasses(classPosition: SourcePosition): MutableList<ReferenceType> {
      val allClasses = super.getAllClasses(classPosition)
      println("getAllClasses $classPosition -> $allClasses")
      return allClasses
    }

    override fun createPrepareRequest(requestor: ClassPrepareRequestor, position: SourcePosition): ClassPrepareRequest? {
      val prepareRequest = super.createPrepareRequest(requestor, position)
      println("create prepare request $requestor $position -> $prepareRequest")
      return prepareRequest
    }

    override fun getPsiFileByLocation(project: Project?, location: Location?): PsiFile? {
      val typeName = location?.declaringType()?.name() ?: ""
      if (currentSdkLevel != 0 && typeName.startsWith("android.")) {
        location!!

        val androidSdk = getSdk(currentSdkLevel)
        if (androidSdk != null) {
          val srcFilePath = "jar://${androidSdk.sourceJarFile.path}!/${typeName.replace('.', '/')}.java"
          val srcVFile = virtualFileManager.findFileByUrl(srcFilePath)
          if (srcVFile != null) {
            return psiManager.findFile(srcVFile)
          }
        }

//        val library = androidAllSdkLibraries[androidSdk]
//        if (library == null) {
//          LOGGER.warn("no Android source found for SDK $currentSdkLevel ($androidSdk)")
//        } else {//          androidSdk.sourceJar
//          val classPsi = javaPsiFacade.findClass(typeName, LibraryScope(project, library))
//          LOGGER.info("using library = $library for $location")
      }

      return super.getPsiFileByLocation(project, location)
    }

    override fun getSourcePosition(location: Location?): SourcePosition? {
//        DebuggerManagerThreadImpl.assertIsManagerThread()
//
//        val typeName = location?.declaringType()?.name() ?: ""
//        if (false && typeName.startsWith("android.")) {
//          location!!
//
//          val version = sdksByApiLevel[currentSdkLevel]
//          val library = androidAllSdkLibraries[version]
//          val classPsi = javaPsiFacade.findClass(typeName, LibraryScope(project, library))
//          println("using library = $library for $location")
//          val psiFile = classPsi!!.containingFile
//
//          var lineNumber: Int
//          try {
//            lineNumber = location.lineNumber() - 1
//          } catch (e: InternalError) {
//            lineNumber = -1
//          }
//
//          var sourcePosition: SourcePosition? = null
//          if (lineNumber > -1) {
//            sourcePosition = calcLineMappedSourcePosition(psiFile, lineNumber)
//          }
//
//          return SourcePosition.createFromElement(psiFile)
//        } else {
      val sourcePosition = super.getSourcePosition(location)
      println("get source position $location -> $sourcePosition")
      return sourcePosition
//        }
    }

    private fun calcLineMappedSourcePosition(psiFile: PsiFile, originalLine: Int): SourcePosition? {
      val line = DebuggerUtilsEx.bytecodeToSourceLine(psiFile, originalLine)
      if (line > -1) {
        return SourcePosition.createFromLine(psiFile, line - 1)
      }
      return null
    }
  }
}