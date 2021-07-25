@file:Suppress("DEPRECATION")

package it.polito.mad.carpoolingapp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.graphics.PorterDuff
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.*
import android.widget.*
import androidx.constraintlayout.widget.Guideline
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.api.IGeoPoint
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.OverlayItem
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import java.text.DateFormatSymbols


class TripDetailsFragment : Fragment() {

    private var owned = false
    private var confirmed = false
    private var myList = ArrayList<StopDetails>()
    private var interestedUserDetailsList = ArrayList<InterestedUserDetails>()
    private var day: Int = 1
    private var month: Int = 1
    private var year: Int = 1970
    private var n: String = ""
    private lateinit var recyclerView: RecyclerView
    private lateinit var interestedUsersDetailsRecyclerView: RecyclerView
    private lateinit var ivPhoto: ImageView
    private lateinit var tvDate: TextView
    private lateinit var etDepTime: TextInputLayout
    private lateinit var etArrTime: TextInputLayout
    private lateinit var etDepLoc: TextInputEditText
    private lateinit var etArrLoc: TextInputEditText
    private lateinit var tvDuration: TextView
    private lateinit var tvSeats: TextView
    private lateinit var tvPrice: TextView
    private lateinit var etDesc: TextInputEditText
    private lateinit var cardImage: ImageView
    private lateinit var cardImageFrame: FrameLayout
    private lateinit var cardName: TextView
    private lateinit var card: MaterialCardView
    @ExperimentalCoroutinesApi
    private lateinit var tripModel : TripViewModel
    @ExperimentalCoroutinesApi
    private lateinit var userModel : UserProfileViewModel
    private var interest = true
    private lateinit var fabNotify: FloatingActionButton
    private var auth: FirebaseAuth? = FirebaseAuth.getInstance()
    private lateinit var map: CustomMapView
    private val items = ArrayList<OverlayItem>()
    private var isUserInterested = MutableLiveData<Boolean>()
    private var isComplete: Boolean = false
    private lateinit var tripOwner: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        n = arguments?.getString("nTrip", "") ?: ""
        owned = arguments?.getBoolean("owned") == true
        setHasOptionsMenu(true)

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.edit_show_profile_menu, menu)

        if (!owned)
            menu.removeItem(R.id.itemEdit)

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.itemEdit -> {
                findNavController().navigate(R.id.action_tripDetailsFragment_to_tripEditFragment, bundleOf("nTrip" to n))
                true
            }
            R.id.itemInfo -> {
                fragmentManager?.let { TutorialDialogFragment(R.string.detailsDialog, "showDetailsTutorial").show(it, "DetailsTutorialDialog") }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_trip_details, container, false)

        val pref = activity?.getPreferences(Context.MODE_PRIVATE)
        if(pref != null){
            val show = pref.getBoolean("showDetailsTutorial", true)
            if(show)
                fragmentManager?.let { TutorialDialogFragment(R.string.detailsDialog, "showDetailsTutorial").show(it, "DetailsTutorialDialog") }
        }

        // Finding all the views
        ivPhoto = view.findViewById(R.id.carImage)
        tvDate = view.findViewById(R.id.tvDate)
        etDepLoc = view.findViewById(R.id.etDepLoc)
        etDepTime = view.findViewById(R.id.tiDepTime)
        etArrLoc = view.findViewById(R.id.etArrLoc)
        etArrTime = view.findViewById(R.id.tiArrTime)
        tvDuration = view.findViewById(R.id.tvDuration)
        tvSeats = view.findViewById(R.id.tvSeats)
        tvPrice = view.findViewById(R.id.tvPrice)
        etDesc = view.findViewById(R.id.etDesc)
        cardImage = view.findViewById(R.id.ivProfile)
        cardName = view.findViewById(R.id.tvProfileName)
        card = view.findViewById(R.id.cvProfile)
        cardImageFrame = view.findViewById(R.id.flDriverProfile)


        recyclerView = view.findViewById(R.id.rvStops)
        recyclerView.isNestedScrollingEnabled = false

        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        map = view.findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)

        map.setOnTouchListener { _, event ->
            if(event.actionMasked == MotionEvent.ACTION_UP && (event.eventTime - event.downTime)<700)
                findNavController().navigate(R.id.action_tripDetailsFragment_to_tripRoute, bundleOf("geoPoints" to items))
            true
        }

        val height = Resources.getSystem().displayMetrics.heightPixels
        view.findViewById<Guideline>(R.id.guideline).setGuidelineBegin((height * 0.33).toInt())

        return view
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    @ExperimentalCoroutinesApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spinner = initSpinner(layoutInflater)
        spinner.show(childFragmentManager, "PurchaseConfirmationDialog")

        var ownerName = ""

        fabNotify = view.findViewById(R.id.fab_notify)
        fabNotify.bringToFront()

        val factory = TripViewModelFactory(n, "")
        tripModel = ViewModelProviders.of(this, factory).get(TripViewModel::class.java)
        tripModel.map.observe(viewLifecycleOwner) { map ->
            //If map returned is empty, display message
            if (map.toString() == "{}") {
                Toast.makeText(context, R.string.tripNotFound, Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
            }
            else {
                //if trip belongs to current user, hide fab
                if (owned) {
                    fabNotify.isVisible = false
                }//else set up its behaviour
                else setUpFab()
                //retrieve all bookings for current trip
                val us = map["bookingslist"]
                interestedUserDetailsList.clear()
                if (us is ArrayList<*>) {
                    for (u in us) {
                        val user = u as InterestedUserDetails
                        if (user.profileId == auth?.currentUser?.uid) {
                            //if a booking of the current user for current trip is found, check whether booking was confirmed or trip was completed, set flag
                            confirmed = user.state == bookState.CONFIRMED.name || user.state == bookState.COMPLETED.name
                        }
                        interestedUserDetailsList.add(user)
                        Log.d("aaaa", u.name)
                    }
                    //disable fab (that lets the user (un)subscribe to this trip) when trip has already been confirmed for current user or completed
                    if (confirmed) {
                        fabNotify.isEnabled = false
                        fabNotify.bringToFront()

                    }
                }

                val it = map["trip"]
                Log.d("DBG aaaaa", it.toString())
                if (it is JSONObject) {
                    restoreTrip(it)

                    // Card
                    val profileID = it.get("profileId").toString()
                    ownerName = it.get("cardName").toString()
                    cardName.text = ownerName
                    val profileRef =
                        Firebase.storage.reference.child("/profileImages/${profileID}.jpg")
                    profileRef.metadata.addOnSuccessListener { meta ->
                        if (isAdded) {
                            GlideApp.with(this)
                                .load(profileRef)
                                .signature(ObjectKey(meta.updatedTimeMillis.toString()))
                                .into(cardImage)
                        }

                    }.addOnFailureListener {
                        Log.d("dbg", "Profile picture download error")
                    }
                    // Click listener for the profile card
                    cardImageFrame.setOnClickListener { _ ->
                        findNavController().navigate(
                            R.id.action_tripDetailsFragment_to_nav_show_profile,
                            bundleOf("profileId" to it.getString("profileId"))
                        )
                    }

                    recyclerView.layoutManager = LinearLayoutManager(this.context)
                    recyclerView.adapter = StopDetailsAdapter(myList)
                }
                interestedUsersDetailsRecyclerView.layoutManager = LinearLayoutManager(this.context)
                interestedUsersDetailsRecyclerView.adapter = InterestedUserDetailsAdapter(interestedUserDetailsList, findNavController(), this, isComplete, n, ownerName)
            }

            //If trip is completed, user is a passenger (state = Confirmed) of said trip and
            //not the owner, show rating bar in the driver's profile card
            if(isComplete && !owned && confirmed){
                val ratingBar = view.findViewById<RatingBar>(R.id.inputDriverRatingBar)
                val ratingButton = view.findViewById<Button>(R.id.buttonSubmitDriverRating)
                ratingBar.visibility = View.VISIBLE
                val driverRating = TripViewModel.getUserRating(n, auth?.currentUser?.uid!!)
                driverRating.observe(viewLifecycleOwner) {
                    if (it >= 0.0) {
                        ratingBar.setIsIndicator(true)
                        ratingBar.rating = it.toFloat()
                        ratingButton.visibility = View.GONE
                    } else {
                        ratingBar.setIsIndicator(false)
                        ratingBar.stepSize = 0.5F
                        ratingButton.visibility = View.VISIBLE
                        ratingButton.setOnClickListener {
                            DialogForConfirmRating { ctx ->
                                val builder = AlertDialog.Builder(ctx)
                                val v = layoutInflater.inflate(R.layout.dialog_comment_rating, null)
                                builder.setView(v)
                                    .setTitle(R.string.ratingAlert)
                                    .setPositiveButton(getString(R.string.yesConfirm)) { _, _ ->
                                        val comm = v.findViewById<TextInputEditText>(R.id.etComment)
                                        TripViewModel.addDriverRating(n, auth?.currentUser?.uid!!, ratingBar.rating.toDouble())
                                        UserProfileViewModel.addDriverRating(tripOwner, ratingBar.rating.toDouble(), comm.text.toString(), auth?.currentUser?.uid!!)
                                        ratingButton.visibility = View.GONE
                                        Snackbar.make(card, R.string.ratingSubmitted, Snackbar.LENGTH_SHORT).show()
                                    }
                                    .setNegativeButton(getString(R.string.noConfirm)) { _, _ -> }
                                builder.create()
                            }.show(childFragmentManager, "PurchaseConfirmationDialog")
                        }
                    }
                }
            }

            spinner.dismiss()
        }

        interestedUsersDetailsRecyclerView = view.findViewById(R.id.rvInterestedUsersDetails)

        //If user is not the owner of the trip and not a confirmed passenger yet, show fab
        if (!owned && !confirmed) {
            Log.d("DBG", "Not owned")
            fabNotify.isEnabled = true

            tripModel.bookingslist.observe(viewLifecycleOwner){
                var found = false
                for(book in it) {
                    if (book.profileId == auth?.currentUser?.uid) {
                        found = true
                        break
                    }
                }
                isUserInterested.value = found
                tripModel.bookingslist.removeObservers(viewLifecycleOwner)
            }

            view.findViewById<TextView>(R.id.textView10).isVisible = false
            interestedUsersDetailsRecyclerView.isVisible = false
        }
        //if current user is the owner, show the list of interested people
        else if (owned){
            Log.d("DBG", "Owned")
            card.isVisible = false
            fabNotify.isVisible = false
            view.findViewById<TextView>(R.id.textView10).isVisible = true
            interestedUsersDetailsRecyclerView.isVisible = true
            interestedUsersDetailsRecyclerView.isNestedScrollingEnabled = false
            view.findViewById<TextView>(R.id.textView12).isVisible = false
        }
        else {
            card.isVisible = true
            fabNotify.isVisible = false
            interestedUsersDetailsRecyclerView.isVisible = false
        }

    }

    @ExperimentalCoroutinesApi
    private fun setUpFab() {
        isUserInterested.observe(viewLifecycleOwner) {
            //if user is interested
            if (it) {
                fabNotify.setOnClickListener {
                    tripModel.unbookTrip(
                        FirebaseAuth.getInstance().currentUser?.uid!!,
                        {
                            view?.let { it1 ->
                                Snackbar.make(
                                    it1,
                                    getString(R.string.tripNoInterest),
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                            isUserInterested.postValue(false)
                        },
                        {
                            it.message?.let { it1 -> Log.d("dbg", it1) }
                            view?.let { it1 ->
                                Snackbar.make(
                                    it1,
                                    getString(R.string.daoSetProfileError),
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
                fabNotify.setImageDrawable(context?.let { ContextCompat.getDrawable(it, R.drawable.notification_off_icon) })
                fabNotify.bringToFront()
            }
            else {
                fabNotify.setOnClickListener {
                    tripModel.bookTrip(
                        FirebaseAuth.getInstance().currentUser?.uid!!,
                        {
                            view?.let { it1 ->
                                Snackbar.make(
                                    it1,
                                    getString(R.string.tripInterest),
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                            isUserInterested.postValue(true)
                        },
                        {
                            it.message?.let { it1 -> Log.d("dbg", it1) }
                            view?.let { it1 ->
                                Snackbar.make(
                                    it1,
                                    getString(R.string.daoSetProfileError),
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
                fabNotify.setImageDrawable(context?.let { ContextCompat.getDrawable(it, R.drawable.ic_notify) })
                fabNotify.bringToFront()
            }
        }
    }

    @ExperimentalCoroutinesApi
    private fun restoreTrip(json: JSONObject){

        val storageReference = Firebase.storage.reference.child("/tripImages/${n}")
        storageReference.metadata.addOnSuccessListener {
            if(isAdded){
                GlideApp.with(this)
                    .load(storageReference)
                    .signature(ObjectKey(it.updatedTimeMillis.toString()))
                    .into(ivPhoto)
            }

        }.addOnFailureListener{
            Log.d("dbg", "Profile picture download error")
        }


        // Departure date
        day = json.get("depDay").toString().toInt()
        month = json.get("depMonth").toString().toInt()
        year = json.get("depYear").toString().toInt()
        tvDate.text = getString(R.string.dateFormat, day, DateFormatSymbols().months[month], year)
        
        items.clear()

        // Departure location and time
        var addr = Geocoder(requireContext()).getFromLocationName(json.getString("depLoc"),1)
        addGeoPoint(addr[0].latitude,
            addr[0].longitude,
            getString(R.string.departurePoint))

        etDepLoc.setText(json.get("depLoc").toString())
        etDepTime.hint = json.get("depTime").toString()

        // List of stops
        if(json.get("stopsArray")!="" && json.get("stopsArray")!="{}" && json.get("stopsArray")!="[]")
            restoreJsonStopArray(JSONArray(json.get("stopsArray").toString()))

        // Arrival location and time
        addr = Geocoder(requireContext()).getFromLocationName(json.getString("arrLoc"),1)
        addGeoPoint(addr[0].latitude,
            addr[0].longitude,
            getString(R.string.arrivalPoint))

        etArrLoc.setText(json.get("arrLoc").toString())
        if(json.get("dayAfter").toString().toBoolean())
            etArrTime.hint = "${json.get("arrTime")}*"
        else
            etArrTime.hint = json.get("arrTime").toString()

        // Other information
        tvSeats.text = json.get("seats").toString()
        tvPrice.text = getString(R.string.showPrice, json.get("price").toString().toFloat())
        etDesc.setText(json.get("desc").toString())
        tripOwner = json.getString("profileId")

        // Calculating trip duration
        val start = TimeT.parseTimeT(json.get("depTime").toString())
        val end = TimeT.parseTimeT(json.get("arrTime").toString())
        val diff = TimeT.differenceTimeT(start, end)

        tvDuration.text = getString(R.string.durationFormat, diff.hours, diff.minutes)

        val mapController = map.controller
        fixZoom(mapController, items.map { it->it.point },map)

        if (json.has("completed")) isComplete = json.getBoolean("completed")
    }



    private fun addGeoPoint(lat: Double, long: Double, title: String){
        if(!(lat.compareTo(90)>0 && lat.compareTo(-90)<0 && long.compareTo(180)>0 && long.compareTo(-180)<0)){
            val ovr = OverlayItem(title, "", GeoPoint(lat, long))
            items.add(ovr)
            addMarker(map, ovr, requireContext())
        }
    }

    private fun restoreJsonStopArray(jsonArr: JSONArray){
        myList.clear()
        lateinit var addr : List<Address>
        for (idx in (0 until jsonArr.length()-1)){
            val jsonObj = JSONObject(jsonArr.getString(idx))
            addr = Geocoder(requireContext()).getFromLocationName(jsonObj.getString("addr"),1)
            addGeoPoint(addr[0].latitude,
                addr[0].longitude,
                getString(R.string.stopPoint)+" ${idx+1}")
            myList.add(StopDetails(jsonObj.getString("addr"), jsonObj.getString("time")))
        }
    }

}

class DialogForConfirmRating(private val callback: (FragmentActivity) -> Dialog) : DialogFragment() {
    @ExperimentalCoroutinesApi
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            callback(it)
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}

internal fun addMarker(map: MapView, ovr: OverlayItem, ctx: Context){
    val marker = Marker(map)
    marker.position = GeoPoint(ovr.point.latitude, ovr.point.longitude)
    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    map.overlays.add(marker)
    map.invalidate()
    when(ovr.title){
        ctx.getString(R.string.departurePoint) ->  marker.icon = ResourcesCompat.getDrawable(ctx.resources, R.drawable.departure_marker, null)

        ctx.getString(R.string.arrivalPoint) ->  marker.icon = ResourcesCompat.getDrawable(ctx.resources, R.drawable.arrival_marker, null)

        else -> {
            marker.icon = ResourcesCompat.getDrawable(ctx.resources, R.drawable.stop_marker, null)
        }
    }

    marker.title = ovr.title
    marker.infoWindow = MarkerInfoWindow(R.layout.marker_layout, map)
    marker.showInfoWindow()
    marker.infoWindow.view.setOnTouchListener { _, _ ->
        return@setOnTouchListener false
    }
}

internal fun fixZoom(controller: IMapController, points: List<IGeoPoint>, map: MapView) {
    var minLat = Int.MAX_VALUE.toDouble()
    var maxLat = Int.MIN_VALUE.toDouble()
    var minLon = Int.MAX_VALUE.toDouble()
    var maxLon = Int.MIN_VALUE.toDouble()
    for (item in points) {
        val lat = item.latitude
        val lon = item.longitude
        maxLat = Math.max(lat, maxLat)
        minLat = Math.min(lat, minLat)
        maxLon = Math.max(lon, maxLon)
        minLon = Math.min(lon, minLon)
    }
    val fitFactor =1.4
    if(points.size > 1) {
        val box = BoundingBox(
            maxLat * fitFactor,
            maxLon * fitFactor,
            minLat * fitFactor,
            minLon * fitFactor
        )
        map.zoomToBoundingBox(box, true)
    }
    else{
        map.controller.setZoom(11.5)
    }
    controller.animateTo(
        GeoPoint(
            (maxLat + minLat) / 2,
            (maxLon + minLon) / 2
        )
    ) //so the center is positioned just in the middle
}

class TimeT(var hours: Int, var minutes: Int): Comparable<TimeT>{

    override operator fun compareTo(other: TimeT):Int{
        if((hours == other.hours) && (minutes==other.minutes))
            return 0

        if( (hours < other.hours) || ((hours == other.hours) && (minutes < other.minutes)))
            return -1

        if((hours > other.hours) || ((hours == other.hours) && (minutes > other.minutes)))
            return 1

        return 0 // Useless, only to have compilable code
    }

    companion object{
        fun add(a: TimeT, b: TimeT): TimeT{
            val sum = TimeT(0, 0)
            sum.hours = a.hours + b.hours
            sum.minutes = a.minutes + b.minutes
            sum.hours += (sum.minutes / 60)
            sum.minutes = sum.minutes % 60
            return sum
        }



        fun parseTimeT(s: String): TimeT{
            if(s=="")
                return TimeT(-1, -1)

            val l = s.split(":")
            if(l.size != 2)
                return TimeT(-1, -1)

            if((l[0].toInt() < 0) || (l[0].toInt() > 23) || (l[1].toInt() < 0) || (l[1].toInt() > 59))
                return TimeT(-1, -1)

            return TimeT(l[0].toInt(), l[1].toInt())
        }

        fun differenceTimeT(start: TimeT, stop: TimeT): TimeT{

            var diff = TimeT(0, 0)

            // if start minute is greater
            // convert stop hour into minutes
            // and add minutes to stop minutes
            if(stop.hours<start.hours){
                diff = add(differenceTimeT(start, TimeT(24, 0)), differenceTimeT(TimeT(0, 0), stop))
            } else{
                if(start.minutes > stop.minutes){
                    --stop.hours
                    stop.minutes += 60
                }

                diff.minutes = stop.minutes - start.minutes
                diff.hours = stop.hours - start.hours
            }
            // return the difference time
            return diff
        }
    }
}

class StopDetails(val address: String, val time: String)

class StopDetailsAdapter(private val data: ArrayList<StopDetails>): RecyclerView.Adapter<StopDetailsAdapter.StopDetailsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StopDetailsViewHolder {
        val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.stop_layout, parent, false)

        return StopDetailsViewHolder(v)
    }

    override fun onBindViewHolder(holder: StopDetailsViewHolder, position: Int) {
        val u = data[position]
        holder.bind(u, position + 1)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    class StopDetailsViewHolder(view: View): RecyclerView.ViewHolder(view) {
        private val addr = view.findViewById<TextInputEditText>(R.id.etStop)
        private val t = view.findViewById<TextInputLayout>(R.id.tiStopTime)
        private val stopView = view.findViewById<TextInputLayout>(R.id.tiStop)

        fun bind(stop: StopDetails, pos: Int){
            addr.setText(stop.address)
            t.hint = stop.time
            stopView.hint = "Stop #$pos:"
        }
    }
}

class InterestedUserDetails(val id: String, val profileId: String, val name: String, var state: String, var passengerRating: Double, var driverRating: Double)

class InterestedUserDetailsAdapter(private val data: ArrayList<InterestedUserDetails>, private val nav: NavController, private val act: Fragment, private val isComplete: Boolean, private val tripId: String, private val tripOwner: String): RecyclerView.Adapter<InterestedUserDetailsAdapter.InterestedUserDetailsViewHolder>() {

    private val navigate = { profileId: String? ->
        nav.navigate(R.id.action_tripDetailsFragment_to_nav_show_profile, bundleOf("profileId" to profileId))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InterestedUserDetailsViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.interested_user_layout, parent, false)

        return InterestedUserDetailsViewHolder(v, act, isComplete, tripId, tripOwner)
    }

    @ExperimentalCoroutinesApi
    override fun onBindViewHolder(holder: InterestedUserDetailsViewHolder, position: Int) {
        val u = data[position]
        holder.bind(u, position + 1, navigate)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    class InterestedUserDetailsViewHolder(view: View, private val act: Fragment, private val isComplete: Boolean, private val tripId: String, private val tripOwner: String): RecyclerView.ViewHolder(view) {
        private val name = view.findViewById<TextView>(R.id.tvInterestedUserName)
        private val state = view.findViewById<Chip>(R.id.interestedUserState)
        private val userCardView = view.findViewById<MaterialCardView>(R.id.cvInterestedUser)
        private val image = view.findViewById<ImageView>(R.id.ivInterestedUser)
        private val imageFrame = view.findViewById<FrameLayout>(R.id.flProfile)
        private val radio = view.findViewById<RadioGroup>(R.id.radioGroup)
        private val ratingBar = view.findViewById<RatingBar>(R.id.inputPassengerRatingBar)
        private val submitButton = view.findViewById<Button>(R.id.buttonSubmitPassengerRating)

        @ExperimentalCoroutinesApi
        fun bind(interestedUser: InterestedUserDetails, pos: Int, navigate: (profileId: String) -> Unit){

            name.text = interestedUser.name
            when(interestedUser.state){
                bookState.CONFIRMED.name ->{
                    state.text = act.getString(R.string.confirmed)
                    state.setChipBackgroundColorResource(R.color.confirmed)
                    if (isComplete && interestedUser.passengerRating == -1.0) {
                        ratingBar.visibility = View.VISIBLE
                        ratingBar.stepSize = 0.5F
                        submitButton.visibility = View.VISIBLE
                        submitButton.setOnClickListener {
                            DialogForConfirmRating { ctx ->
                                val builder = AlertDialog.Builder(ctx)
                                val v = act.layoutInflater.inflate(R.layout.dialog_comment_rating, null)
                                builder.setView(v)
                                    .setTitle(R.string.ratingAlert)
                                    .setPositiveButton(R.string.yesConfirm) { _, _ ->
                                        val comm = v.findViewById<TextInputEditText>(R.id.etComment)
                                        UserProfileViewModel.addPassengerRating(interestedUser.profileId, ratingBar.rating.toDouble(), comm.text.toString(), tripOwner)
                                        TripViewModel.addPassengerRating(tripId, interestedUser.profileId, ratingBar.rating.toDouble())
                                        submitButton.visibility = View.GONE
                                        Snackbar.make(userCardView, R.string.ratingSubmitted, Snackbar.LENGTH_SHORT).show()
                                    }
                                    .setNegativeButton(R.string.noConfirm){ _, _ -> }
                                builder.create()
                            }.show(act.childFragmentManager, "PurchaseConfirmationDialog")
                        }
                    }
                    else if (isComplete && interestedUser.passengerRating != -1.0) {
                        ratingBar.setIsIndicator(true)
                        ratingBar.visibility = View.VISIBLE
                        ratingBar.rating = interestedUser.passengerRating.toFloat()
                    }

                }
                bookState.REJECTED.name ->{
                    state.text = act.getString(R.string.rejected)
                    state.setChipBackgroundColorResource(R.color.rejected)
                }
                bookState.PENDING.name ->{
                    state.text = act.getString(R.string.pending)
                    state.setChipBackgroundColorResource(R.color.pending)
                }
            }
            radio.isVisible = false
            val profileRef = Firebase.storage.reference.child("/profileImages/${interestedUser.profileId}.jpg")
            profileRef.metadata.addOnSuccessListener { meta ->
                GlideApp.with(act)
                    .load(profileRef)
                    .signature(ObjectKey(meta.updatedTimeMillis.toString()))
                    .into(image)
            }.addOnFailureListener {
                Log.d("dbg", "Profile picture download error")
            }
            imageFrame.setOnClickListener {
                navigate(interestedUser.profileId)
            }
        }
    }
}