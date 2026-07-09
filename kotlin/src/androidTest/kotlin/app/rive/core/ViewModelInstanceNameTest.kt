package app.rive.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.rive.Artboard
import app.rive.RiveAndroidTest
import app.rive.ViewModelInstance
import app.rive.ViewModelInstanceSource
import app.rive.ViewModelSource
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.test.R
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ViewModelInstanceNameTest : RiveAndroidTest() {
    @Test
    fun getName_matchesClassicApiForAllCreationSources() {
        val bytes = context.resources.openRawResource(R.raw.data_bind_test_impl)
            .use { it.readBytes() }

        // The classic API resolves names synchronously; use it as the source of truth for the
        // names this fixture assigns.
        val classicFile = File(bytes)
        val classicVm = classicFile.getViewModelByName("Test All")
        val classicDefault = classicVm.createDefaultInstance()
        val expectedNamedName = classicVm.createInstanceFromName("Test Alternate").name
        val expectedDefaultName = classicDefault.name
        val expectedBlankName = classicVm.createBlankInstance().name
        val expectedNestedName = classicDefault.getInstanceProperty("Test Nested").name

        val classicListArtboard = classicFile.artboard("Test List")
        val classicListOwner =
            classicFile.defaultViewModelForArtboard(classicListArtboard).createDefaultInstance()
        val expectedListItemName = classicListOwner.getListProperty("Test List")[0].name
        classicFile.release()

        // Fixture sanity: these names are distinct, so a wrong lookup cannot pass by accident.
        assertEquals("Test Alternate", expectedNamedName)
        assertEquals("Test Default", expectedDefaultName)
        assertEquals("", expectedBlankName)

        riveWorker.withPolling {
            val fileHandle = runBlocking { loadFile(bytes) }
            val vmSource = ViewModelSource.Named("Test All")

            val named = createViewModelInstance(fileHandle, vmSource.namedInstance("Test Alternate"))
            val default = createViewModelInstance(fileHandle, vmSource.defaultInstance())
            val blank = createViewModelInstance(fileHandle, vmSource.blankInstance())

            // Reference and list-item sources take the public wrapper type.
            val defaultInstance = ViewModelInstance(default, riveWorker, fileHandle)
            val nested = createViewModelInstance(
                fileHandle,
                ViewModelInstanceSource.Reference(defaultInstance, "Test Nested")
            )

            val listArtboard = Artboard(
                createArtboardByName(fileHandle, "Test List"),
                riveWorker,
                fileHandle,
                "Test List"
            )
            val listOwnerInstance = ViewModelInstance(
                createViewModelInstance(
                    fileHandle,
                    ViewModelSource.DefaultForArtboard(listArtboard).defaultInstance()
                ),
                riveWorker,
                fileHandle
            )
            val listItem = createViewModelInstance(
                fileHandle,
                ViewModelInstanceSource.ReferenceListItem(listOwnerInstance, "Test List", 0)
            )

            runBlocking {
                assertEquals(expectedNamedName, getViewModelInstanceName(named))
                assertEquals(expectedDefaultName, getViewModelInstanceName(default))
                assertEquals(expectedBlankName, getViewModelInstanceName(blank))
                assertEquals(expectedNestedName, getViewModelInstanceName(nested))
                assertEquals(expectedListItemName, getViewModelInstanceName(listItem))
            }

            listOf(named, blank, nested, listItem).forEach { deleteViewModelInstance(it) }
            listOwnerInstance.close()
            defaultInstance.close()
            listArtboard.close()
            deleteFile(fileHandle)
        }
    }
}
