package it.polito.mad.carpoolingapp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Guideline
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.api.IGeoPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.IOException
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class TripEditFragment : Fragment() {

    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_GALLERY_IMAGE = 2
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    private var isLocPermissionEnabled = false
    private var interestedUserList = ArrayList<InterestedUserDetails>()
    private var dayAfter: Boolean = false
    private var day: Int = 1
    private var month: Int = 1
    private var year: Int = 1970
    private lateinit var ivPhoto: ImageView
    private var imageUri: Uri? = null
    private var newPhotoUri: Uri? = null
    private var myList = ArrayList<Stop>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var interestedUserRecyclerView: RecyclerView
    private lateinit var advertiseSwitch: SwitchMaterial
    private lateinit var completeSwitch: SwitchMaterial
    private lateinit var etDate: TextInputEditText
    private lateinit var etDepTime: TextInputEditText
    private lateinit var etArrTime: TextInputEditText
    private lateinit var etDepLoc: TextInputEditText
    private lateinit var etArrLoc: TextInputEditText
    private lateinit var etSeats: TextInputEditText
    private lateinit var etPrice: TextInputEditText
    private lateinit var etDesc: TextInputEditText
    private lateinit var geo: CustomMapView
    private lateinit var depMarker: Marker
    private lateinit var arrMarker: Marker
    private var stopMarkers = ArrayList<Marker>()
    private var n: String = ""
    private var profile: String = ""
    @ExperimentalCoroutinesApi
    private var tripModel = TripViewModel()
    private var isComplete: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        n = arguments?.getString("nTrip", "") ?: ""
        if(n==""){
            profile = FirebaseAuth.getInstance().currentUser?.uid!!
        }
    }

    override fun onPause() {
        super.onPause()
        geo.onPause()
    }

    override fun onResume() {
        super.onResume()
        geo.onResume()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_PERMISSIONS_REQUEST_CODE ->
                isLocPermissionEnabled = grantResults[0] == PackageManager.PERMISSION_GRANTED
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.save_edit_profile_menu, menu)
    }

    @ExperimentalCoroutinesApi
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.itemSave -> {
                if (validateFields()) {
                    saveTrip()
                }
                true
            }
            R.id.itemInfo -> {
                fragmentManager?.let { TutorialDialogFragment(R.string.editDialog, "showEditTutorial").show(it, "EditTutorialDialog") }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_trip_edit, container, false)

        ivPhoto = view.findViewById(R.id.carImage)

        advertiseSwitch = view.findViewById(R.id.advertiseSwitch)
        completeSwitch = view.findViewById(R.id.completeSwitch)

        etDate = view.findViewById(R.id.etDate)
        etDate.setOnClickListener{
            showDatePickerDialog(etDate)
        }
        etDepTime = view.findViewById(R.id.etDepTime)
        etDepTime.setOnClickListener{
            showTimePickerDialog(etDepTime)
        }
        etDepTime.setOnFocusChangeListener { v, hasFocus ->
            if(hasFocus && v is TextInputEditText && v.text.toString()==""){
                showTimePickerDialog(v)
            }
        }

        etArrTime = view.findViewById(R.id.etArrTime)
        etArrTime.setOnClickListener{
            showTimePickerDialog(etArrTime)
        }
        etArrTime.setOnFocusChangeListener { v, hasFocus ->
            if(hasFocus && v is TextInputEditText && v.text.toString()==""){
                showTimePickerDialog(v)
            }
        }

        etDepLoc = view.findViewById(R.id.etDepLoc)
        etArrLoc = view.findViewById(R.id.etArrLoc)
        etSeats = view.findViewById(R.id.etSeats)
        etPrice = view.findViewById(R.id.etPrice)
        etDesc = view.findViewById(R.id.etDesc)

        geo = view.findViewById(R.id.map)


        recyclerView = view.findViewById(R.id.rvStops)
        recyclerView.isNestedScrollingEnabled = false

        val height = Resources.getSystem().displayMetrics.heightPixels
        view.findViewById<Guideline>(R.id.guideline).setGuidelineBegin((height * 0.55).toInt())

        registerForContextMenu(view.findViewById<ImageButton>(R.id.imageButton))


        return view
    }

    private fun initMap(){
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        geo.setTileSource(TileSourceFactory.MAPNIK)
        geo.setTouchable(true)
        val rotationGestureOverlay = RotationGestureOverlay(context, geo)
        rotationGestureOverlay.isEnabled
        geo.setMultiTouchControls(true)
        geo.overlays.add(rotationGestureOverlay)

        if (Build.VERSION.SDK_INT >= 23){
            if (ActivityCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED ){
                isLocPermissionEnabled = false
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_PERMISSIONS_REQUEST_CODE)
            }
            else
                isLocPermissionEnabled = true
        }
        lateinit var startPoint: List<Address>
        if(isLocPermissionEnabled) {
            geo.controller.setZoom(13.5)
            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), geo)
            locationOverlay.enableMyLocation()
            val icon = requireContext().resources.getDrawable(R.drawable.person).toBitmap()
            locationOverlay.setPersonIcon(icon)
            locationOverlay.setDirectionArrow(icon, icon)
            geo.overlays.add(locationOverlay)

            val locationManager = getSystemService(requireContext(), LocationManager::class.java)
            var myLocation: Location? = locationManager!!.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (myLocation == null)
                myLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            if (myLocation != null) {
                startPoint = Geocoder(context).getFromLocation(myLocation.latitude, myLocation.longitude, 1)
            }
            else {
                startPoint = Geocoder(context).getFromLocationName("Rome, Italy", 1)
                geo.controller.setZoom(6.5)
            }
        }
        else {
            startPoint = Geocoder(context).getFromLocationName("Rome, Italy", 1)
            geo.controller.setZoom(6.5)
        }
        // TODO: try on real device if it works
