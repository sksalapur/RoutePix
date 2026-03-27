package com.routepix.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.routepix.data.model.User
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val usersCollection = firestore.collection("users")

    suspend fun getCurrentUser(): User? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            usersCollection.document(uid).get().await().toObject(User::class.java)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveUser(user: User) {
        if (user.uid.isBlank()) return
        usersCollection.document(user.uid).set(user).await()
    }

    suspend fun updateProfile(
        displayName: String,
        botToken: String?,
        chatId: String?,
        showDownloadedPhotosInGallery: Boolean
    ) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).update(
            mapOf(
                "displayName" to displayName,
                "telegramBotToken" to botToken,
                "telegramChatId" to chatId,
                "showDownloadedPhotosInGallery" to showDownloadedPhotosInGallery
            )
        ).await()
    }
}
