package com.dikamahard.presensi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.dikamahard.presensi.databinding.FragmentHomeBinding
import com.dikamahard.presensi.helper.createCustomTempFile
import com.example.faceverify.models.mfn.MobileFaceNet
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.math.log
import kotlin.properties.Delegates

class HomeFragment : Fragment() {

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.ACCESS_FINE_LOCATION)
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val RECORD = "records"
    }


    // This property is only valid between onCreateView and
    // onDestroyView.

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var binding: FragmentHomeBinding
    private var getFile: File? = null
    private var database = Firebase.database
    private var storage = Firebase.storage
    private lateinit var fusedLocationClient: FusedLocationProviderClient


    private val testRef = storage.reference.child("record/test2.jpg")
    val downloadRef = storage.reference.child("master/1.JPG")

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(
                    requireContext(),
                    "Permission Required!!!",
                    Toast.LENGTH_SHORT
                ).show()
                requireActivity().finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
        // for actionbar name
        (activity as MainActivity).supportActionBar?.title = "Attendance"

        // VARIABLE NEEDED FOR IMAGE COMPARE
        lateinit var listOfBitmaps: List<Bitmap>
        lateinit var listOfNames: List<String>
        val listOfCroppedBitmaps: MutableList<Bitmap> = mutableListOf() // what we will use for compare

        lateinit var croppedCaptureBitmap: Bitmap // what we will use for compare

        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        // TODO: Use the ViewModel
        homeViewModel.isLoading.observe(viewLifecycleOwner) {
            Log.d("DEBUG", "OBSERVE LOADING $it")
            showLoading(it)
        }

        homeViewModel.images.observe(viewLifecycleOwner) { images ->
            listOfBitmaps = images
        }

        homeViewModel.names.observe(viewLifecycleOwner) { names ->
            listOfNames = names
            Log.d("TAG", "names observe: $listOfNames")
        }


        Log.d("TAG", "onViewCreated: should be after glide")
        // ML KIT
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.LANDMARK_MODE_NONE) // skips landmark mapping
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE) // skips facial expressions and other classification such as wink
            .build()
        val faceDetector = FaceDetection.getClient(options)

        // INIT MODEL MOBILE FACENET
        lateinit var mfn: MobileFaceNet
        try {
            mfn = MobileFaceNet(requireContext().assets)
        } catch (e: IOException) {
            Log.e("ERROR", "Error initing models", e)
        } finally {
            Log.d("DEBUG", "SUCCESS INIT MODEL")
        }




        binding.btnCamera.setOnClickListener {
            if (checkPermission(android.Manifest.permission.CAMERA)){
                startCamera()
            } else{
                launcherRequestPermission.launch(
                    arrayOf(
                        android.Manifest.permission.CAMERA,
                    )
                )
            }
        }

        /////////////// TODO: EXPERIMENT BUTTON
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        binding.btnTes.setOnClickListener {
            /*
            val time = Calendar.getInstance().time
            var formatter = SimpleDateFormat("dd-MM-yyyy")
            val currentDate = formatter.format(time)
            formatter = SimpleDateFormat("HH:mm:ss")
            val currentTime = formatter.format(time)
            val recordData = mapOf<String, Boolean>(
                currentTime to true
            )
            Log.d("TAG", "onViewCreated: $currentDate")

            database.reference.child(RECORD).child("9999").child(currentDate).updateChildren(recordData)
                .addOnSuccessListener {
                Log.d("TAG", "onViewCreated: sukses")
                }.addOnFailureListener {
                    Log.d("TAG", "onViewCreated: $it")
                }.addOnCompleteListener {
                    Log.d("TAG", "onViewCreated: $it")
                }

             */
//            val targetLat = -6.923384445321216  // unpad coordinate
//            val targetLong = 107.77301948057257
            

            val targetLat = -6.237438 // home coordinate
            val targetLong = 106.526787

//            val targetLat = 80.01685951702866 // greenland coordinate
//            val targetLong = -46.644851699388674

            val distance = FloatArray(1)
            getCurrentLocation{ latitude,longitude ->
                // Use latitude and longitude for your conditional check
                if (latitude != 0.0 && longitude != 0.0) {
                    // Location detected, do something
                    android.location.Location.distanceBetween(targetLat, targetLong, latitude, longitude, distance)
                    Log.d("TAG", "lat long: $latitude, $longitude")
                    Log.d("TAG", "Distance: ${distance[0]}")
                    if (distance[0] <= 1000) {  // meters
                        binding.btnProcess.visibility = View.VISIBLE
                        binding.btnCamera.visibility = View.VISIBLE
                    }else{
                        Toast.makeText(
                            requireContext(),
                            "Not In Radius 1000 Meter",
                            Toast.LENGTH_SHORT
                        ).show()
                    }



                } else {
                    // No location detected, handle accordingly
                    Log.d("TAG", "No Location, Please Activate GPS")
                }
            }
//            android.location.Location.distanceBetween(pointLat, pointLong, locationLat, locationLong, distance)
//            Log.d("TAG", "onViewCreated: $locationLat")
            /*
                        if (locationLat != null && locationLong != null) {
                            android.location.Location.distanceBetween(pointLat, pointLong, locationLat?:0.0, locationLong?:0.0, distance)
                            Log.d("TAG", "Distance: ${distance[0]}")
                        } else {
                            Log.d("TAG", "Distance not found $locationLat, $locationLong")
                        }

             */

        }
        ////////////////////


        binding.btnProcess.setOnClickListener {
            // uploadImage()
            // get img from cloud storage
            // detect faces
            // compare

            // TODO: RESET CONDITION IF TAKING ANOTHER PICTURE


            val tempFile = File.createTempFile("images", "jpg")



            //val rotation = getRotationCompensation("MY_CAMERA_ID",requireActivity(),true)

//            val bitmap = BitmapFactory.decodeFile(getFile!!.path)
//            Log.d("DEBUG", "bitmap = ${getFile!!.path}")
//            val image = InputImage.fromBitmap(bitmap, 0)
//            Log.d("DEBUG", "image = ${image != null}")




//            var i = 1
//            for (bitmap in listOfBitmaps) {
//                val image = InputImage.fromBitmap(bitmap, 0)
//                facedetector.process(image)
//                    .addOnSuccessListener { faces ->
//                        for (face in faces) {
//                            val bounds = face.boundingBox
//                            val croppedbmap = Bitmap.createBitmap(bitmap,bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top)
//                            listOfCroppedBitmaps.add(croppedbmap)
//                            Log.d("DEBUG", "hasil crop $i oke ${listOfCroppedBitmaps.size}")
//                            i += 1
//                        }
//                    }
//            }

            var processing1 = true
            var processing2 = true

            // improve with livedata

            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    while (processing1 || processing2) {
                        withContext(Dispatchers.Main) {
                            //binding.tvName.visibility = View.VISIBLE
                            binding.tvName.text = "Processing Image"
                        }
                    }
                    Log.d("TAG", "onViewCreated: 1 in")


                    //                withContext(Dispatchers.Main) {
//                    binding.tvName.text = "Processing Completed"
//                }

                    // TODO: COMPARE FACES(done) --> ADD CONDITION IF DIDN'T FIND MATCHING FACE
                    Log.d("COMPARE", "before compare")
                    var i = 0
                    var faceMatched = false

                    for (croppedMasterBitmap in listOfCroppedBitmaps){
                        val similarity = mfn.compare(croppedCaptureBitmap, croppedMasterBitmap)
                        Log.d("COMPARE", "comparing ${listOfNames[i]} = $similarity")
                        withContext(Dispatchers.Main) {
                            binding.ivResult.setImageBitmap(croppedMasterBitmap)
                        }
                        if (similarity > MobileFaceNet.THRESHOLD) {
                            Log.d("COMPARE", "compare found")
                            faceMatched = true
                            withContext(Dispatchers.Main) {
                                binding.tvName.visibility = View.VISIBLE
                                binding.tvName.text = "${listOfNames[i].split('.')[0]}'s Face Detected"
                            }

                            // TODO: GET image name then Record to database (done) --> change to be button press to post attendance(done)

                            withContext(Dispatchers.Main) {
                                binding.ivResult.visibility = View.VISIBLE
                                binding.ivResult.setImageBitmap(croppedMasterBitmap)
                                binding.btnSubmit.apply {
                                    this.visibility = View.VISIBLE
                                    this.setOnClickListener {
                                        postAttendance(listOfNames[i])
                                    }
                                }
                            }
                            break
                        }
                        i+=1
                    }

                    if (!faceMatched) {
                        throw Exception("Face Not Found!!")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.ivResult.setImageResource(R.drawable.noimg)
                        Toast.makeText(
                            requireContext(),
                            "${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()

                        binding.tvName.text = "Unknown Face"
                    }
                    Log.e("COMPARE ERROR", "exception ${e.message}")
                }
                Log.d("COMPARE", "compare done")

            }


            // Process 1 detect face from camera
            lifecycleScope.launch(Dispatchers.Default) {
                // get bitmap from camera
                val capturedBitmap = BitmapFactory.decodeFile(getFile!!.path)
                Log.d("DEBUG", "bitmap = ${capturedBitmap != null}")
                Log.d("DEBUG", "Before " + capturedBitmap.width + " x "+ capturedBitmap.height)

                // resize bitmap w x h
                val resizedBitmap = Bitmap.createScaledBitmap(capturedBitmap, (capturedBitmap.width * 0.4).toInt(), (capturedBitmap.height*0.4).toInt(), true)
                Log.d("DEBUG", "After " + resizedBitmap.width + " x "+ resizedBitmap.height)

                // rotate bitmap
                val rotation = ExifInterface(getFile!!).rotationDegrees
                Log.d("TAG", "Rotation on detect face : $rotation")
                val matrix = Matrix()
                matrix.postRotate(rotation.toFloat())
                val rotatedBitmap = Bitmap.createBitmap(resizedBitmap, 0, 0, resizedBitmap.width, resizedBitmap.height, matrix, true)



                val capturedImage = InputImage.fromBitmap(rotatedBitmap, 0)
                Log.d("DEBUG", "image = ${capturedImage != null}")
                Log.d("COROUTINE", "Called before Cropping Capture"+ Thread.currentThread().name)

                // TODO: DEBUG BELOW CODE (done)
                try {
                    val faces = faceDetector.process(capturedImage).await()
                    Log.d("FACES", "$faces")

                    if (faces.isEmpty()) {
                        throw Exception("No Face Detected")
                    }else if (faces.size >= 2) {
                        throw Exception("Multiple Faces Detected")
                    }

                    for (face in faces) {
                        val bounds = face.boundingBox
                        val croppedBitmap = Bitmap.createBitmap(rotatedBitmap,bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top)
                        croppedCaptureBitmap = croppedBitmap
                        withContext(Dispatchers.Main){
                            binding.ivPreview.setImageBitmap(croppedBitmap)
                        }
                        Log.d("COROUTINE", "Called after Cropped Capture Success with width ${croppedBitmap.width} and thread "+ Thread.currentThread().name)
                    }
                    processing1 = false
                    Log.d("PROCESS", "process 1 = $processing1")

                }catch (e: Exception) {
                    Log.e("FaceDetection", "Error detecting camera faces: ${e.message}", )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()

                        binding.tvName.text = "Error, ${e.message}"
                    }
                }
                Log.d("COROUTINE", "AFTER FACEDETECT "+ Thread.currentThread().name)

            }


            // Process 2 detect face from firebase
            lifecycleScope.launch(Dispatchers.Default) {
                for (bitmap in listOfBitmaps) {
                    //lifecycleScope.launch(Dispatchers.Default) {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    try {
                        val faces = faceDetector.process(image).await()
                        for (face in faces) {
                            val bounds = face.boundingBox
                            // Create a cropped bitmap using the face bounding box
                            val croppedBitmap = Bitmap.createBitmap(
                                bitmap,
                                bounds.left,
                                bounds.top,
                                bounds.right - bounds.left,
                                bounds.bottom - bounds.top
                            )
                            // Add the cropped bitmap to the list
                            listOfCroppedBitmaps.add(croppedBitmap)
                            Log.d("DEBUG", "hasil crop oke ${listOfCroppedBitmaps.size}")
                        }
                        Log.d("DEBUG", "should be call after cropping ${listOfCroppedBitmaps.size}")


                    } catch (e: Exception) {
                        // Handle failure (e.g., log an error)
                        Log.e("FaceDetection", "Error detecting master faces: ${e.message}")
                    }
                    //}
                }
                processing2 = false
                Log.d("PROCESS", "process 2 = $processing2")

                Log.d("COROUTINE", "Called after detecting faces "+ Thread.currentThread().name)

                // to check list of name detected in firebase
