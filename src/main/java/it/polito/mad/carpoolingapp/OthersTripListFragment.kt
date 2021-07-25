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

class OthersTripListFragment : Fragment() {
    private lateinit var cards: MutableList<CardOtherList>
    private lateinit var searchKey : String
    private var filteredCards = mutableListOf<CardOtherList>()
    @ExperimentalCoroutinesApi
    private lateinit var viewModel : TripViewModel
    private lateinit var rv: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
        inflater.inflate(R.menu.search_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.ic_search -> {
                findNavController().navigate(R.id.action_nav_others_list_to_searchFragment)
                true
            }
            R.id.ic_clear_search -> {
                filteredCards.clear()
                filteredCards.addAll(cards)
                rv.adapter?.notifyDataSetChanged()
                Snackbar.make(requireView(), R.string.searchFiltersCleared, Snackbar.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @ExperimentalCoroutinesApi
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val spinner = initSpinner(layoutInflater)
        spinner.show(childFragmentManager, "PurchaseConfirmationDialog")

        val view = inflater.inflate(R.layout.fragment_others_trip_list, container, false)
        rv = view.findViewById(R.id.rv_other_list)

        val factory = TripViewModelFactory("", FirebaseAuth.getInstance().currentUser?.uid!!)
        viewModel = ViewModelProviders.of(this, factory).get(TripViewModel::class.java)
        viewModel.triplist.observe(viewLifecycleOwner){
            if(it.isNullOrEmpty())
                Snackbar.make(view, R.string.emptyTripList, Snackbar.LENGTH_SHORT).show()
            else
                createCards(it)
            rv.layoutManager = LinearLayoutManager(this.context)

            setUpFilteredCards(rv)

            val cardOtherListAdapter=CardOtherListAdapter(filteredCards, findNavController(), requireContext().contentResolver, this)
            rv.adapter = cardOtherListAdapter

            spinner.dismiss()
        }

        return view
    }

    private fun setUpFilteredCards(rv: RecyclerView) {
        if (arguments != null) {
            val arguments = requireArguments()
            val depLocFilter = arguments["depLoc"].toString()
            val arrLocFilter = arguments["arrLoc"].toString()
            val depDateFilter = arguments["depDate"].toString()
            val depTimeMinFilter = arguments["depTimeMin"].toString()
            val depTimeMaxFilter = arguments["depTimeMax"].toString()
            val arrTimeMinFilter = arguments["arrTimeMin"].toString()
            val arrTimeMaxFilter = arguments["arrTimeMax"].toString()
            val priceMinFilter = arguments["priceMin"].toString()
            val priceMaxFilter = arguments["priceMax"].toString()

            filteredCards.clear()

            for (card in cards) {
                if (
                    locationMatches(card.departure, depLocFilter) &&
                    locationMatches(card.arrival, arrLocFilter) &&
                    dateMatches(card.title, depDateFilter) &&
                    timeIntervalMatches(card.departureTime, depTimeMinFilter, depTimeMaxFilter) &&
                    timeIntervalMatches(card.arrivalTime, arrTimeMinFilter, arrTimeMaxFilter) &&
                    priceMatches(card.price, priceMinFilter, priceMaxFilter)
                )
                    filteredCards.add(card)
            }
            rv.adapter?.notifyDataSetChanged()
        }
    }

    private fun locationMatches(location: String, filter: String): Boolean {
        return if (filter == "") true
        else return location.contains(filter, true)
    }

    private fun dateMatches(date: String, filter: String): Boolean {
        return if (filter == "") true
        else return date.contains(filter, true)
    }

    private fun timeIntervalMatches(time: String, minTime: String, maxTime: String): Boolean{
        return if (minTime == "" && maxTime == "") true
        else {
            val timeT = TimeT.parseTimeT(time)
            val minTimeT = TimeT.parseTimeT(minTime)
            val maxTimeT = TimeT.parseTimeT(maxTime)
            if (minTime == "") return timeT <= maxTimeT
            if (maxTime == "") return timeT >= minTimeT
            return (timeT >= minTimeT) && (timeT <= maxTimeT)
        }
    }

    private fun priceMatches(price: String, minPrice: String, maxPrice: String): Boolean {
        if (minPrice == "" && maxPrice == "") return true
        if (minPrice == "") return price <= maxPrice
        if (maxPrice == "") return price >= minPrice
        return price in minPrice..maxPrice
    }

    private fun createCards(json: List<JSONObject>) {

        var card: CardOtherList
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

        cards= mutableListOf()
        filteredCards = mutableListOf()

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

            card = CardOtherList(tripJson.getString("id"), title, departure, departureTime, arrival, arrivalTime, duration, seats, price)
            cards.add(card)
            filteredCards.add(card)
        }
    }
}

class CardOtherList(val tripId: String, val title :String, val departure :String, val departureTime :String,  val arrival :String, val arrivalTime :String,
                    val duration: String, val seats: String, val price: String){}

class CardOtherListAdapter(val cards: MutableList<CardOtherList>, navigationController: NavController, contentResolver: ContentResolver, frg: Fragment): RecyclerView.Adapter<CardOtherListAdapter.CardOtherListViewHolder>()/*, Filterable*/ {
    private val navController = navigationController
    private val frag = frg

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardOtherListViewHolder {
        val layout = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_layout, parent, false)
        return CardOtherListViewHolder(layout, navController)
    }

    class CardOtherListViewHolder(v: View, navigationController: NavController) : RecyclerView.ViewHolder(v) {
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

        fun bind(card: CardOtherList, fragment: Fragment) {

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
                navController.navigate(R.id.action_othersTripListFragment_to_tripDetailsFragment, bundle)
            }
            modify_button.visibility = View.INVISIBLE
        }
    }

    override fun getItemCount(): Int {
        return cards.size
    }

    override fun onBindViewHolder(holder: CardOtherListViewHolder, position: Int) {
        holder.bind(cards[position], frag)
    }

    fun add(card: CardOtherList) {
        cards.add(card)
        this.notifyItemInserted(cards.size - 1)
    }
}
