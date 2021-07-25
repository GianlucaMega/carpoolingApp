package it.polito.mad.carpoolingapp

import android.util.Log
import androidx.lifecycle.*
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class bookState{
    CONFIRMED, PENDING, REJECTED, COMPLETED
}

@ExperimentalCoroutinesApi
class TripViewModel(
         tid: String = "",
         uid: String = ""
    ) : ViewModel() {
        private val tripId : String = tid
        private val userId : String = uid
        private val _trip = MutableLiveData<JSONObject>()
        var trip : LiveData<JSONObject> = _trip
        private val _triplist = MutableLiveData<List<JSONObject>>()
        var triplist : LiveData<List<JSONObject>> = _triplist
        private val _userTripList = MutableLiveData<List<JSONObject>>()
        var userTripList : LiveData<List<JSONObject>> = _userTripList
        private val _booklist = MutableLiveData<List<InterestedUserDetails>>()
        var bookingslist : LiveData<List<InterestedUserDetails>> = _booklist
        private val _map = MutableLiveData<Map<String, Any>>()
        var map : LiveData<Map<String, Any>> = _map
        private val _interestingTripsList = MutableLiveData<List<JSONObject>>()
        var interestingTripsList : LiveData<List<JSONObject>> = _interestingTripsList
        private val _boughtTripsList = MutableLiveData<List<JSONObject>>()
        var boughtTripsList : LiveData<List<JSONObject>> = _boughtTripsList


        init {
            viewModelScope.launch {
                if(tripId != ""){
                    trip = FirebaseTripService
                        .getTripData(tripId).asLiveData(this.coroutineContext,5000)
                    bookingslist = FirebaseTripService
                        .getBookingsList(tripId).asLiveData(this.coroutineContext,5000)
                    map = FirebaseTripService
                        .getMappedTripAndBooking(tripId).asLiveData(this.coroutineContext, 5000)
                }

                if(userId != ""){
                    triplist = FirebaseTripService
                        .getTripList(userId).asLiveData(this.coroutineContext, 5000)
                    userTripList = FirebaseTripService
                        .getUsersTripList(userId).asLiveData(this.coroutineContext, 5000)
                    interestingTripsList = FirebaseTripService
                        .getInterestingTripList(userId).asLiveData(this.coroutineContext, 5000)
                    boughtTripsList  = FirebaseTripService
                        .getBoughtTripList(userId).asLiveData(this.coroutineContext, 5000)
                }
            }
        }

    fun setTrip(tripId: String, trip: Map<String, Any>, success: (DocumentReference?)->Unit, failure: (Exception)->Unit){
        val db: FirebaseFirestore = FirebaseFirestore.getInstance()
        if(tripId=="")
            db.collection("trips").add(trip)
                .addOnSuccessListener {
                    success(it)
                }
                .addOnFailureListener{
                    failure(it)
                }
        else
            db.collection("trips").document(tripId).set(trip)
                .addOnSuccessListener {
                    success(null)
                }
                .addOnFailureListener{
                    failure(it)
                }
    }

    fun setBookingState(tripId: String, bookId: String, state: bookState, userId: String){
        FirebaseFirestore.getInstance().collection("trips")
            .document(tripId).collection("bookings")
            .document(bookId).update("state", state.name)
        FirebaseFirestore.getInstance().collection("profiles")
            .document(userId).collection("bookings").whereEqualTo("id", tripId).get()
            .addOnSuccessListener {
                it.documents.forEach { documentSnapshot ->
                    documentSnapshot.reference.update("state", state.name)
                }
            }
            .addOnFailureListener { exception ->
                Log.d(exception.toString(), exception.message ?: "exception raised")
            }
    }



    fun setTripComplete(tripId: String, userId: String, complete: Boolean) {
        FirebaseFirestore.getInstance().collection("profiles")
            .document(userId).collection("bookings").whereEqualTo("id", tripId).get()
            .addOnSuccessListener {
                it.documents.forEach { doc->
                    doc.reference.update("completed", complete)
                }
            }
            .addOnFailureListener { exception ->
                Log.d(exception.toString(), exception.message ?: "exception raised")
            }
    }

    fun deleteAllBookings(){
        if(tripId != ""){
            viewModelScope.launch {
                FirebaseFirestore.getInstance().collection("trips").document(tripId).collection("bookings")
                    .get().addOnSuccessListener {
                        it.documents.forEach { it2 ->
                            it2.reference.delete()
                        }
                    }
                FirebaseFirestore.getInstance().collectionGroup("bookings").whereEqualTo("id", tripId)
                    .get().addOnSuccessListener {
                        it.documents.forEach { it2 ->
                            it2.reference.delete()
                        }
                    }
            }
        }
        else
            Log.d("dbg", "Error on deleting all bookings, tripId is null")
    }

    fun bookTrip(userId: String, success: (DocumentReference?)->Unit, failure: (Exception)->Unit){
        if(tripId!="" && userId!=""){
            FirebaseFirestore.getInstance().collection("profiles").document(userId)
                .get().addOnSuccessListener {
                    val data = mutableMapOf(Pair("profileId", userId),
                        Pair("name", it.data?.get("name")?:""),
                        Pair("state", bookState.PENDING),
                        Pair("passengerRating", -1L),
                        Pair("driverRating", -1L))
                    FirebaseFirestore.getInstance().collection("trips").document(tripId)
                        .collection("bookings").add(data)
                        .addOnSuccessListener(success)
                        .addOnFailureListener(failure)
                }
            FirebaseFirestore.getInstance().collection("trips").document(tripId)
                .get().addOnSuccessListener {
                    val map = mutableMapOf<String, Any>()
                    map.putAll(it.data!!)
                    map["state"] = bookState.PENDING
                    map["id"] = tripId
                    FirebaseFirestore.getInstance().collection("profiles").document(userId).collection("bookings")
                        .add(map)
                        .addOnSuccessListener(success)
                        .addOnFailureListener(failure)
                }
        }
        else
            Log.d("dbg", "Error on deleting all bookings, tripId or userId is null")
    }

    companion object {

        fun addPassengerRating(tId: String, userId: String, rating: Double) {
            FirebaseFirestore.getInstance().collection("trips").document(tId)
                .collection("bookings").whereEqualTo("profileId", userId).get()
                .addOnSuccessListener {
                    it.documents.first().reference.update("passengerRating", rating)
                }
                .addOnFailureListener { exception ->
                    Log.d(exception.toString(), exception.message ?: "exception raised")
                }
        }

        fun addDriverRating(tId: String, userId: String, rating: Double) {
            FirebaseFirestore.getInstance().collection("trips").document(tId)
                .collection("bookings").whereEqualTo("profileId", userId).get()
                .addOnSuccessListener {
                    it.documents.first().reference.update("driverRating", rating)
                }
                .addOnFailureListener { exception ->
                    Log.d(exception.toString(), exception.message ?: "exception raised")
                }
        }

        fun getUserRating(tId: String, userId: String): MutableLiveData<Double>{
            val rating = MutableLiveData(0.0)
            FirebaseFirestore.getInstance().collection("trips").document(tId)
                .collection("bookings").whereEqualTo("profileId", userId).get()
                .addOnSuccessListener {
                    rating.value = it.documents.first().get("driverRating").toString().toDouble()
                }
                .addOnFailureListener { exception ->
                    Log.d(exception.toString(), exception.message ?: "exception raised")
                }
            return rating
        }

    }

    fun unbookTrip(userId: String, success: ()->Unit, failure: (Exception)->Unit){
        if(tripId!="" && userId!=""){
            FirebaseFirestore.getInstance().collection("trips").document(tripId)
                .collection("bookings").whereEqualTo("profileId", userId)
                .get().addOnSuccessListener {
                    it.documents.forEach { it2 ->
                        it2.reference.delete()
                    }
                    success()
                }
                .addOnFailureListener(failure)
            FirebaseFirestore.getInstance().collection("profiles").document(userId)
                .collection("bookings").whereEqualTo("id", tripId)
                .get()
                .addOnSuccessListener {
                    it.documents.forEach { documentSnapshot ->
                        documentSnapshot.reference.delete()
                    }
                    success()
                }
                .addOnFailureListener(failure)
        }
    }


}

class TripViewModelFactory(
    private val tripId: String,
    private val userId: String
): ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return modelClass.getConstructor(String::class.java, String::class.java)
            .newInstance(tripId, userId)
    }
}
