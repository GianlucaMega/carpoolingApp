package it.polito.mad.carpoolingapp

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject

object FirebaseProfileService {
    private const val TAG = "dbg"

    @ExperimentalCoroutinesApi
    suspend fun getProfileData(userId: String): Flow<JSONObject> {
        val db = FirebaseFirestore.getInstance()
        return callbackFlow {
            val listenerRegistration = db.collection("profiles")
                .document(userId)
                .addSnapshotListener{ snap: DocumentSnapshot?, exc: FirebaseFirestoreException? ->
                    if(exc != null){
                        cancel(message = "Error fetching user",
                        cause = exc)
                        return@addSnapshotListener
                    }

                    if (isActive) offer(JSONObject(snap?.data?: mutableMapOf<String, Any>()))
                }
            awaitClose{
                Log.d(TAG, "Cancelling user listener")
                listenerRegistration.remove()
            }
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun getUserRatings(userId: String, asDriver: Boolean): Flow<List<JSONObject>> {
        val db = FirebaseFirestore.getInstance()
        return callbackFlow {
            val listenerRegistration = db.collection("profiles")
                .document(userId).collection("ratings").whereEqualTo("isDriver", asDriver)
                .addSnapshotListener { snap: QuerySnapshot?, exc: FirebaseFirestoreException? ->
                    if (exc != null) {
                        cancel(
                            message = "Error fetching ratings",
                            cause = exc
                        )
                        return@addSnapshotListener
                    }

                    val list = snap?.documents?.map { JSONObject(it?.data) }
                    if (isActive && list?.isNotEmpty() == true) offer(list)
                }
            awaitClose {
                Log.d(TAG, "Cancelling ratings listener")
                listenerRegistration.remove()
            }
        }
    }
}
