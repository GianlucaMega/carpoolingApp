package it.polito.mad.carpoolingapp

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Adapter
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.animation.core.LinearEasing
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

class RatingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.ratings_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val asDriver = arguments?.getBoolean("asDriver")
        val ratingsJSON = arguments?.get("ratings") as List<JSONObject>

        val ratings = ratingsJSON.map { Rating(
            it.getString("rater"),
            it.getDouble("stars"),
            it.getString("comment")
        ) }

        if(asDriver == true)
            view.findViewById<TextView>(R.id.tvTitle).text = getString(R.string.driverRating)
        else
            view.findViewById<TextView>(R.id.tvTitle).text = getString(R.string.passengerRating)

        val rv = view.findViewById<RecyclerView>(R.id.rvRatings)
        rv.layoutManager = LinearLayoutManager(this.context)
        rv.adapter = RatingAdapter(ratings)

    }


}

class Rating(val author: String, val rating: Double, val message: String){}

class RatingAdapter(val data: List<Rating>): RecyclerView.Adapter<RatingAdapter.RatingViewHolder>(){
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RatingViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.rating_card_layout, parent, false)

        return RatingViewHolder(v)
    }

    override fun onBindViewHolder(holder: RatingViewHolder, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount(): Int {
        return data.size
    }

    class RatingViewHolder(view: View): RecyclerView.ViewHolder(view) {

        val tvmex = view.findViewById<TextView>(R.id.tvMessage)
        val ratingBar = view.findViewById<RatingBar>(R.id.rating)
        val tvAuthor = view.findViewById<TextView>(R.id.tvAuthor)

        fun bind(rating: Rating){
            if(rating.message != "")
                tvmex.text = rating.message
            else
                tvmex.visibility = View.GONE
            ratingBar.rating = rating.rating.toFloat()
            tvAuthor.text = rating.author
        }
    }
}