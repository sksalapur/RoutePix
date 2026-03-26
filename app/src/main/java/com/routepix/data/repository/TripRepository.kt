package com.routepix.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.routepix.data.model.PhotoMeta
import com.routepix.data.model.Trip
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await


class TripRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    fun getTripsForCurrentUser(): Flow<List<Trip>> = callbackFlow {
        val uid = auth.currentUser?.uid ?: ""
        val reg = firestore.collection("trips")
            .whereArrayContains("memberUids", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.toObjects(Trip::class.java) ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun createTrip(name: String): String {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        val userDoc = firestore.collection("users").document(uid).get().await()
        val botToken = userDoc.getString("telegramBotToken")
        val chatId = userDoc.getString("telegramChatId")

        val tripId = firestore.collection("trips").document().id
        val inviteCode = generateInviteCode()

        val trip = Trip(
            tripId = tripId,
            adminUid = uid,
            name = name,
            inviteCode = inviteCode,
            memberUids = listOf(uid),
            telegramBotToken = botToken,
            telegramChatId = chatId
        )

        firestore.collection("trips").document(tripId).set(trip).await()
        return tripId
    }

    suspend fun joinTrip(inviteCode: String): String {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val snap = firestore.collection("trips")
            .whereEqualTo("inviteCode", inviteCode)
            .get().await()

        if (snap.isEmpty) throw IllegalArgumentException("Invalid invite code")
        
        val doc = snap.documents.first()
        val tripId = doc.id
        
        firestore.collection("trips").document(tripId)
            .update("memberUids", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
            .await()
            
        return tripId
    }


    fun getPhotosForTrip(tripId: String): Flow<List<PhotoMeta>> = callbackFlow {
        val reg = firestore.collection("trips").document(tripId)
            .collection("photos")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.toObjects(PhotoMeta::class.java) ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun uploadPhotoMeta(tripId: String, photo: PhotoMeta) {
        val docId = firestore.collection("trips").document(tripId)
            .collection("photos").document().id
        
        val finalPhoto = photo.copy(photoId = docId)
        firestore.collection("trips").document(tripId)
            .collection("photos").document(docId)
            .set(finalPhoto).await()
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    suspend fun exitTrip(tripId: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("trips").document(tripId)
            .update("memberUids", com.google.firebase.firestore.FieldValue.arrayRemove(uid))
            .await()
    }

    suspend fun deletePhoto(tripId: String, photoId: String) {
        firestore.collection("trips").document(tripId)
            .collection("photos").document(photoId)
            .delete()
            .await()
    }

    
    suspend fun syncCredentialsToTrips(botToken: String?, chatId: String?) {
        val uid = auth.currentUser?.uid ?: return
        val trips = firestore.collection("trips")
            .whereEqualTo("adminUid", uid)
            .get()
            .await()
        
        val batch = firestore.batch()
        for (doc in trips.documents) {
            batch.update(doc.reference, mapOf(
                "telegramBotToken" to botToken,
                "telegramChatId" to chatId
            ))
        }
        batch.commit().await()
    }
}

