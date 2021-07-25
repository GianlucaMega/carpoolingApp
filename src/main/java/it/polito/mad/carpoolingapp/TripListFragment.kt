package it.polito.mad.carpoolingapp

import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentResolver
import android.content.res.Resources
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.marginTop
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONObject
import java.text.DateFormatSymbols


class TripListFragment : Fragment() {

    private var cards = mutableListOf<Card>()
    @ExperimentalCoroutinesApi
    private lateinit var viewModel : TripViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }


    @ExperimentalCoroutinesApi
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val spinner = initSpinner(layoutInflater)
        spinner.show(childFragmentManager, "PurchaseConfirmationDialog")
        val view = inflater.inflate(R.layout.fragment_trip_list, container, false)
        val rv: RecyclerView = view.findViewById(R.id.rv)
        val add_button: FloatingActionButton = view.findViewById<FloatingActionButton>(R.id.fab_add)

        val factory = TripViewModelFactory("", FirebaseAuth.getInstance().currentUser?.uid!!)
        viewModel = ViewModelProviders.of(this, factory).get(TripViewModel::class.java)
        viewModel.userTripList.observe(viewLifecycleOwner){
            if(it.isNullOrEmpty())
                Snackbar.make(view, R.string.emptyTripList, Snackbar.LENGTH_SHORT).show()
            else
                createCards(it)
            rv.layoutManager = LinearLayoutManager(this.context)
            val cardAdapter=CardAdapter(cards, findNavController(), requireContext().contentResolver, this)
            rv.adapter = cardAdapter

            spinner.dismiss()
        }

        add_button.setOnClickListener{
            findNavController().navigate(R.id.action_tripListFragment_to_tripEditFragment)
        }

        return view
    }

    private fun createCards(json: List<JSONObject>){
        var card: Card
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

            card = Card(
                tripJson.getString("id"),
                title,
                departure,
                departureTime,
                arrival,
                arrivalTime,
                duration,
                seats,
                price
            )
            cards.add(card)
        }
    }
}




class Card(val tripId: String, val title :String, val departure :String, val departureTime :String,  val arrival :String, val arrivalTime :String,
            val duration: String, val seats: String, val price: String){}

class CardAdapter(val cards: MutableList<Card>,
                  navigationController: NavController,
                  contentResolver: ContentResolver,
                  frg: Fragment
): RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    private val navController = navigationController
    private val contRes = contentResolver
    private val frag = frg

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val layout = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_layout, parent, false)
        return CardViewHolder(layout, navController)
    }

    class CardViewHolder(v: View, navigationController: NavController) : RecyclerView.ViewHolder(v) {
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


        fun bind(card: Card, fragment: Fragment) {

            //initialize bundle with trip id to be passed to trip fragments (via navigate) so they can retrieve correct trip data
            val bundle = Bundle()
            bundle.putString("nTrip", card.tripId)
            bundle.putBoolean("owned", true)


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
                navController.navigate(R.id.action_tripListFragment_to_tripDetailsFragment, bundle)
            }
            modify_button.setOnClickListener {
                navController.navigate(R.id.action_tripListFragment_to_tripEditFragment, bundle)
            }

        }
    }

    override fun getItemCount(): Int {
        return cards.size
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(cards[position], frag)
    }

    fun add(card: Card) {
        cards.add(card)
        this.notifyItemInserted(cards.size - 1)
    }
}

class LoadingDialog(private val callback: (FragmentActivity) -> Dialog) : DialogFragment() {
    @ExperimentalCoroutinesApi
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            callback(it)
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}

fun initSpinner(layoutInflater: LayoutInflater): LoadingDialog{
    return LoadingDialog { ctx ->
        val builder = AlertDialog.Builder(ctx, R.style.myFullscreenAlertDialogStyle)
        val v = layoutInflater.inflate(R.layout.spinner_layout, null)
        val height = Resources.getSystem().displayMetrics.heightPixels
        val loading = v.findViewById<ImageView>(R.id.progressBar)
        loading.setPadding(0, (height * 0.45).toInt(), 0, 0)
        val animate = loading.drawable as Animatable
        animate.start()
        builder.setView(v)
        builder.create()
    }
}