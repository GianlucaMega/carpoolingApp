package it.polito.mad.carpoolingapp

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.*
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.signature.ObjectKey
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONObject


class ShowProfileFragment : Fragment() {

    private lateinit var tvName : TextView
    private lateinit var tvNick : TextView
    private lateinit var tvEmail : TextView
    private lateinit var tvLocation : TextView
    private lateinit var ivPhoto : ImageView
    private lateinit var listener: ListenerRegistration
    private lateinit var driverRating: RatingBar
    private lateinit var passengerRating: RatingBar
    private var auth: FirebaseAuth? = FirebaseAuth.getInstance()
    private var profile: String? = ""
    @ExperimentalCoroutinesApi
    private lateinit var viewModel : UserProfileViewModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        profile = arguments?.getString("profileId")?: auth?.currentUser?.uid //if no uid is passed as argument, set current logged in user as profile
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_show_profile, container, false)
    }

    @ExperimentalCoroutinesApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spinner = initSpinner(layoutInflater)
        spinner.show(childFragmentManager, "PurchaseConfirmationDialog")

        tvName = view.findViewById(R.id.tvName)
        tvNick = view.findViewById(R.id.tvNick)
        tvEmail = view.findViewById(R.id.tvEmail)
        tvLocation = view.findViewById(R.id.tvLocation)
        ivPhoto = view.findViewById(R.id.ivPhoto)
        driverRating = view.findViewById(R.id.ratingBarProfileDriver)
        passengerRating = view.findViewById(R.id.ratingBarProfilePassenger)
        driverRating.isEnabled = false
        passengerRating.isEnabled = false

        val factory = UserViewModelFactory(profile!!)
        viewModel = ViewModelProviders.of(this, factory).get(UserProfileViewModel::class.java)
        viewModel.user.observe(viewLifecycleOwner){
            if(it.toString()!="{}")
                populate(it)
            else{
                Toast.makeText(context, R.string.profileNotFound, Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }

            spinner.dismiss()
        }
        if (profile != auth?.currentUser?.uid){
            tvEmail.visibility = GONE
            tvLocation.visibility = GONE
        }

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        if (profile == auth?.currentUser?.uid) inflater.inflate(R.menu.edit_show_profile_menu, menu)
        menu.removeItem(R.id.itemInfo)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.itemEdit -> {
                editProfile()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun editProfile(){
        findNavController().navigate(R.id.action_nav_show_profile_to_nav_edit_profile)
    }

     private fun populate(json: JSONObject): Unit {
         tvName.text = json.getString("name")
         tvNick.text = json.getString("nickname")
         tvEmail.text = json.getString("email")
         tvLocation.text = json.getString("location")

         var driverTotalStars: Float = 0f
         var driverNumberOfRatings: Long = 0
         var passengerTotalStars: Float = 0f
         var passengerNumberOfRatings: Long = 0

         if (json.has("driverTotalStars")) driverTotalStars = json.getDouble("driverTotalStars").toFloat()
         if (json.has("driverNumberOfRatings")) driverNumberOfRatings = json.getLong("driverNumberOfRatings")
         if (json.has("passengerTotalStars")) passengerTotalStars = json.getDouble("passengerTotalStars").toFloat()
         if (json.has("passengerNumberOfRatings")) passengerNumberOfRatings = json.getLong("passengerNumberOfRatings")

         if (driverNumberOfRatings != 0L) {
             driverRating.rating = (driverTotalStars / driverNumberOfRatings.toFloat())
             val string = SpannableString(driverRating.rating.toString())
             string.setSpan(CustomClickableSpan(true), 0, string.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

             val tv = view?.findViewById<TextView>(R.id.tvDriver)
             tv?.setText(string, TextView.BufferType.SPANNABLE)
             tv?.movementMethod = LinkMovementMethod.getInstance()

         }
         else driverRating.rating = 0f
         if (passengerNumberOfRatings != 0L) {
             passengerRating.rating =(passengerTotalStars / passengerNumberOfRatings.toFloat())
             val string = SpannableString(passengerRating.rating.toString())
             string.setSpan(CustomClickableSpan(false), 0, string.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

             val tv = view?.findViewById<TextView>(R.id.tvPassenger)
             tv?.setText(string, TextView.BufferType.SPANNABLE)
             tv?.movementMethod = LinkMovementMethod.getInstance()
         }
         else passengerRating.rating = 0f

         if(isAdded){
             val storageReference = Firebase.storage.reference.child("/profileImages/${profile}.jpg")
                storageReference.metadata.addOnSuccessListener {
                    GlideApp.with(this)
                        .load(storageReference)
                        .signature(ObjectKey(it.updatedTimeMillis.toString()))
                        .into(ivPhoto)
                }.addOnFailureListener{
                     Log.d("dbg", "Profile picture download error")
                }
         }
    }
    inner class CustomClickableSpan(val asDriver: Boolean): ClickableSpan(){

        @ExperimentalCoroutinesApi
        override fun onClick(widget: View) {
            val ratings = viewModel.getRatings(asDriver)
            ratings.observe(viewLifecycleOwner){
                if(it != null && it.isNotEmpty()) {
                    ratings.removeObservers(viewLifecycleOwner)
                    findNavController().navigate(
                        R.id.action_nav_show_profile_to_ratingsFragment,
                        bundleOf("ratings" to it)
                    )
                }
            }
        }

    }
}
