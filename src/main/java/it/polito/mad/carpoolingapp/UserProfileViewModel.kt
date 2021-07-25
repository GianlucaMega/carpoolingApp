package it.polito.mad.carpoolingapp

import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

@ExperimentalCoroutinesApi
class UserProfileViewModel(
    val uid: String
) : ViewModel() {
    var userId : String = uid
    private val _user = MutableLiveData<JSONObject>()
    var user : LiveData<JSONObject> = _user
    var bookings = MutableLiveData<List<JSONObject>>()

    init {
        viewModelScope.launch {
            user = FirebaseProfileService
                .getProfileData(userId).asLiveData(this.coroutineContext,5000)
        }
    }

    fun setProfile(uid: String, profile: Map<String, Any>, success: ()->Unit, failure: (Exception)->Unit){
        val db: FirebaseFirestore = FirebaseFirestore.getInstance()
        db.collection("profiles")
            .document(uid)
            .set(profile)
            .addOnSuccessListener {
                success()
            }
            .addOnFailureListener {
                failure(it)
            }
    }

    fun getRatings(asDriver: Boolean): LiveData<List<JSONObject>>{
        viewModelScope.launch{
            bookings = FirebaseProfileService.getUserRatings(userId, asDriver).asLiveData(this.coroutineContext,5000) as MutableLiveData<List<JSONObject>>
        }
        return bookings
    }

    companion object {

        fun addPassengerRating(uid: String, stars: Double, comment: String, rater: String) {
            FirebaseFirestore.getInstance().collection("profiles")
                .document(uid)
                .update("passengerTotalStars", FieldValue.increment(stars))
                .addOnFailureListener { exception ->
                    Log.d(exception.toString(), exception.message ?: "exception raised")
                }
            FirebaseFirestore.getInstance().collection("profiles")
                .document(uid)
                .update("passengerNumberOfRatings", FieldValue.increment(1L))
                .addOnFailureListener { exception ->
                    Log.d(exception.toString(), exception.message ?: "exception raised")
                }

            val rate = mutableMapOf<String, Any>()
            rate["rater"] = rater
            rate["stars"] = stars
            rate["comment"] = comment
            rate["isDriver"] = false
            FirebaseFirestore.getInstance().collection("profiles")
                .document(uid).collection("ratings").add(rate)
                .addOnFailureListener { exception ->
                    Log.d(exception.toString(), exception.message ?: "exception raised")
                }
        }

        fun addDriverRating(uid: String, stars: Double, comment: String, rater: String) {
            FirebaseFirestore.getInstance().collection("profiles")
                .document(uid)
                .update("driverTotalStars", FieldValue.increment(stars))
                .addOnFailureListener { exception ->
                    Log.d(exception.toString(), exception.message ?: "exception raised")
                }
            FirebaseFirestore.getInstance().collection("profiles")
                .document(uid)
                .update("driverNumberOfRatings", FieldValue.increment(1L))
                .addOnFailureListener { exception ->
                    Log.d(exception.toString(), exception.message ?: "exception raised")
                }

            MainScope().async {
                val res =
                    FirebaseFirestore.getInstance().collection("profiles").document(rater).get()
                        .await()
                val name = res.data?.get("name").toString()
                val rate = mutableMapOf<String, Any>()
                rate["rater"] = name
                rate["stars"] = stars
                rate["comment"] = comment
                rate["isDriver"] = true
                FirebaseFirestore.getInstance().collection("profiles")
                    .document(uid).collection("ratings").add(rate)
                    .addOnFailureListener { exception ->
                        Log.d(exception.toString(), exception.message ?: "exception raised")
                    }
            }
        }



    }

}

class UserViewModelFactory(
    val userId: String
): ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return modelClass.getConstructor(String::class.java)
            .newInstance(userId)
    }

}