//        val compassOverlay = CompassOverlay(context, InternalCompassOrientationProvider(context), geo)
//        compassOverlay.enableCompass()
//        geo.overlays.add(compassOverlay)

        geo.controller.setCenter(GeoPoint(startPoint[0].latitude, startPoint[0].longitude))
        depMarker = Marker(geo)
        depMarker.isEnabled = false
        arrMarker = Marker(geo)
        arrMarker.isEnabled = false


        geo.overlays.add(object: Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                val projection =  mapView.projection
                val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt())
                Log.d("OSM", "${geoPoint.latitude},${geoPoint.longitude}")

                populateMap(geoPoint.latitude, geoPoint.longitude)
                return true
            }
        })
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun populateMap(lat: Double, lon: Double){
        DialogForTypeOfPosition{
            val builder = AlertDialog.Builder(it)
            builder.setTitle(getString(R.string.chooseLocation))
                .setItems(
                    arrayOf(getString(R.string.depLoc), getString(R.string.stopLocation), getString(R.string.arrLoc))
                ) { _, which ->
                    when (which) {
                        0 -> { // Departure
                            addMarker(lat, lon, depMarker, etDepLoc, dep = true, new = true)
                            // TODO: add setCenter
                            if(!arrMarker.isEnabled)
                                geo.controller.setCenter(depMarker.position)
                        }
                        1 -> { // Stops
                            val n = stopMarkers.size
                            stopMarkers.add(Marker(geo))
                            stopMarkers[n].position = GeoPoint(lat, lon)
                            stopMarkers[n].setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            geo.overlays.add(stopMarkers[n])
                            geo.invalidate()
                            stopMarkers[n].icon = requireContext().resources.getDrawable(R.drawable.stop_marker).apply {
                                //this.setTint(Color.GRAY)
                            }

                            stopMarkers[n].setOnMarkerClickListener { marker, mapView -> deleteMarker(marker, mapView, true) }
                            myList[n].address = Geocoder(context).getFromLocation(lat, lon, 1)[0].getAddressLine(0)
                            myList.add(Stop("", ""))
                            recyclerView.adapter?.notifyItemInserted(n)
                        }
                        2 -> { // Arrival
                            addMarker(lat, lon, arrMarker, etArrLoc, dep = false, new = true)
                            // TODO: add setCenter
                            if(!depMarker.isEnabled)
                                geo.controller.setCenter(arrMarker.position)
                        }
                    }
                    fixZoom(geo.controller, getGeoPointsList(), geo)
                }
            builder.create()
        }.show(childFragmentManager, DialogForTypeOfPosition.TAG)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun addMarker(lat: Double, lon: Double, marker: Marker, et: TextInputEditText, dep: Boolean, new: Boolean){
        marker.position = GeoPoint(lat, lon)
        marker.isEnabled = true
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        geo.overlays.add(marker)
        geo.invalidate()
        marker.icon = requireContext().resources.getDrawable(
            if(dep) R.drawable.departure_marker
            else R.drawable.arrival_marker
        ).apply {
            //this.setTint(Color.GRAY)
        }

        marker.setOnMarkerClickListener { m, mapView ->

            deleteMarker(m, mapView, false, et)
        }
        if(new)
            et.setText(Geocoder(context).getFromLocation(lat, lon, 1)[0].getAddressLine(0))
    }

    private fun deleteMarker(marker: Marker, mapView: MapView, isStop: Boolean, vararg ets:TextInputEditText): Boolean{
        DialogForDeleteMarker { it2 ->
            val builder2 = AlertDialog.Builder(it2)
            builder2.setTitle(getString(R.string.deleteMarker))
                .setPositiveButton(getString(R.string.yesConfirm)){ _, _ ->
                    mapView.overlays.remove(marker)
                    mapView.invalidate()
                    marker.isEnabled = false
                    if(isStop){
                        val pos = stopMarkers.indexOf(marker)
                        stopMarkers.removeAt(pos)
                        myList.removeAt(pos)
                        recyclerView.layoutManager?.removeAllViews()
                        recyclerView.adapter?.notifyDataSetChanged()
                    } else
                        ets[0].setText("")
                }
                .setNegativeButton(getString(R.string.noConfirm)) { _, _ ->}
            builder2.create()
        }.show(childFragmentManager, DialogForDeleteMarker.TAG)

        return true
    }

    @ExperimentalCoroutinesApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pref = activity?.getPreferences(Context.MODE_PRIVATE)
        if(pref != null){
            val show = pref.getBoolean("showEditTutorial", true)
            if(show)
                fragmentManager?.let { TutorialDialogFragment(R.string.editDialog, "showEditTutorial").show(it, "EditTutorialDialog") }
        }


        initMap()
        // If new trip initialize, if edit existing one retrieve values
        if(n == ""){
            myList = arrayListOf(Stop("", ""))

            recyclerView.layoutManager = LinearLayoutManager(this.context)
            recyclerView.adapter = StopAdapter(myList, stopMarkers, geo, requireContext(), childFragmentManager, recyclerView, ::showTimePickerDialog, ::getGeoPointsList)
        }

        else{
            val factory = TripViewModelFactory(n, "")
            tripModel = ViewModelProviders.of(this, factory).get(TripViewModel::class.java)
            tripModel.trip.observe(viewLifecycleOwner){
                restoreTrip(it)
                if (it.has("completed"))
                    isComplete = it.getBoolean("completed")
                if (isComplete) {
                    completeSwitch.isChecked = true
                    completeSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
                        Snackbar.make(view, R.string.tripIsCompleteWarning, Snackbar.LENGTH_SHORT).show()
                        completeSwitch.isChecked = true
                    }
                }
                recyclerView.layoutManager = LinearLayoutManager(this.context)
                recyclerView.adapter = StopAdapter(myList, stopMarkers, geo, requireContext(), childFragmentManager, recyclerView, ::showTimePickerDialog, ::getGeoPointsList)
            }

            tripModel.bookingslist.observe(viewLifecycleOwner){ it2 ->
                interestedUserList = ArrayList(it2)
                interestedUserRecyclerView = view.findViewById(R.id.rvInterestedUsers)
                interestedUserRecyclerView.isNestedScrollingEnabled = false
                interestedUserRecyclerView.layoutManager = LinearLayoutManager(this.context)
                interestedUserRecyclerView.adapter = InterestedUserAdapter(interestedUserList, findNavController(), this, requireContext())

            }
        }



        etDepLoc.setOnFocusChangeListener { _, hasFocus ->
            if(!hasFocus && etDepLoc.text.toString() != "") {
                val pos = Geocoder(context).getFromLocationName(etDepLoc.text.toString(), 1)
                if (pos.isNotEmpty()) {
                    addMarker(pos[0].latitude, pos[0].longitude, depMarker, etDepLoc, dep = true, new = true)
                    // TODO: add setCenter
                    fixZoom(geo.controller, getGeoPointsList(), geo)
                }
                else{
                    Toast.makeText(context, R.string.errorOnAddress, Toast.LENGTH_SHORT).show()
                    etDepLoc.setText("")
                }
            }
            else if(!hasFocus && etDepLoc.text.toString() == "" &&  depMarker.isEnabled){
                geo.overlays.remove(depMarker)
                geo.invalidate()
                depMarker.isEnabled = false
            }
        }
        etArrLoc.setOnFocusChangeListener { _, hasFocus ->
            if(!hasFocus && etArrLoc.text.toString() != "") {
                val pos = Geocoder(context).getFromLocationName(etArrLoc.text.toString(), 1)
                if (pos.isNotEmpty()) {
                    addMarker(pos[0].latitude, pos[0].longitude, arrMarker, etArrLoc, dep = false, new = true)
                    // TODO: add setCenter
                    fixZoom(geo.controller, getGeoPointsList(), geo)
                }
                else{
                    Toast.makeText(context, R.string.errorOnAddress, Toast.LENGTH_SHORT).show()
                    etArrLoc.setText("")
                }
            }
            else if(!hasFocus && etArrLoc.text.toString() == "" &&  arrMarker.isEnabled){
                geo.overlays.remove(arrMarker)
                geo.invalidate()
                arrMarker.isEnabled = false
            }
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

    @RequiresApi(Build.VERSION_CODES.KITKAT)
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

            ivPhoto.setImageBitmap(null)
            ivPhoto.setImageURI(null)
            ivPhoto.setImageURI(imageUri)
        }
        else {
            if(resultCode != AppCompatActivity.RESULT_CANCELED)
                Toast.makeText(context, "Error: invalid result", Toast.LENGTH_SHORT).show()
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
                "JPEG_${timeStamp}_", /* prefix */
                ".jpg", /* suffix */
                storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            newPhotoUri = absolutePath.toUri()
        }
    }

    @ExperimentalCoroutinesApi
    private fun saveTrip(){

        val json = HashMap<String, Any>()

        json["advertised"] = advertiseSwitch.isChecked
        json["completed"] = completeSwitch.isChecked
        json["depDay"] = day.toString()
        json["depMonth"] = month.toString()
        json["depYear"] = year.toString()
        json["depLoc"] = etDepLoc.text.toString()
        json["depTime"] = etDepTime.text.toString()
        json["arrLoc"] = etArrLoc.text.toString()
        json["arrTime"] = etArrTime.text.toString()
        json["stopsArray"] = createJsonStopArray()
        json["seats"] = etSeats.text.toString()
        json["price"] = etPrice.text.toString()
        json["desc"] = etDesc.text.toString()
        json["profileId"] = profile
        json["dayAfter"] = dayAfter

        for(u in interestedUserList) {
            tripModel.setBookingState(n, u.id, bookState.valueOf(u.state), u.profileId)
            tripModel.setTripComplete(n, u.profileId, advertiseSwitch.isChecked)
        }

        if (!advertiseSwitch.isChecked)
            tripModel.deleteAllBookings()

        if (imageUri != null) {
            val file: Uri = if (imageUri!!.isRelative) Uri.fromFile(File(imageUri.toString()))
                            else imageUri!!
            tripModel.setTrip(n,
                json,
                { it: DocumentReference? ->
                    if (file != null) {
                        val storage = Firebase.storage.reference.child("/tripImages/${it?.id ?: n}")
                        storage.putFile(file)
                            .addOnSuccessListener { _ ->
                                findNavController().navigate(
                                    R.id.action_tripEditFragment_to_tripDetailsFragment,
                                    bundleOf("nTrip" to (it?.id ?: n),
                                    "owned" to true)
                                )
                                Snackbar.make(
                                    requireView(),
                                    getString(R.string.snackTripSaved),
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                            .addOnFailureListener { err: Exception ->
                                Log.d("dbg", err.message.toString())
                                Toast.makeText(
                                    this.context,
                                    R.string.daoSetProfileError,
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
                })
        }
        else
            tripModel.setTrip(
                n,
                json,
                {
                    findNavController().navigate(
                        R.id.action_tripEditFragment_to_tripDetailsFragment,
                        bundleOf("nTrip" to (it?.id ?: n),
                        "owned" to true)
                    )
                    Snackbar.make(
                        requireView(),
                        getString(R.string.snackTripSaved),
                        Snackbar.LENGTH_SHORT
                    ).show()
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

    private fun restoreTrip(json: JSONObject){

        profile = json.getString("profileId")

        // Image
        if(isAdded){
            val storageReference = Firebase.storage.reference.child("/tripImages/${n}")
            storageReference.metadata.addOnSuccessListener {
                GlideApp.with(this)
                    .load(storageReference)
                    .signature(ObjectKey(it.updatedTimeMillis.toString()))
                    .into(ivPhoto)
            }.addOnFailureListener{
                Log.d("dbg", "Profile picture download error")
            }
        }


        // Departure date
        day = json.get("depDay").toString().toInt()
        month = json.get("depMonth").toString().toInt()
        year = json.get("depYear").toString().toInt()
        etDate.setText(getString(R.string.dateFormat, day, DateFormatSymbols().months[month], year))

        // Departure location and time
        etDepLoc.setText(json.get("depLoc").toString())
        val dep = Geocoder(context).getFromLocationName(json.get("depLoc").toString(), 1)
        addMarker(dep[0].latitude, dep[0].longitude, depMarker, etDepLoc, dep = true, new = false)
        etDepTime.setText(json.get("depTime").toString())

        // Arrival location and time
        etArrLoc.setText(json.get("arrLoc").toString())
        val arr = Geocoder(context).getFromLocationName(json.get("arrLoc").toString(), 1)
        addMarker(arr[0].latitude, arr[0].longitude, arrMarker, etArrLoc, dep = false, new = false)
        etArrTime.setText(json.get("arrTime").toString())

        // List of stops
        if(json.get("stopsArray")!="" && json.get("stopsArray")!="{}" && json.get("stopsArray")!="[]")
            restoreJsonStopArray(JSONArray(json.get("stopsArray").toString()))
        else
            myList = arrayListOf(Stop("", ""))

        // Other information
        etSeats.setText(json.get("seats").toString())
        etPrice.setText(json.get("price").toString())
        etDesc.setText(json.get("desc").toString())

        advertiseSwitch.isChecked = json.getBoolean("advertised")

        //TODO: zoom con points già presenti
        fixZoom(geo.controller, getGeoPointsList(), geo)
    }

    private fun getGeoPointsList(): ArrayList<IGeoPoint>{
        val points = ArrayList<IGeoPoint>()
        if(depMarker.isEnabled)
            points.add(depMarker.position)
        if(arrMarker.isEnabled)
            points.add(arrMarker.position)
        stopMarkers.forEach { points.add(it.position) }
        return points
    }

    private fun createJsonStopArray(): String{
        val jsonArr = JSONArray()
        for(item in myList){
            val jsonObj = JSONObject()
                    .put("addr", item.address)
                    .put("time", item.time)
            jsonArr.put(jsonObj.toString())
        }
        return jsonArr.toString()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun restoreJsonStopArray(jsonArr: JSONArray) {
        myList.clear()

        for (idx in (0 until jsonArr.length())) {
            val jsonObj = JSONObject(jsonArr.getString(idx))
            myList.add(Stop(jsonObj.getString("addr"), jsonObj.getString("time")))
            if(jsonObj.getString("addr") != "") {
                val stop = Geocoder(context).getFromLocationName(jsonObj.getString("addr"), 1)
                stopMarkers.add(Marker(geo))
                stopMarkers[idx].position = GeoPoint(stop[0].latitude, stop[0].longitude)
                stopMarkers[idx].setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                geo.overlays.add(stopMarkers[idx])
                geo.invalidate()
                stopMarkers[idx].icon =
                    requireContext().resources.getDrawable(R.drawable.stop_marker).apply {
                        //this.setTint(Color.GRAY)
                    }
                stopMarkers[idx].setOnMarkerClickListener { marker, mapView -> deleteMarker(marker, mapView, true) }
            }
        }
    }

    private fun validateFields(): Boolean{

        val array = ArrayList<Pair<TextInputEditText, TextInputLayout>>()
        var arrTimeT: TimeT
        var depTimeT: TimeT
        var emptyFlag = false
        var timeFlag = false
        var invalidTimeFormat = false
        var availableSeats = -1
        var confirmedSeats = 0



        // Empty fields checks
        array.add(Pair(etDate, view?.findViewById(R.id.tiDate)!!))
        array.add(Pair(etDepLoc, view?.findViewById(R.id.tiDepLoc)!!))
        array.add(Pair(etDepTime, view?.findViewById(R.id.tiDepTime)!!))
        array.add(Pair(etArrLoc, view?.findViewById(R.id.tiArrLoc)!!))
        array.add(Pair(etArrTime, view?.findViewById(R.id.tiArrTime)!!))
        array.add(Pair(etSeats, view?.findViewById(R.id.tiSeats)!!))
        array.add(Pair(etPrice, view?.findViewById(R.id.tiPrice)!!))

        array.forEach { item ->
            clearError(item.second)
            if(item.first.text.toString() == ""){
                setError(item.second)
                item.first.doAfterTextChanged {
                    clearError(item.second)
                }
                emptyFlag = true
            }
        }

        for(i in (1 until myList.size)){
            val tiStop = view?.findViewWithTag<TextInputLayout>("Stop$i")
            val etStop = view?.findViewWithTag<TextInputEditText>("etStop$i")
            val tiStopTime = view?.findViewWithTag<TextInputLayout>("StopTime$i")
            val etStopTime = view?.findViewWithTag<TextInputEditText>("etStopTime$i")

            clearError(tiStopTime!!)

            if(etStop?.text.toString()==""){
                emptyFlag = true
                setError(tiStop!!)
                etStop?.doAfterTextChanged {
                    clearError(tiStop) }
            }

            if(etStopTime?.text.toString()=="") {
                emptyFlag = true
                setError(tiStopTime)
                etStopTime?.doAfterTextChanged {
                    clearError(tiStopTime) }
            }
        }

        if(emptyFlag){
            Toast.makeText(activity, R.string.emptyFields, Toast.LENGTH_LONG).show()
            return false
        }

        arrTimeT = TimeT.parseTimeT(etArrTime.text.toString())
        depTimeT = TimeT.parseTimeT(etDepTime.text.toString())
        availableSeats = etSeats.text.toString().toInt()

        // Check on booked people
        for(u in interestedUserList)
            if(u.state == bookState.CONFIRMED.name)
                confirmedSeats++

        Log.d("DBG", "$availableSeats $confirmedSeats")
        if(confirmedSeats > availableSeats){
            Toast.makeText(requireContext(), R.string.exceededSeats, Toast.LENGTH_LONG).show()
            return false
        }


        if (arrTimeT.hours == -1) {
            val tmp = view?.findViewById<TextInputLayout>(R.id.tiArrTime)!!
            setError(tmp)
            etArrTime.setText("")
            etArrTime.doAfterTextChanged {
                clearError(tmp)
            }
            invalidTimeFormat = true
        }

        if (depTimeT.hours == -1) {
            val tmp = view?.findViewById<TextInputLayout>(R.id.tiDepTime)!!
            setError(tmp)
            etDepTime.setText("")
            etDepTime.doAfterTextChanged {
                clearError(tmp)
            }
            invalidTimeFormat = true
        }

        if(invalidTimeFormat){
            Toast.makeText(activity, R.string.invalidTimeFormat, Toast.LENGTH_LONG).show()
            return false
        }

        if(depTimeT > arrTimeT)
            timeFlag = true

        var etStopTime = view?.findViewWithTag<TextInputEditText>("etStopTime1")
        var curTimeT = TimeT.parseTimeT(etStopTime?.text.toString())
        var tiStopTime = view?.findViewWithTag<TextInputLayout>("StopTime1")

        if(myList.size>1){
            if(!timeFlag){
                if ((curTimeT < depTimeT) || (curTimeT > arrTimeT)) {
                    Toast.makeText(activity, R.string.timeOutOfBounds, Toast.LENGTH_LONG).show()
                    setError(tiStopTime!!)
                    etStopTime?.doAfterTextChanged {
                        clearError(tiStopTime!!)
                    }
                    return false
                }
            }
            else{
                if ((curTimeT < depTimeT) && (curTimeT > arrTimeT)) {
                    Toast.makeText(activity, R.string.timeOutOfBounds, Toast.LENGTH_LONG).show()
                    setError(tiStopTime!!)
                    etStopTime?.doAfterTextChanged {
                        clearError(tiStopTime!!)
                    }
                    return false
                }
            }
        }


        if ((myList.size > 1) && (curTimeT.hours == -1)) {
            setError(tiStopTime!!)
            etStopTime?.setText("")
            etStopTime?.doAfterTextChanged {
                clearError(tiStopTime!!)
            }
            Toast.makeText(activity, R.string.invalidTimeFormat, Toast.LENGTH_LONG).show()
            return false
        }

        if(myList.size > 2) {

            // Stops' times check
            for (i in (2 until myList.size)) {
                etStopTime = view?.findViewWithTag("etStopTime$i")
                val etPrevStopTime = view?.findViewWithTag<TextInputEditText>("etStopTime${i - 1}")
                val prevTimeT = TimeT.parseTimeT(view?.findViewWithTag<TextInputEditText>("etStopTime${i - 1}")?.text.toString())
                curTimeT = TimeT.parseTimeT(etStopTime?.text.toString())
                tiStopTime = view?.findViewWithTag("StopTime$i")
                val tiPrevStopTime = view?.findViewWithTag<TextInputLayout>("StopTime${i - 1}")

                if (curTimeT.hours == -1) {
                    setError(tiStopTime!!)
                    etStopTime?.setText("")
                    etStopTime?.doAfterTextChanged {
                        clearError(tiStopTime)
                    }
                    Toast.makeText(activity, R.string.invalidTimeFormat, Toast.LENGTH_LONG).show()
                    return false
                }

                if (!timeFlag) {
                    if (curTimeT < prevTimeT) {
                        Toast.makeText(activity, R.string.stopsNotSubsequent, Toast.LENGTH_LONG).show()
                        setError(tiStopTime!!)
                        etStopTime?.doAfterTextChanged {
                            clearError(tiStopTime)
                            clearError(tiPrevStopTime!!)
                        }

                        setError(tiPrevStopTime!!)
                        etPrevStopTime?.doAfterTextChanged {
                            clearError(tiPrevStopTime)
                            clearError(tiStopTime)
                        }
                        return false
                    }

                    if ((curTimeT < depTimeT) || (curTimeT > arrTimeT)) {
                        Toast.makeText(activity, R.string.timeOutOfBounds, Toast.LENGTH_LONG).show()
                        setError(tiStopTime!!)
                        etStopTime?.doAfterTextChanged {
                            clearError(tiStopTime)
                        }
                        return false
                    }
                } else {
                    if (((curTimeT > depTimeT) && (curTimeT < TimeT(23, 59))) &&
                            ((prevTimeT > depTimeT) && (prevTimeT < TimeT(23, 59))) && (curTimeT < prevTimeT)) {
                        Toast.makeText(activity, R.string.stopsNotSubsequent, Toast.LENGTH_LONG).show()
                        setError(tiStopTime!!)
                        etStopTime?.doAfterTextChanged {
                            clearError(tiStopTime)
                            clearError(tiPrevStopTime!!)
                        }

                        setError(tiPrevStopTime!!)
                        etPrevStopTime?.doAfterTextChanged {
                            clearError(tiPrevStopTime)
                            clearError(tiStopTime)
                        }
                        return false
                    }

                    if (((curTimeT > depTimeT) && (curTimeT < TimeT(23, 59))) &&
                            ((prevTimeT < arrTimeT) && (prevTimeT > TimeT(0, 0)))) {
                        Toast.makeText(activity, R.string.stopsNotSubsequent, Toast.LENGTH_LONG).show()
                        setError(tiStopTime!!)
                        etStopTime?.doAfterTextChanged {
                            clearError(tiStopTime)
                            clearError(tiPrevStopTime!!)
                        }

                        setError(tiPrevStopTime!!)
                        etPrevStopTime?.doAfterTextChanged {
                            clearError(tiPrevStopTime)
                            clearError(tiStopTime)
                        }
                        return false
                    }

                    if (((curTimeT < arrTimeT) && (curTimeT > TimeT(0, 0))) &&
                            ((prevTimeT < arrTimeT) && (prevTimeT > TimeT(0, 0))) && (curTimeT < prevTimeT)) {
                        Toast.makeText(activity, R.string.stopsNotSubsequent, Toast.LENGTH_LONG).show()
                        setError(tiStopTime!!)
                        etStopTime?.doAfterTextChanged {
                            clearError(tiStopTime)
                            clearError(tiPrevStopTime!!)
                        }

                        setError(tiPrevStopTime!!)
                        etPrevStopTime?.doAfterTextChanged {
                            clearError(tiPrevStopTime)
                            clearError(tiStopTime)
                        }
                        return false
                    }

                    if ((curTimeT < depTimeT) && (curTimeT > arrTimeT)) {
                        Toast.makeText(activity, R.string.timeOutOfBounds, Toast.LENGTH_LONG).show()
                        setError(tiStopTime!!)
                        etStopTime?.doAfterTextChanged {
                            clearError(tiStopTime)
                        }
                        return false
                    }
                }
            }
        }
        /**
         * con timeflag true: stop(i) < arrTime
         * sempre tutti gli orari: depTime < stop(i) < arrTime
         * sempre(primo caso falso setta timeflag): stop(i-1) < stop(i)
         */
        if(timeFlag)
            dayAfter = true

        return true
    }

    private fun clearError(view: TextInputLayout){
        view.error = null
        view.isErrorEnabled = false
    }

    private  fun setError(view: TextInputLayout){
        view.isErrorEnabled = true
        view.error = " "

    }

    private fun showDatePickerDialog(editText: TextInputEditText) {
        val c: Calendar = Calendar.getInstance()
        val cday = c.get(Calendar.DAY_OF_MONTH)
        val cmonth = c.get(Calendar.MONTH)
        val cyear = c.get(Calendar.YEAR)

        val datePicker = DatePickerDialog(activity as Context, R.style.SpinnerDatePickerDialog, { _: DatePicker?, selectedYear: Int, selectedMonth: Int, selectedDay: Int ->
            editText.setText(getString(R.string.dateFormat, selectedDay, DateFormatSymbols().months[selectedMonth], selectedYear))
            day = selectedDay
            month = selectedMonth
            year = selectedYear
        }, cyear, cmonth, cday)

        datePicker.setTitle(getString(R.string.pickDate))
        datePicker.show()
    }

     private fun showTimePickerDialog(editText: TextInputEditText) {
        val prevTime = TimeT.parseTimeT(editText.text.toString())
        val c: Calendar = Calendar.getInstance()
        val hour = if(editText.text.toString() != "") prevTime.hours else c.get(Calendar.HOUR_OF_DAY)
        val minute = if(editText.text.toString() != "") prevTime.minutes else c.get(Calendar.MINUTE)

        val timePicker = TimePickerDialog(activity as Context, R.style.SpinnerTimePicker,
                { _: TimePicker?, selectedHour: Int, selectedMinute: Int ->
                    editText.setText(getString(R.string.timeFormat, selectedHour, selectedMinute))
                }, hour, minute, true)

        timePicker.setTitle(getString(R.string.pickTime))
        timePicker.show()
    }

}


class InterestedUserAdapter(private val data: ArrayList<InterestedUserDetails>, private val nav: NavController, private val act: Fragment, private val context: Context): RecyclerView.Adapter<InterestedUserAdapter.InterestedUserViewHolder>() {
    private val ctx = context

    private val updateState = { pos: Int, state: String ->
        data[pos].state = state
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InterestedUserViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.interested_user_layout, parent, false)

        return InterestedUserViewHolder(v, act, ctx)
    }

    override fun onBindViewHolder(holder: InterestedUserViewHolder, position: Int) {
        val u = data[position]
        holder.bind(u, position + 1, updateState)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    class InterestedUserViewHolder(view: View, private val act: Fragment, private val ctx: Context): RecyclerView.ViewHolder(view) {
        private val name = view.findViewById<TextView>(R.id.tvInterestedUserName)
        private val state = view.findViewById<Chip>(R.id.interestedUserState)
        private val image = view.findViewById<ImageView>(R.id.ivInterestedUser)
        private val radio = view.findViewById<RadioGroup>(R.id.radioGroup)
        private val confirmed = view.findViewById<RadioButton>(R.id.radioConfirmed)
        private val rejected = view.findViewById<RadioButton>(R.id.radioRejected)
        private val suspended = view.findViewById<RadioButton>(R.id.radioSuspended)

        fun bind(interestedUser: InterestedUserDetails, pos: Int, updateState: (pos: Int, state: String) -> Unit) {
            name.text = interestedUser.name
            when(interestedUser.state){
                bookState.CONFIRMED.name ->{
                    state.text = act.getString(R.string.confirmed)
                    confirmed.isChecked = true
                    state.setChipBackgroundColorResource(R.color.confirmed)
                }
                bookState.REJECTED.name ->{
                    state.text = act.getString(R.string.rejected)
                    rejected.isChecked = true
                    state.setChipBackgroundColorResource(R.color.rejected)
                }
                bookState.PENDING.name ->{
                    state.text = act.getString(R.string.pending)
                    suspended.isChecked = true
                    state.setChipBackgroundColorResource(R.color.pending)
                }
            }

            val profileRef = Firebase.storage.reference.child("/profileImages/${interestedUser.profileId}.jpg")
            profileRef.metadata.addOnSuccessListener { meta ->
                GlideApp.with(act)
                    .load(profileRef)
                    .signature(ObjectKey(meta.updatedTimeMillis.toString()))
                    .into(image)
            }.addOnFailureListener {
                Log.d("dbg", "Profile picture download error")
            }

            //TODO: controllare stato di conferma appropriatamente
            //If trip has been confirmed previously, seller can't revoke their committal
            //if (interestedUser.state == bookState.CONFIRMED.name)
             //   radio.children.forEach { it.isEnabled = false }


            radio.setOnCheckedChangeListener { it, _ ->
                if (it is RadioGroup) {
                    // Check which radio button was clicked
                    when (it.checkedRadioButtonId) {
                        R.id.radioConfirmed -> {
                            //AlertDialog.Builder(ctx)
                               // .setPositiveButton(R.string.yesConfirm) { dialogInterface: DialogInterface, i: Int ->
                                    updateState(pos-1, bookState.CONFIRMED.name)
                               // }
                               // .setNegativeButton(R.string.noConfirm) { dialogInterface: DialogInterface, i: Int ->
                                //    dialogInterface.dismiss()
                              //  }
                               // .setMessage(R.string.areYouSure).show()
                        }
                        R.id.radioRejected -> {
                            updateState(pos-1, bookState.REJECTED.name)
                        }
                        R.id.radioSuspended -> {
                            updateState(pos-1, bookState.PENDING.name)
                        }
                    }
                }
            }
        }
    }
}

class Stop(var address: String, var time: String)

class StopAdapter(private val data: ArrayList<Stop>, private val stopMarkers: ArrayList<Marker>, private val geo: CustomMapView, private val c: Context, private val cfm: FragmentManager, val recyclerView: RecyclerView, private val timePick: (TextInputEditText) -> Unit, private val getGeoPointsList: () -> ArrayList<IGeoPoint>): RecyclerView.Adapter<StopAdapter.StopViewHolder>() {

    @SuppressLint("UseCompatLoadingForDrawables")
    private val addMarker = { lat: Double, lon: Double, n: Int ->
        stopMarkers.add(Marker(geo))
        stopMarkers[n].position = GeoPoint(lat, lon)
        stopMarkers[n].setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        geo.overlays.add(stopMarkers[n])
        geo.invalidate()
        stopMarkers[n].icon = c.resources.getDrawable(R.drawable.stop_marker).apply {
            //this.setTint(Color.GRAY)
        }

        stopMarkers[n].setOnMarkerClickListener { marker, mapView -> deleteMarker(marker, mapView) }
        data[n].address = Geocoder(c).getFromLocation(lat, lon, 1)[0].getAddressLine(0)
        fixZoom(geo.controller, getGeoPointsList(), geo)
    }

    private fun deleteMarker(marker: Marker, m: MapView): Boolean{
        DialogForDeleteMarker { it2 ->
            val builder2 = AlertDialog.Builder(it2)
            builder2.setTitle(c.getString(R.string.deleteMarker))
                .setPositiveButton(R.string.yesConfirm){ _, _ ->
                    m.overlays.remove(marker)
                    m.invalidate()
                    val pos = stopMarkers.indexOf(marker)
                    stopMarkers.removeAt(pos)
                    deleteStop(pos)
                }
                .setNegativeButton(R.string.noConfirm) { _, _ ->}
            builder2.create()
        }.show(cfm, DialogForTypeOfPosition.TAG)

        return true
    }

    private val addStop = {
        data.add(Stop("", ""))
        notifyItemInserted(data.size - 1)
    }

    private val saveStop = { pos: Int, stop: String, time: String ->
        data[pos].address = stop
        data[pos].time = time
    }

    private val deleteStop = { pos: Int ->
        data.removeAt(pos)
        recyclerView.layoutManager?.removeAllViews()
        notifyDataSetChanged()
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StopViewHolder {
        val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.add_stop_layout, parent, false)

        return StopViewHolder(v)
    }

    override fun onBindViewHolder(holder: StopViewHolder, position: Int) {
        val u = data[position]
        holder.bind(u, position + 1, addStop, saveStop, deleteStop, timePick, addMarker, c)
    }

    override fun getItemCount(): Int {
        return data.size
    }


    class StopViewHolder(val view: View): RecyclerView.ViewHolder(view) {
        private val tiStop = view.findViewById<TextInputLayout>(R.id.tiStop)
        private val etStop = view.findViewById<TextInputEditText>(R.id.etStop)
        private val etStopTime = view.findViewById<TextInputEditText>(R.id.etStopTime)
        private val tiStopTime = view.findViewById<TextInputLayout>(R.id.tiStopTime)

        fun bind(stop: Stop, pos: Int,
                 add: () -> Unit,
                 save: (pos: Int, stop: String, time: String) -> Unit,
                 del: (Int) -> Unit,
                 timePick: (TextInputEditText) -> Unit,
                 addMarker: (Double, Double, Int) -> Unit,
                 c: Context){
            etStop.tag = "etStop$pos"
            etStopTime.tag = "etStopTime$pos"
            tiStopTime.tag = "StopTime$pos"
            tiStop.tag = "Stop$pos"

            etStopTime.setOnClickListener{
                timePick(etStopTime)
            }

            etStopTime.setOnFocusChangeListener { v, hasFocus ->
                if(hasFocus && v is TextInputEditText && v.text.toString()==""){
                    timePick(v)
                }
            }

            etStop.setOnFocusChangeListener { _, hasFocus ->
                if(!hasFocus && etStop.text.toString() != ""){
                    val p = Geocoder(c).getFromLocationName(etStop.text.toString(), 1)
                    if(p.isNotEmpty()) {
                        addMarker(p[0].latitude, p[0].longitude, pos - 1)
                        etStop.setText(p[0].getAddressLine(0))
                    }
                    else{
                        Toast.makeText(c, R.string.errorOnAddress, Toast.LENGTH_SHORT).show()
                        etStop.setText("")
                    }
                }
            }

            val tw = object : TextWatcher{
                var firstMod : Boolean = true

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    if(firstMod){
                        firstMod = false
                        tiStop.hint = tiStop.tag.toString()
                        add()
                    }
                    save(pos - 1, etStop.text.toString(), etStopTime.text.toString())
                    if(etStop.text.toString() == "" && etStopTime.text.toString() == ""){
                        del(pos - 1)
                    }
                }
            }

            if(stop.address != ""){
                etStop.setText(stop.address)
                tiStop.hint = tiStop.tag.toString()
                tw.firstMod = false
            }
            if(stop.time != ""){
                etStopTime.setText(stop.time)
                tiStop.hint = tiStop.tag.toString()
                tw.firstMod = false
            }

            etStop.addTextChangedListener(tw)
            etStopTime.addTextChangedListener(tw)
        }
    }
}

class DialogForTypeOfPosition(private val callback: (FragmentActivity) -> Dialog)
    : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            callback(it)
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    companion object {
        const val TAG = "PurchaseConfirmationDialog"
    }
}

class DialogForDeleteMarker(private val callback: (FragmentActivity) -> Dialog)
    : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            callback(it)
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    companion object {
        const val TAG = "PurchaseConfirmationDialog"
    }
}

class TutorialDialogFragment(val desc: Int, val key: String) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.setTitle("Tutorial")
                .setMessage(desc)
                .setPositiveButton(R.string.dialogDontShow,
                ) { _, _ ->
                    val pref = activity?.getPreferences(Context.MODE_PRIVATE)
                    if (pref != null) {
                        with(pref.edit()) {
                            putBoolean(key, false)
                            apply()
                        }
                    }
                }
                .setNeutralButton("Ok"
                ) { _, _ ->
                    val pref = activity?.getPreferences(Context.MODE_PRIVATE)
                    if (pref != null) {
                        with(pref.edit()) {
                            putBoolean(key, true)
                            apply()
                        }
                    }
                }


            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}