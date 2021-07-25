package it.polito.mad.carpoolingapp

import android.util.Log
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject

object FirebaseTripService {
    private const val TAG = "dbg"

    @ExperimentalCoroutinesApi
    suspend fun getTripData(tripId: String): Flow<JSONObject> {
        val db = FirebaseFirestore.getInstance()
        return callbackFlow {
            val listenerRegistration = db.collection("trips")
                .document(tripId)
                .addSnapshotListener { snap: DocumentSnapshot?, exc: FirebaseFirestoreException? ->
                    if (exc != null) {
                        cancel(
                            message = "Error fetching trip",
                            cause = exc
                        )
                        return@addSnapshotListener
                    }
                    val profile = snap?.data?.get("profileId").toString()
                    db.collection("profiles")
                        .document(profile)
                        .addSnapshotListener { snap2: DocumentSnapshot?, _: FirebaseFirestoreException? ->

                            val json = JSONObject(snap?.data ?: mutableMapOf<String, Any>())
                            if (snap2 != null) {
                                json.put("cardName", snap2.data?.get("name").toString())
                            }
                            if (isActive) offer(json)
                        }
                }
            awaitClose {
                Log.d(TAG, "Cancelling trip listener")
                listenerRegistration.remove()
            }
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun getTripList(userId: String): Flow<List<JSONObject>> {
        val db = FirebaseFirestore.getInstance()
        return callbackFlow {
            val listenerRegistration = db.collection("trips")
                .whereEqualTo("advertised", true).whereNotEqualTo("profileId", userId)
                .addSnapshotListener { snap: QuerySnapshot?, exc: FirebaseFirestoreException? ->
                    if (exc != null) {
                        cancel(
                            message = "Error fetching trip",
                            cause = exc
                        )
                        return@addSnapshotListener
                    }

                    val list = snap?.documents?.map { JSONObject(it?.data).put("id", it?.id) }
                    if (isActive) offer(list!!)
                }
            awaitClose {
                Log.d(TAG, "Cancelling trip listener")
                listenerRegistration.remove()
            }
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun getUsersTripList(uid: String): Flow<List<JSONObject>> {
        val db = FirebaseFirestore.getInstance()
        return callbackFlow {
            val listenerRegistration = db.collection("trips").whereEqualTo("profileId", uid)
                .addSnapshotListener { snap: QuerySnapshot?, exc: FirebaseFirestoreException? ->
                    if (exc != null) {
                        cancel(
                            message = "Error fetching trip",
                            cause = exc
                        )
                        return@addSnapshotListener
                    }

                    val list = snap?.documents?.map { JSONObject(it?.data).put("id", it?.id) }
                    if (isActive) offer(list!!)
                }
            awaitClose {
                Log.d(TAG, "Cancelling trip listener")
                listenerRegistration.remove()
            }
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun getInterestingTripList(uid: String): Flow<List<JSONObject>> {
        val db = FirebaseFirestore.getInstance()
        return callbackFlow {
            val listenerRegistration = db.collection("profiles").document(uid)
                .collection("bookings").whereEqualTo("state", bookState.PENDING.name)
                .addSnapshotListener { value, error ->
                    if (error != null) {
                        cancel(
                            message = "Error fetching bookings",
                            cause = error
                        )
                        return@addSnapshotListener
                    }
                    val list = value?.documents?.map { JSONObject(it.data)}
                    if (isActive) offer(list!!)
                }
            awaitClose {
                Log.d(TAG, "Cancelling interesting trips listener")
                listenerRegistration.remove()
            }
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun getBoughtTripList(uid: String): Flow<List<JSONObject>> {
        val db = FirebaseFirestore.getInstance()
        return callbackFlow {
            val listenerRegistration = db.collection("profiles").document(uid)
                .collection("bookings").whereIn("state", mutableListOf(bookState.COMPLETED.name, bookState.CONFIRMED.name))
                .addSnapshotListener { value, error ->
                    if (error != null) {
                        cancel(
                            message = "Error fetching bookings",
                            cause = error
                        )
                        return@addSnapshotListener
                    }
                    val list = value?.documents?.map { JSONObject(it.data)}
                    if (isActive) offer(list!!)
                }
            awaitClose {
                Log.d(TAG, "Cancelling booked trips listener")
                listenerRegistration.remove()
            }
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun getBookingsList(tripId: String): Flow<List<InterestedUserDetails>> {
        val db = FirebaseFirestore.getInstance()
        return callbackFlow {
            val listenerRegistration =
                db.collection("trips").document(tripId).collection("bookings")
                    .addSnapshotListener { snap: QuerySnapshot?, exc: FirebaseFirestoreException? ->
                        if (exc != null) {
                            cancel(
                                message = "Error fetching trip bookings",
                                cause = exc
                            )
                            return@addSnapshotListener
                        }
                        var list = listOf<InterestedUserDetails>()
                        if (snap != null)
                            list = snap.documents.map {
                                InterestedUserDetails(
                                    it.id,
                                    it.data?.get("profileId").toString(),
                                    it.data?.get("name").toString(),
                                    it.data?.get("state").toString(),
                                    if(it.data?.get("passengerRating") != null) it.data?.get("passengerRating").toString().toDouble() else -1.0,
                                    if(it.data?.get("driverRating") != null) it.data?.get("driverRating").toString().toDouble() else -1.0
                                )
                            }

                        if (isActive) offer(list)
                    }
            awaitClose {
                Log.d(TAG, "Cancelling trip bookings listener")
                listenerRegistration.remove()
            }
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun getMappedTripAndBooking(tripId: String): Flow<Map<String, Any>> {
        val db = FirebaseFirestore.getInstance()
        return callbackFlow {
            val map = mutableMapOf<String, Any>()
            val listenerRegistration =
                db.collection("trips").document(tripId).collection("bookings")
                    .addSnapshotListener { snap: QuerySnapshot?, exc: FirebaseFirestoreException? ->
                        if (exc != null) {
                            cancel(
                                message = "Error fetching bookings",
                                cause = exc
                            )
                            return@addSnapshotListener
                        }

                        val list: List<InterestedUserDetails>
                        if (snap != null) {
                            list = snap.documents.map {
                                InterestedUserDetails(
                                    it.data?.get("id").toString(),
                                    it.data?.get("profileId").toString(),
                                    it.data?.get("name").toString(),
                                    it.data?.get("state").toString(),
                                    if(it.data?.get("passengerRating") != null) it.data?.get("passengerRating").toString().toDouble() else -1.0,
                                    if(it.data?.get("driverRating") != null) it.data?.get("driverRating").toString().toDouble() else -1.0
                                )}

                            db.collection("trips").document(tripId)
                                .addSnapshotListener { snap2: DocumentSnapshot?, _: FirebaseFirestoreException? ->
                                    if (snap2 != null) {
                                        val json =
                                            JSONObject(snap2.data ?: mutableMapOf<String, Any>())

                                        val profile = json.get("profileId").toString()
                                        db.collection("profiles").document(profile)
                                            .addSnapshotListener { snap3: DocumentSnapshot?, _: FirebaseFirestoreException? ->
                                                if (snap3 != null) {
                                                    json.put(
                                                        "cardName",
                                                        snap3.data?.get("name").toString()
                                                    )

                                                    map["bookingslist"] = list
                                                    map["trip"] = json

                                                    if (isActive) offer(map)
                                                }
                                            }
                                    }
                                }

                        }
                    }
            awaitClose {
                Log.d(TAG, "Cancelling trip bookings listener")
                listenerRegistration.remove()
            }
        }
    }

}
