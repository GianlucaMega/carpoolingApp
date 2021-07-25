package it.polito.mad.carpoolingapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class EditProfileFragment : Fragment() {

    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_GALLERY_IMAGE = 2
    private var imageUri: Uri? = null
    private var newPhotoUri: Uri? = null
    private lateinit var etName : TextInputEditText
    private lateinit var etNick : TextInputEditText
    private lateinit var etEmail : TextInputEditText
    private lateinit var etLocation : TextInputEditText
    private lateinit var ivEditPhoto : ImageView
    private var auth: FirebaseAuth? = FirebaseAuth.getInstance()
    private var profile: String? = ""
    private var driverTotalStars: Long = 0
    private var driverNumberOfRatings: Long = 0
    private var passengerTotalStars: Long = 0
    private var passengerNumberOfRatings: Long = 0
    @ExperimentalCoroutinesApi
    private lateinit var viewModel : UserProfileViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        profile = arguments?.getString("profileId")?: auth?.currentUser?.uid
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false)
    }

    @ExperimentalCoroutinesApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etName = requireView().findViewById(R.id.etName)
        etNick = requireView().findViewById(R.id.etNick)
        etEmail = requireView().findViewById(R.id.etEmail)
        etLocation = requireView().findViewById(R.id.etLocation)
        ivEditPhoto = requireView().findViewById(R.id.ivEditPhoto)

        val editPicButton = requireView().findViewById<ImageButton>(R.id.imageButton)
        registerForContextMenu(editPicButton)

        val factory = UserViewModelFactory(profile!!)
        viewModel = ViewModelProviders.of(this, factory).get(UserProfileViewModel::class.java)
        viewModel.user.observe(viewLifecycleOwner){
            if(it!=null && it.toString()!="{}")
                populate(it)
        }
    }

    private fun populate(json: JSONObject): Unit {
        etName.setText(json.getString("name"))
        etNick.setText(json.getString("nickname"))
        etEmail.setText(json.getString("email"))
        etLocation.setText(json.getString("location"))

        if (json.has("driverTotalStars")) driverTotalStars = json.getLong("driverTotalStars")
        if (json.has("driverNumberOfRatings")) driverNumberOfRatings = json.getLong("driverNumberOfRatings")
        if (json.has("passengerTotalStars")) passengerTotalStars = json.getLong("passengerTotalStars")
        if (json.has("passengerNumberOfRatings")) passengerNumberOfRatings = json.getLong("passengerNumberOfRatings")

        if(isAdded){
            val storageReference = Firebase.storage.reference.child("/profileImages/${profile}.jpg")
            storageReference.metadata.addOnSuccessListener {
                GlideApp.with(this)
                    .load(storageReference)
                    .signature(ObjectKey(it.updatedTimeMillis.toString()))
                    .into(ivEditPhoto)
            }.addOnFailureListener{
                Log.d("dbg", "Profile picture download error")
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.save_edit_profile_menu, menu)
        menu.removeItem(R.id.itemInfo)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.itemSave -> {
                saveProfile()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater: MenuInflater? = activity?.menuInflater
        inflater?.inflate(R.menu.edit_picture_menu, menu)
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.itemGallery -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(intent, REQUEST_GALLERY_IMAGE)
                true
            }
            R.id.itemCamera -> {
                dispatchTakePictureIntent()
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    @ExperimentalCoroutinesApi
    private fun saveProfile(){
        val name = etName.text.toString()
        val nick = etNick.text.toString()
        val email = etEmail.text.toString()
        val location = etLocation.text.toString()
        val map = HashMap<String, Any>()
        map["name"] = name
        map["nickname"] = nick
        map["email"] = email
        map["location"] = location
        map["driverTotalStars"] = driverTotalStars
        map["driverNumberOfRatings"] = driverNumberOfRatings
        map["passengerTotalStars"] = passengerTotalStars
        map["passengerNumberOfRatings"] = passengerNumberOfRatings

        if (imageUri != null) {
            val file: Uri = if (imageUri!!.isRelative)
                Uri.fromFile(File(imageUri.toString()))
            else
                imageUri!!

            profile?.let {
                viewModel.setProfile(
                    it,
                    map,
                    {
                        if (file != null) {
                            val ivNavHeader = activity?.findViewById<ImageView>(R.id.carImage)
                            ivNavHeader?.setImageURI(file)
                            val img = Firebase.storage.reference.child("/profileImages/${it}.jpg")
                            img.putFile(file).addOnCompleteListener {
                                Snackbar.make(
                                    requireView(),
                                    R.string.snackProfileSaved,
                                    Snackbar.LENGTH_LONG
                                ).show()
                                findNavController().navigate(R.id.action_nav_edit_profile_to_nav_show_profile)
                            }.addOnProgressListener { task ->
                                Toast.makeText(
                                    context,
                                    "Uploading picture: " + ((task.bytesTransferred / task.totalByteCount) * 100).toString() + "%",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                        }
                    },
                    { err: Exception ->
                        Log.d("dbg", err.message.toString())
                        Toast.makeText(
                            this.context,
                            R.string.daoSetProfileError,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
        else
            profile?.let {
                viewModel.setProfile(
                    it,
                    map,
                    {
                        Snackbar.make(
                            requireView(),
                            R.string.snackProfileSaved,
                            Snackbar.LENGTH_LONG
                        ).show()
                        findNavController().navigate(R.id.action_nav_edit_profile_to_nav_show_profile)
                    },
                    { err: Exception ->
                        Log.d("dbg", err.message.toString())
                        Toast.makeText(
                            this.context,
                            R.string.daoSetProfileError,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
    }

    private fun dispatchTakePictureIntent() {

        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
          // Create the File where the photo should go
            val photoFile: File? = try {
                createImageFile()
            } catch (ex: IOException) {
                // Error occurred while creating the File
                Log.d("FILE_ERROR", "Error creating a file for the camera activity")
                null
            }
            // Continue only if the File was successfully created
            photoFile?.also {
                val photoURI: Uri = FileProvider.getUriForFile(
                        this.requireContext(),
                        "it.polito.mad.carpoolingapp.fileprovider",
                        it
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            }

        }
    }

    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = activity?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
                "profile_${timeStamp}_", /* prefix */
                ".jpg", /* suffix */
                storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            newPhotoUri = absolutePath.toUri()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == AppCompatActivity.RESULT_OK) {
            if (requestCode == REQUEST_GALLERY_IMAGE) {
                imageUri = data?.data
                val contentResolver = context?.contentResolver

                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                contentResolver?.takePersistableUriPermission(imageUri!!, takeFlags)
            }
            else if(requestCode == REQUEST_IMAGE_CAPTURE)
                imageUri = newPhotoUri

            ivEditPhoto.setImageBitmap(null)
            ivEditPhoto.setImageURI(null)
            ivEditPhoto.setImageURI(imageUri)
        }
        else {
            if(resultCode != AppCompatActivity.RESULT_CANCELED)
                Toast.makeText(context, "Error: invalid result", Toast.LENGTH_SHORT).show()
        }
    }
}