//                withContext(Dispatchers.Main){
//                    for (i in 0 until listOfCroppedBitmaps.size) {
//                        binding.ivPreview.setImageBitmap(listOfCroppedBitmaps[i])
//                        Log.d("TAG", "Name: ${listOfNames[i]} ")
//                        delay(3000)
//                        Log.d("TAG", "onViewCreated: Done delay")
//                    }
//                }
            }



            // THIS BLOCKING UI THREAD / MAIN

//            Log.d("COROUTINE", "conditional processing "+ Thread.currentThread().name)
//            while (processing1 || processing2) {
//                binding.tvName.text = "Processing Image"
//            }
//            Log.d("COROUTINE", "after conditional processing2 "+ Thread.currentThread().name)
//            binding.tvName.text = "Processing Completed"




            // this is doable but not best practice since the delay is hard coded
//            lifecycleScope.launch {
//                delay(500) // Add a small delay to ensure all tasks finish
//                Log.d("DEBUG", "should be call after cropping")
//                Log.d("DEBUG", "All face detection tasks completed ${listOfCroppedBitmaps.size}")
//            }


/*
            val bmap = BitmapFactory.decodeFile(getFile!!.path)
            Log.d("DEBUG", "Before " + bmap.width + " x "+ bmap.height)
            //TODO:resize bitmap w x h
            val rbmap = Bitmap.createScaledBitmap(bmap, (bmap.width * 0.4).toInt(), (bmap.height*0.4).toInt(), true)
            Log.d("DEBUG", "After " + rbmap.width + " x "+ rbmap.height)

            // rotate bmap
            val matrix = Matrix()
            matrix.postRotate(-90f)
            val rotatedBitmap = Bitmap.createBitmap(bmap, 0, 0, bmap.width, bmap.height, matrix, true)

            val imaget = InputImage.fromBitmap(rotatedBitmap, 0)

            // TODO:WHATS WRONG WITH PHOTO TAKEN FROM CAMERA!!!!
            lifecycleScope.launch {
                Log.d("COROUTINE", Thread.currentThread().name)
                try {
                    val faces = faceDetector.process(imaget).await()
                    Log.d("DEBUG", "HERE " + faces.size)    // NO FACE DETECTED?
                    for (face in faces) {
                        val bounds = face.boundingBox
                        Log.d("DEBUG", "bounding box : " + face.boundingBox)
                        val croppedbmap = Bitmap.createBitmap(rotatedBitmap,bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top)
                        if (croppedbmap != null) {
                            Log.d("DEBUG", "hasil crop oke")
                            binding.ivPreview.setImageBitmap(croppedbmap)
                        } else {
                            // Handle the case where no faces were detected
                            Log.d("DEBUG", "hasil crop null")

                        }
                    }
                }catch (e: Exception) {
                    Log.e("ERROR", "${e.message} ", )
                }
            }

 */


        } // Button
    } // OnViewCreated

    private fun uploadImage() {
        var file = Uri.fromFile(getFile)
        var uploadTask = testRef.putFile(file)

        // Register observers to listen for when the download is done or if it fails
        uploadTask.addOnFailureListener {
            // Handle unsuccessful uploads
            Log.d("TAG", "uploadImage: FAILED")
        }.addOnSuccessListener { taskSnapshot ->
            // taskSnapshot.metadata contains file metadata such as size, content-type, etc.
            // ...
            Log.d("TAG", "uploadImage: SUCCESS")

        }
    }


    private fun startCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.resolveActivity(requireActivity().packageManager)
        createCustomTempFile(requireContext()).also {
            val photoURI: Uri = FileProvider.getUriForFile(
                requireActivity(),
                "com.dikamahard.presensi",
                it
            )
            currentPhotoPath = it.absolutePath
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            launcherIntentCamera.launch(intent)
        }
    }

    private lateinit var currentPhotoPath: String

    private val launcherIntentCamera = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if(it.resultCode == AppCompatActivity.RESULT_OK) {
            val myFile = File(currentPhotoPath)
            getFile = myFile    //  TODO: this is what we will upload (done)
            myFile.let { file ->
                val bitmap = BitmapFactory.decodeFile(file.path)
                val matrix = Matrix()
                // dynamic camera rotation from exif
                val rotation = ExifInterface(file).rotationDegrees
                Log.d("TAG", "Rotation on camera capture: $rotation")
                matrix.postRotate(rotation.toFloat())
                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                binding.ivPreview.setImageBitmap(rotatedBitmap)
                // TODO: RESET CONDITION HERE
                binding.ivResult.visibility = View.INVISIBLE
                if (binding.btnSubmit.visibility == View.VISIBLE) {
                    binding.btnSubmit.visibility = View.INVISIBLE
                }

            }
        }
    }

    private fun postAttendance(userId: String) {
        val time = Calendar.getInstance().time
        var formatter = SimpleDateFormat("MM-dd-yyyy")
        val currentDate = formatter.format(time)
        formatter = SimpleDateFormat("HH:mm:ss")
        val currentTime = formatter.format(time)
        val recordData = mapOf<String, Boolean>(
            currentTime to true
        )
        val id = userId.split('.')[0]
        Log.d("TAG", "postAttendance: $id")

        //TODO: move to repository
        // to records db ref
        database.reference.child(RECORD).child(id).child(currentDate).updateChildren(recordData)
            .addOnSuccessListener {
                Log.d("TAG", "onViewCreated: sukses attendance record")
                Toast.makeText(requireContext(), "Attendance Record Submitted", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener {
                Log.d("TAG", "onViewCreated: $it")
            }.addOnCompleteListener {
                Log.d("TAG", "onViewCreated: $it")
            }

        //to dates db ref
        database.reference.child("dates").child(currentDate).child(id).updateChildren(recordData)
            .addOnSuccessListener {
                Log.d("TAG", "onViewCreated: sukses attendance dates")
            }.addOnFailureListener {
                Log.d("TAG", "onViewCreated: $it")
            }.addOnCompleteListener {
                Log.d("TAG", "onViewCreated: $it")
            }
    }

    private fun showLoading(isLoading: Boolean) {
        if(isLoading) {
            binding.progressBarHome.visibility = View.VISIBLE
            Log.d("TAG", "loading is visible : ${homeViewModel.isLoading.value}")
        }else {
            binding.progressBarHome.visibility = View.GONE
            Log.d("TAG", "loading is GONE : ${homeViewModel.isLoading.value}")

        }
    }

    private val launcherRequestPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false -> {
                    // Precise location access granted.
                    //getCurrentLocation()
                }
                else -> {
                    // No location access granted.
                }
            }
        }
    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
    private fun getCurrentLocation(callback: (Double, Double) -> Unit) {
        if (checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)){
            // TODO: RETURN USER LOCATION

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    Toast.makeText(
                        requireContext(),
                        "LOCATION DETECTED AT ${location.latitude}, ${location.longitude}",
                        Toast.LENGTH_SHORT
                    ).show()

                    val latitude = location.latitude
                    val longitude = location.longitude

                    callback(latitude, longitude)


                } else {
                    Toast.makeText(
                        requireContext(),
                        "No Location Detected",
                        Toast.LENGTH_SHORT
                    ).show()
                    callback(0.0, 0.0)
                }
            }

        } else{
            launcherRequestPermission.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                )
            )
        }
    }

}