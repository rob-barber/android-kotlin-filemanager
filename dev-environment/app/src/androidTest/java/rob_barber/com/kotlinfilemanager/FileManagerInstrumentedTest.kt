package rob_barber.com.kotlinfilemanager

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import java.io.File

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class FileManagerInstrumentedTest {

    private val testFileName = "testJsonFile.json"
    private val testJson = "{\n" +
            "\"id\": \"c5d764c9-0758-432f-8f9d-7a38c70e557f\",\n" +
            "\"date_created\": \"2017-10-13T19:48:17Z\",\n" +
            "\"last_updated\": \"2018-02-07T21:06:34.079286Z\",\n" +
            "\"is_active\": true,\n" +
            "\"is_deleted\": false\n" +
            "}"

    @After
    fun deleteTestFile() {
        val appContext = InstrumentationRegistry.getTargetContext()
        val fileManager = FileManager.getInstance(appContext)

        val filePath = fileManager.getDocumentsFolderPath()?.path + "/$testFileName"

        val testFile = File(filePath)

        if (testFile.exists()) {
            testFile.delete()
        }
    }

    @Test
    fun testFileSystem_CanReadAndWrite() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getTargetContext()
        assertEquals("rob_barber.com.kotlinfilemanager", appContext.packageName)

        val fileManager = FileManager.getInstance(appContext)
        assertTrue(fileManager.isExternalStorageReadable())
        assertTrue(fileManager.isExternalStorageReadable())
    }

    @Test
    fun testSaveJsonAsync_SavesCorrectly() {

        val appContext = InstrumentationRegistry.getTargetContext()
        val fileManager = FileManager.getInstance(appContext)

        val jsonObject = JSONTokener(testJson).nextValue() as? JSONObject

        assertNotNull("Could not parse JSON Object", jsonObject)

        val filePath = fileManager.getDocumentsFolderPath()?.path + "/$testFileName"
        assertNotNull(filePath)

        val saveFile = File(filePath)

        fileManager.saveJSON(jsonObject!!, saveFile,
                callback = {
                    assertTrue("Error Saving file", saveFile.exists())
                },
                error = {
                    fail(it)
                }
        )
    }

    @Test
    fun testSaveJsonSync_SavesCorrectly() {

        val appContext = InstrumentationRegistry.getTargetContext()
        val fileManager = FileManager.getInstance(appContext)

        val jsonObject = JSONTokener(testJson).nextValue() as? JSONObject

        assertNotNull("Could not parse JSON Object", jsonObject)

        val filePath = fileManager.getDocumentsFolderPath()?.path + "/testJson.json"
        assertNotNull(filePath)

        val saveFile = File(filePath)

        fileManager.saveJSON(jsonObject!!, saveFile, async = false,
                callback = {
                    assertTrue("Error Saving file", saveFile.exists())
                },
                error = {
                    assertTrue(it, false)
                }
        )

    }

}
