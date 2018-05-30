package rob_barber.com.kotlinfilemanager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.*

typealias AsyncLambda = ()->Unit
typealias AsyncErrorLambda = (message:String) -> Unit

/**
 * Created by robertbarber on 2/13/18.
 */
class FileManager private constructor(context:Context) {

    //TODO: Condense function down with a function that handles the threading

    companion object {
        private const val TAG = "FileManager"

        @Volatile private var instance:FileManager? = null

        fun getInstance(context:Context):FileManager {
            if (instance == null) {
                synchronized(this) {
                    instance = FileManager(context.applicationContext)
                }
            }

            return instance!!
        }

    }

    private val appContext = context.applicationContext

    fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    fun isExternalStorageReadable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state || Environment.MEDIA_MOUNTED_READ_ONLY == state
    }

    /**
     * Takes a file path and returns the string of characters after the last '/' character. This essentially
     * gets the filename/directory from a path.
     * @return The filename/directory name from the path given.
     * */
    fun parseFilenameFromPath(filePath: String): String {
        val index = filePath.lastIndexOf('/') + 1
        return filePath.substring(index)
    }

    //region Private File Path Methods
    /**
     * Used to get the path to the save location for general project documents.<br/>
     * If the optional sub-folder path is included but parent directories don't exist then this will
     * create the directory and any parent directories needed. This will default to external storage
     * if available; if external storage is not available then this will default to local storage.
     * @param subFolderPath Optional: The sub-folder within the Documents directory required. Sub
     *                      directories can be included and will be created if they do not exist.
     * @param appContext Application context to use to access Android's resources
     * @return The File Object that represents the directory's location on the phone.
     */
    @Synchronized
    fun getDocumentsFolderPath(subFolderPath:String? = null): File? {

        val folderDir: File?//The File Object that contains the path to the file/directory

        // If there is external storage and it can be written to save bitmap there.
        if (isExternalStorageWritable()) {
            if (subFolderPath == null) {
                folderDir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).path)
            } else {
                folderDir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).path + "/$subFolderPath")
            }

        } else {
            // There is no external storage to write to, save to internal storage.
            if (subFolderPath == null) {
                folderDir = File(appContext.filesDir.path + "/Documents")
            } else {
                folderDir = File(appContext.filesDir.path + "/Documents/$subFolderPath")
            }
        }

        if (!folderDir.isDirectory) {
            folderDir.mkdirs()
        }

        return folderDir
    }

    @Synchronized
    fun getPicturesFolderPath(subFolderPath:String?, appContext: Context): File? {
        var folderDir: File?//The File Object that contains the path to the save folder

        // If there is external storage and it can be written to save bitmap there.
        if (isExternalStorageWritable()) {
            if (subFolderPath == null) {
                folderDir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!.path)
            } else {
                folderDir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!.path + "/$subFolderPath")
            }
        } else {
            // There is no external storage to write to, save to internal storage.
            if (subFolderPath == null) {
                folderDir = File(appContext.filesDir.path + "/Pictures")
            } else {
                folderDir = File(appContext.filesDir.path + "/Pictures/$subFolderPath")
            }
        }

        if (!folderDir.isDirectory) {
            folderDir.mkdirs()
        }

        return folderDir
    }
    //endregion

    //region File Storage Methods
    /**
     * Saves a file object to the file location specified asynchronously or synchronously
     * @param file A File object with the fully qualified path (including file name)
     *             where the data should be saved.
     * @param data The data to be saved.
     * @param async true if operation should be processed asynchronously, defaults to true
     * @param callback Callback to be processed after successful operation. Gets called on the main thread
     *                 if async is true. If async is false then this callback will return immediately.
     * @param error Error callback to be processed after a failed operation. Gets called on the main thread
     *              if async is true. If async is false then this callback will return immediately.
     */
    @Synchronized
    fun saveFile(file:File, data:String, async: Boolean = true, callback:AsyncLambda? = null, error:AsyncErrorLambda? = null) {

        if (async) {
            Thread(Runnable {

                val success = saveFile(file, data)

                object:Handler(Looper.getMainLooper()){
                    override fun handleMessage(msg: Message?) {
                        if (success) {
                            callback?.invoke()
                        } else {
                            error?.invoke("Error saving file ${file.name}")
                        }
                    }
                }.obtainMessage().sendToTarget()
            }).start()
        } else {
            if (saveFile(file, data)) {
                callback?.invoke()
            } else {
                error?.invoke("Error saving file ${file.name}")
            }
        }
    }

    /**
     * Tries to save data to the given file location
     * @param file The file location to save the data
     * @param data The data to be saved at the given file location
     * @return true if successful, false otherwise
     * */
    private fun saveFile(file:File, data:String): Boolean {

        try {
            val inputStream = ByteArrayInputStream(data.toByteArray())
            val outputStream = BufferedOutputStream(FileOutputStream(file))

            var buffer = ByteArray(1024)
            var bytesRead = inputStream.read(buffer, 0, buffer.size)

            while (bytesRead > 0) {
                outputStream.write(buffer, 0, bytesRead)
                bytesRead = inputStream.read(buffer, 0, buffer.size)
            }

            inputStream.close()
            outputStream.close()

            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Deletes a file at the specified location either asynchronously or synchronously
     * @param file The file location that should be delete
     * @param async true if operation should be processed asynchronously, defaults to true
     * @param callback Callback to be processed after successful operation. Gets called on the main thread
     *                 if async is true. If async is false then this callback will return immediately.
     * @param error Error callback to be processed after a failed operation. Gets called on the main thread
     *              if async is true. If async is false then this callback will return immediately.
     * */
    @Synchronized
    fun deleteFile(file:File, async:Boolean = true, callback:AsyncLambda? = null, error: AsyncErrorLambda? = null) {

        if (async) {
            Thread(Runnable {

                val success = deleteFile(file)

                object: Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message?) {
                        if (success) {
                            callback?.invoke()
                        } else {
                            error?.invoke("Error deleting file ${file.name}")
                        }
                    }
                }.obtainMessage().sendToTarget()
            }).start()
        } else {
            if (deleteFile(file)) {
                callback?.invoke()
            } else {
                error?.invoke("Error deleting file: ${file.name}")
            }
        }
    }

    /**
     * Deletes a file at the specified location
     * @param file The file to delete
     * @return true if the file was successfully deleted, false if 'file' is not a file or in error
     * */
    private fun deleteFile(file:File): Boolean {
        return if (!file.isFile) false else file.delete()
    }

    /**
     * This method retrieves file data in String format for the given File object either asynchronously or synchronously.
     *
     * @param file The path to the file that holds the data needed.
     * @param callback Callback to be processed after successful operation. Gets called on the main thread
     *                 if async is true. If async is false then this callback will return immediately.
     * @param async true if operation should be processed asynchronously, defaults to true
     * @param error Error callback to be processed after a failed operation. Gets called on the main thread
     *              if async is true. If async is false then this callback will return immediately.
     */
    @Synchronized
    fun loadFile(file:File, callback: ((contents:String) -> Unit), async:Boolean = true,  error:AsyncErrorLambda? = null) {

        if (async) {
            Thread(Runnable {

                val response = loadFile(file)

                object:Handler(Looper.getMainLooper()){
                    override fun handleMessage(msg: Message?) {
                        if (response != null) {
                            callback(response)
                        } else {
                            error?.invoke("Error loading file: ${file.name}")
                        }
                    }
                }.obtainMessage().sendToTarget()
            }).start()
        } else {
            val response = loadFile(file)

            if (response != null) {
                callback(response)
            } else {
                error?.invoke("Error loading file: ${file.name}")
            }
        }
    }

    /**
     * Tries to load a file from the specified file location.
     * @param file The file location to try and load data from
     * @return The data in string format or null if there was an error
     * */
    private fun loadFile (file:File): String? {
        try {
            val reader = BufferedReader(InputStreamReader(FileInputStream(file)))

            val builder = StringBuilder()
            var line = reader.readLine()

            while (line != null) {
                builder.append(line)
                line = reader.readLine()
            }

            return builder.toString()

        } catch (ioe:IOException) {
            return null
        }
    }

    /**
     * Deletes all contents within the directory given either asynchronously or synchronously
     * @param directory File object representing a directory on the file system.
     * @param async true if operation should be processed asynchronously, defaults to true
     * @param callback Callback to be processed after successful operation. Gets called on the main thread
     *                 if async is true. If async is false then this callback will return immediately.
     * @param error Error callback to be processed after a failed operation. Gets called on the main thread
     *              if async is true. If async is false then this callback will return immediately.
     */
    @Synchronized
    fun deleteDirContents(directory:File, async:Boolean = true, callback: AsyncLambda? = null, error: AsyncErrorLambda? =  null) {

        if (async) {
            Thread(Runnable {
                val success = deleteDirContents(directory)

                object:Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message?) {
                        if (success) {
                            callback?.invoke()
                        } else {
                            error?.invoke("Error deleting contents for directory: ${directory.name}")
                        }
                    }
                }.obtainMessage().sendToTarget()
            }).start()
        } else {
            if (deleteDirContents(directory)) {
                callback?.invoke()
            } else {
                error?.invoke("Error deleting contents for directory: ${directory.name}")
            }
        }
    }

    /**
     * Tries to delete all of the contents within the specified directory
     * @param directory The directory to delete all content from
     * @return true if the contents were deleted, false otherwise
     * */
    private fun deleteDirContents(directory:File): Boolean {

        try {

            if (directory.isFile) {
                // Sanity check.
                Log.d(TAG, "File object is pointing to a file and not a directory. File: ${directory.name}")
                return false
            }

            var success = true
            val fileNameList = directory.list() //List of all the files in this directory

            // For each image in the directory check if it's also in the database and delete it if not
            for (value in fileNameList!!) {
                Log.i(TAG, value)

                val fileToDelete = File(directory, value)

                //Delete the file or (if directory) delete all contents and the directory
                var isDeleted = false
                if (fileToDelete.isFile) {
                    isDeleted = fileToDelete.delete()
                    success = success && isDeleted
                } else if (fileToDelete.isDirectory) {
                    isDeleted = deleteDirContents(fileToDelete)
                    isDeleted = isDeleted && fileToDelete.delete()
                    success = success && isDeleted
                }
                val name = fileToDelete.toString()
                Log.i(TAG, "Deleted $name: $isDeleted")
            }

            return success

        } catch (se:SecurityException) {
            Log.e(TAG, "Invalid Security Permissions: $se")
            return false
        } catch (e:Exception) {
            Log.e(TAG, e.toString())
            return false
        }
    }
    //endregion

    //region JSON Storage Methods
    /**
     * Saves a JSONObject to the file location given either asynchronously or synchronously.
     * @param jsonObject The JSONObject containing the data to save.
     * @param async true if operation should be processed asynchronously, defaults to true
     * @param callback Callback to be processed after successful operation. Gets called on the main thread
     *                 if async is true. If async is false then this callback will return immediately.
     * @param error Error callback to be processed after a failed operation. Gets called on the main thread
     *              if async is true. If async is false then this callback will return immediately.
     */
    @Synchronized
    fun saveJSON(jsonObject: JSONObject, file:File, async:Boolean = true,
                 callback: AsyncLambda? = null, error: AsyncErrorLambda? = null) {

        if (async) {
            Thread(Runnable {
                val success = saveJSON(jsonObject, file)

                object: Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message?) {
                        if (success) {
                            callback?.invoke()
                        } else {
                            error?.invoke("Error saving JSONObject at file location: ${file.name}")
                        }
                    }
                }.obtainMessage().sendToTarget()
            }).start()
        } else {
            if (saveJSON(jsonObject, file)) {
                callback?.invoke()
            } else {
                error?.invoke("Error saving JSONObject for filename: ${file.name}")
            }
        }
    }

    /**
     * Tries to save the given JSONObject to the specified file location
     * */
    private fun saveJSON(jsonObject:JSONObject, file:File): Boolean {

        try {
            val out = OutputStreamWriter(FileOutputStream(file))
            val reader = StringReader(jsonObject.toString())
            val buffer = CharArray(1024)
            var charRead = reader.read(buffer)
            while (charRead  > 0) {
                out.write(buffer, 0, charRead)
                charRead = reader.read(buffer)
            }

            out.close()
            reader.close()

            return true
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            return false
        }
    }

    /**
     * Saves a JSONArray to the file location given.
     * @param jsonArray The JSONArray containing the data to save.
     * @param file File object containing the path to save the JSON data.
     * @param async Whether or not this function should save asynchronously, defaults to true
     * @param callback Callback to be processed after successful operation. Gets called on the main thread
     *                 if async is true
     * @param error Error callback to be processed after a failed operation. Gets called on the main thread
     *              if async is true
     */
    @Synchronized
    fun saveJSON(jsonArray:JSONArray, file:File, async:Boolean = true,
                 callback:AsyncLambda? = null, error: AsyncErrorLambda? = null) {

        if (async) {
            Thread(Runnable {
                val success = saveJSON(jsonArray, file)

                object: Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message?) {
                        if (success) {
                            callback?.invoke()
                        } else {
                            error?.invoke("Error saving JSONArray for filename: ${file.name}")
                        }
                    }
                }.obtainMessage().sendToTarget()
            }).start()
        } else {
            if (saveJSON(jsonArray, file)) {
                callback?.invoke()
            } else {
                error?.invoke("Error saving JSONArray for filename: ${file.name}")
            }
        }
    }

    /**
     * Tries to save the given JSONArray to the specified file location
     * */
    private fun saveJSON(jsonArray:JSONArray, file:File): Boolean {

        try {
            val out = OutputStreamWriter(FileOutputStream(file))
            val reader = StringReader(jsonArray.toString())
            val buffer = CharArray(1024)
            var charRead = reader.read(buffer)
            while (charRead  > 0) {
                out.write(buffer, 0, charRead)
                charRead = reader.read(buffer)
            }

            out.close()
            reader.close()

            return true
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            return false
        }
    }

    /**
     * Tries to load a JSON object from the File location path either asynchronously or synchronously
     * exist.
     * @param file The path to try and fetch JSON data from.
     * @param callback Callback to be processed after successful operation. Gets called on the main thread
     *                 if async is true. If async is false then this callback will return immediately.
     * @param async true if operation should be processed asynchronously, defaults to true
     * @param error Error callback to be processed after a failed operation. Gets called on the main thread
     *              if async is true. If async is false then this callback will return immediately.
     */
    @Synchronized
    fun loadJSONObject(file:File, callback:(JSONObject)->Unit, async:Boolean = true, error:AsyncErrorLambda? = null) {

        if (async) {
            Thread(Runnable {
                val responseObj = loadJSONObject(file)

                object:Handler(Looper.getMainLooper()){
                    override fun handleMessage(msg: Message?) {
                        if (responseObj != null) {
                            callback(responseObj)
                        } else {
                            error?.invoke("Error loading JSONObject for filename: ${file.name}")
                        }
                    }
                }.obtainMessage().sendToTarget()
            }).start()
        } else {
            val response = loadJSONObject(file)

            if (response != null) {
                callback.invoke(response)
            } else {
                error?.invoke("Error retrieving JSONObject for filename: ${file.name}")
            }
        }
    }

    /**
     * Tries to retrieve a JSONObject at the specified file location
     * @param file The file location to try and retrieve the JSONObject from
     * @return The JSONObject if successful, null otherwise
     * */
    private fun loadJSONObject(file:File): JSONObject? {
        try {
            BufferedReader(InputStreamReader(FileInputStream(file))).use {
                val builder = StringBuilder()
                var line = it.readLine()
                while (line != null) {
                    builder.append(line)
                    line = it.readLine()
                }

                return JSONTokener(builder.toString()).nextValue() as? JSONObject
            }
        } catch (e:IOException) {
            Log.e(TAG, e.toString())
            return null
        }
    }

    /**
     * Tries to load a JSON Array from the File location path
     * exist.
     * @param file The path to try and fetch JSON data from.
     * @param callback Callback to be processed after successful operation. Gets called on the main thread
     *                 if async is true. If async is false then this callback will return immediately.
     * @param async true if operation should be processed asynchronously, defaults to true
     * @param error Error callback to be processed after a failed operation. Gets called on the main thread
     *              if async is true. If async is false then this callback will return immediately.
     */
    @Synchronized
    fun loadJSONArray(file:File, callback:(JSONArray)->Unit, async:Boolean = true, error:AsyncErrorLambda? = null) {

        if (async) {
            Thread(Runnable {
                val response = loadJSONArray(file)

                object:Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message?) {
                        if (response != null) {
                            callback(response)
                        } else {
                            error?.invoke("Error retrieving JSONArray from filename: ${file.name}")
                        }
                    }
                }.obtainMessage().sendToTarget()
            }).start()
        } else {
            val response = loadJSONArray(file)

            if (response != null) {
                callback.invoke(response)
            } else {
                error?.invoke("Error loading JSONArray from filename: ${file.name}")
            }
        }
    }

    /**
     * Tries to retrieve a JSONArray at the specified file location
     * @param file The file location to try and retrieve the JSONArray from
     * @return The JSONArray if successful, null otherwise
     * */
    private fun loadJSONArray(file:File): JSONArray? {
        try {
            BufferedReader(InputStreamReader(FileInputStream(file))).use {
                val builder = StringBuilder()
                var line = it.readLine()
                while (line != null) {
                    builder.append(line)
                    line = it.readLine()
                }

                return JSONTokener(builder.toString()).nextValue() as? JSONArray
            }
        } catch (e:IOException) {
            Log.e(TAG, e.toString())
            return null
        }
    }
    //endregion

    //region Image Storage Methods
    /**
     * Asynchronously saves a bitmap to the location specified by the File parameter
     * @param imageName Name of the bitmap.
     * @param bitmap The actual Bitmap to save.
     * @param file The location to save the Bitmap including the filename.
     * @param async true if operation should be processed asynchronously, defaults to true
     * @param callback Callback to be processed after successful operation. Gets called on the main thread
     *                 if async is true. If async is false then this callback will return immediately.
     * @param error Error callback to be processed after a failed operation. Gets called on the main thread
     *              if async is true. If async is false then this callback will return immediately.
     */
    @Synchronized
    fun saveBitmap(imageName: String, bitmap: Bitmap, file: File, async: Boolean = true,
                   callback: AsyncLambda? = null, error:AsyncErrorLambda? = null) {

        if (async) {
            Thread(Runnable {
                val success = saveBitmap(imageName, bitmap, file)

                object:Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message?) {
                        if (success) {
                            callback?.invoke()
                        } else {
                            error?.invoke("Error saving $imageName to file: ${file.name}")
                        }
                    }
                }.obtainMessage().sendToTarget()
            }).start()
        } else {
            if (saveBitmap(imageName, bitmap, file)) {
                callback?.invoke()
            } else {
                error?.invoke("Error saving $imageName to file: ${file.name}")
            }
        }
    }

    /**
     * Saves a bitmap image with the specified name at the given file location.
     * @param imageName The image name to save the image under
     * @param bitmap The Bitmap data to be saved
     * @param file The file location to save the image at
     * @return true if successful, false otherwise
     * */
    private fun saveBitmap(imageName: String, bitmap: Bitmap, file: File): Boolean {
        // Save the bitmap to the file location
        try {
            FileOutputStream(file).use {
                return  bitmap.compress(getBitmapFormat(imageName), 100, it)
            }

        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            return false
        }
    }

    /**
     * Returns a sub-sampled Bitmap from local storage. Used to reduce memory usage and prevent
     * OutOfMemory crashes.
     * @param fileLocation The file location of the image including the image name.
     * @param reqWidth The width in pixels of the container for the image.
     * @param reqHeight The height in pixels of the container for the image.
     * @return The sub-sampled Bitmap
     */
    @Synchronized
    fun getSampledBitmapFromFile(fileLocation: File, reqWidth: Int,
                                 reqHeight: Int): Bitmap {

        var options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(fileLocation.absolutePath)

        options = getSampledBitmapOptions(options, reqWidth, reqHeight)

        return BitmapFactory.decodeFile(fileLocation.absolutePath, options)

    }

    /**
     * Returns a sub-sampled Bitmap from project resources. Used to reduce memory usage and prevent
     * OutOfMemory crashes.
     * @param resourceId The resource ID of the image to use.
     * @param reqWidth The width in pixels of the container for the image.
     * @param reqHeight The height in pixels of the container for the image.
     * @param callback Callback to be processed after successful operation. Gets called on the main thread
     *                 if async is true. If async is false then this callback will return immediately.
     * @param async true if operation should be processed asynchronously, defaults to true
     * @param error Error callback to be processed after a failed operation. Gets called on the main thread
     *              if async is true. If async is false then this callback will return immediately.
     */
    @Synchronized
    fun getSampledBitmapFromResource(resourceId: Int, reqWidth: Int, reqHeight: Int, callback: (bitmap:Bitmap)->Unit,
                                     async:Boolean = true, error:AsyncErrorLambda? = null) {

        if (async) {
            Thread(Runnable {

                val response = getSampledBitmap(resourceId, reqWidth, reqHeight)

                object:Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message?) {
                        if (response != null) {
                            callback(response)
                        } else {
                            error?.invoke("Error retrieving sampled bitmap for resource")
                        }
                    }
                }.obtainMessage().sendToTarget()
            }).start()
        } else {
            val response = getSampledBitmap(resourceId, reqWidth, reqHeight)

            if (response != null) {
                callback(response)
            } else {
                error?.invoke("Error retrieving sampled bitmap for resource")
            }
        }
    }

    /**
     * Returns a sub-sampled Bitmap from project resources. Used to reduce memory usage and prevent
     * OutOfMemory crashes.
     * @param resourceId The resource ID of the image to use.
     * @param reqWidth The width in pixels of the container for the image.
     * @param reqHeight The height in pixels of the container for the image.
     * @return The sub-sampled Bitmap or null if there was an error
     */
    private fun getSampledBitmap(resourceId: Int, reqWidth: Int, reqHeight: Int): Bitmap? {
        var options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeResource(appContext.resources, resourceId, options)

        options = getSampledBitmapOptions(options, reqWidth, reqHeight)

        return BitmapFactory.decodeResource(appContext.resources, resourceId, options)
    }

    /**
     * TODO: Update to allow for any image file format.
     *
     * Returns the type of image file the image is in based off of it's filename.
     *
     * @param imageName
     * @return
     */
    private fun getBitmapFormat(imageName: String): Bitmap.CompressFormat {
        return if (imageName.contains(".jpg")) {
            Bitmap.CompressFormat.JPEG
        } else {
            Bitmap.CompressFormat.PNG
        }
    }

    private fun getSampledBitmapOptions(initialOptions: BitmapFactory.Options,
                                        reqWidth: Int, reqHeight: Int): BitmapFactory.Options {

        initialOptions.inSampleSize = calculateInSampleSize(initialOptions, reqWidth, reqHeight)

        // Use this display's density for the image
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(metrics)
        val density = metrics.density.toInt()
        initialOptions.inScreenDensity = density
        initialOptions.inDensity = initialOptions.inScreenDensity
        initialOptions.inTargetDensity = density

        // Set to decode the whole image
        initialOptions.inJustDecodeBounds = false
        return initialOptions
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of the image
        val width = options.outWidth
        val height = options.outHeight
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {

            val halfHeight = height / 2
            val halfWidth = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2 // inSampleSize will always round to a power of 2.
            }
        }

        return inSampleSize
    }
    //endregion

}