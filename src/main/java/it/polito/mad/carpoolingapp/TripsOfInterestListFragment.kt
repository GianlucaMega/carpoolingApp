package it.polito.mad.carpoolingapp

import android.content.ContentResolver
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONObject
import java.text.DateFormatSymbols


class TripsOfInterestListFragment : Fragment() {
    private var cards =  mutableListOf<InterestingCard>()
    @ExperimentalCoroutinesApi
    private lateinit var viewModel : TripViewModel
    private lateinit var rv: RecyclerView

    @ExperimentalCoroutinesApi
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val spinner = initSpinner(layoutInflater)
        spinner.show(childFragmentManager, "PurchaseConfirmationDialog")

        val view = inflater.inflate(R.layout.fragment_trips_of_interest_list, container, false)
        rv = view.findViewById(R.id.rv_interesting_list)
        val factory = TripViewModelFactory("", FirebaseAuth.getInstance().currentUser?.uid!!)
        viewModel = ViewModelProviders.of(this, factory).get(TripViewModel::class.java)
        viewModel.interestingTripsList.observe(viewLifecycleOwner){
            if(it.isNullOrEmpty())
                Snackbar.make(view, R.string.emptyTripList, Snackbar.LENGTH_SHORT).show()
            else
                createCards(it)
            rv.layoutManager = LinearLayoutManager(this.context)
            val cardAdapter = InterestingCardListAdapter(cards, findNavController(), requireContext().contentResolver, this)
            rv.adapter = cardAdapter

            spinner.dismiss()
        }

        return view
    }

    private fun createCards(json: List<JSONObject>) {

        var card: InterestingCard
        var title: String
        var d: String
        var m: String
        var y: String
        var departure: String
        var departureTime: String
        var arrival :String
        var arrivalTime :String
        var duration: String
        var seats: String
        var price: String
        var start: TimeT
        var end: TimeT
        var diff: TimeT

        cards.clear()

        for (tripJson in json) {
            d = tripJson.getString("depDay")
            m = tripJson.getString("depMonth")
            y = tripJson.getString("depYear")
            title = getString(R.string.dateFormat, d.toInt(), DateFormatSymbols().months[m.toInt()], y.toInt())
            departure = tripJson.getString("depLoc")
            departureTime = tripJson.getString("depTime")
            arrival = tripJson.getString("arrLoc")
            arrivalTime = tripJson.getString("arrTime")
            start = TimeT.parseTimeT(departureTime)
            end = TimeT.parseTimeT(arrivalTime)
            diff = TimeT.differenceTimeT(start, end)
            duration = getString(R.string.durationFormat, diff.hours, diff.minutes)
            seats = tripJson.getString("seats")
            price = tripJson.getString("price")

            card = InterestingCard(tripJson.getString("id"), title, departure, departureTime, arrival, arrivalTime, duration, seats, price)
            cards.add(card)
        }
    }
}

class InterestingCard(val tripId: String, val title :String, val departure :String, val departureTime :String,  val arrival :String, val arrivalTime :String,
                    val duration: String, val seats: String, val price: String){}

class InterestingCardListAdapter(val cards: MutableList<InterestingCard>, navigationController: NavController, contentResolver: ContentResolver, frg: Fragment): RecyclerView.Adapter<InterestingCardListAdapter.InterestingCardListViewHolder>() {
    private val navController = navigationController
    private val frag = frg

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InterestingCardListViewHolder {
        val layout = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_layout, parent, false)
        return InterestingCardListViewHolder(layout, navController)
    }

    class InterestingCardListViewHolder(v: View, navigationController: NavController) : RecyclerView.ViewHolder(v) {
        val picture = v.findViewById<ImageView>(R.id.card_img)
        val titleCard = v.findViewById<TextView>(R.id.card_title)
        val textDep = v.findViewById<TextView>(R.id.card_dep)
        val timeDep = v.findViewById<TextView>(R.id.card_dep_time)
        val textArr = v.findViewById<TextView>(R.id.card_arr)
        val timeArr = v.findViewById<TextView>(R.id.card_arr_time)
        val duration = v.findViewById<TextView>(R.id.card_dur)
        val seats = v.findViewById<TextView>(R.id.card_seats)
        val price = v.findViewById<TextView>(R.id.card_price)
        val modify_button = v.findViewById<ImageButton>(R.id.card_button_modify)
        val cardView = v.findViewById<MaterialCardView>(R.id.card)

        val navController = navigationController

        fun bind(card: InterestingCard, fragment: Fragment) {

            val bundle = Bundle()
            bundle.putString("nTrip", card.tripId)
            bundle.putBoolean("owned", false)

            val storageReference = Firebase.storage.reference.child("/tripImages/${card.tripId}")
            storageReference.metadata.addOnSuccessListener {
                GlideApp.with(fragment)
                    .load(storageReference)
                    .signature(ObjectKey(it.updatedTimeMillis.toString()))
                    .into(picture)
            }.addOnFailureListener{
                Log.d("dbg", "Profile picture download error")
            }

            titleCard.text = card.title
            textDep.text=card.departure
            timeDep.text=card.departureTime
            textArr.text=card.arrival
            timeArr.text=card.arrivalTime
            duration.text=card.duration
            seats.text=card.seats
            price.text=card.price

            cardView.setOnClickListener {
                navController.navigate(R.id.action_nav_interesting_list_to_tripDetailsFragment, bundle)
            }
            modify_button.visibility = View.INVISIBLE
        }
    }

    override fun getItemCount(): Int {
        return cards.size
    }

    override fun onBindViewHolder(holder: InterestingCardListViewHolder, position: Int) {
        holder.bind(cards[position], frag)
    }

    fun add(card: InterestingCard) {
        cards.add(card)
        this.notifyItemInserted(cards.size - 1)
    }
}
