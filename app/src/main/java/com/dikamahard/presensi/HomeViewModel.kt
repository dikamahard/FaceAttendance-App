package com.dikamahard.presensi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException

class HomeViewModel : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
        private const val MASTER_STORAGE = "master"
        private const val RECORD_STORAGE = "record"
        private const val RECORD_DATABASE = "records"
        private const val USER_DATABASE = "users"
    }
    // TODO: Implement the ViewModel
    private var database = Firebase.database
    private var storage = Firebase.storage
    val masterRef = storage.reference.child(MASTER_STORAGE)
    val client = OkHttpClient()


    private val _images = MutableLiveData<List<Bitmap>>()
    val images: LiveData<List<Bitmap>> = _images

    private val _names = MutableLiveData<List<String>>()
    val names: LiveData<List<String>> = _names

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        viewModelScope.launch {
            fetchMasterData()
        }
    }

    // TODO: WHILE FETCHING MASTER DATA, DO LOADING ANIMATION (loading spiiner)
    suspend fun fetchMasterData() {
        _isLoading.value = true
        val listUrl = mutableListOf<Uri>()
        val listName = mutableListOf<String>()
        val listImage = mutableListOf<Bitmap>()

        val masterRefItems = masterRef.listAll().await().items
        Log.d(TAG, "fetchMasterData: result $masterRefItems")
/*
        for (url in masterRefItems) {
            Log.d(TAG, "fetchMasterData: download url $url")
            val imageUrl = url.downloadUrl.await()
            listUrl.add(imageUrl)
            listName.add(url.name)
            Log.d(TAG, "fetchMasterData: list ${listUrl.size}")

            // donwload image
            val downloadedImage = downloadBitmap(imageUrl.toString())
            downloadedImage?.let { listImage.add(it) }
        }
 */

        // this will mapped url in masterRefItems to run the parallel downloading using coroutine
        val jobs = masterRefItems.map { url ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    url.downloadUrl.await().toString().let { imageUrl ->
                        downloadBitmap(imageUrl)?.let { bitmap ->
                            synchronized(listImage) {
                                listImage.add(bitmap)
                                listName.add(url.name)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading image: ${e.message}")
                }
            }
        }
        jobs.joinAll()

        Log.d(TAG, "fetching url completed")
        _images.postValue(listImage)
        _names.postValue(listName)
        _isLoading.value = false

        // TODO: DOWNLOAD FROM URL AND PUT INTO LIST OF BITMAP




    /*    masterRef.listAll()
            .addOnSuccessListener { result ->
                val listOfBitmaps = mutableListOf<Bitmap>()
                val listOfNames = mutableListOf<String>()

                //TODO: GLIDE HERE WILL BREAK MVVM. alternative : fetch url here, then glide the url in fragment
                for (ref in result.items) {
                    ref.downloadUrl.addOnSuccessListener { imageUrl ->
                        listUrl.add(imageUrl)
                        listName.add(ref.name)
                        Log.d(TAG, "fetchMasterData: list ${listUrl.size}")
                        //Glide.with(Application)
                    }
                    Log.d(TAG, "fetchMasterData: outside download")
                }
                // Send data to be observed on fragment
                Log.d(TAG, "fetchMasterData: DONE")
                _isLoading.value = false
                _images.postValue(listUrl)
                _names.postValue(listName)
            }
            .addOnFailureListener {
                _error.value = it.message
                _isLoading.value = false
                Log.e(TAG, "loadMasterData: ${it.message}")
            }

     */


    }

    //download using okhttp
    suspend fun downloadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                response.body?.byteStream()?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading image: ${e.message}")
            null
        }
    }



}