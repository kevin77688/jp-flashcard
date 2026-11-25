package com.kevin77688.jpflashcard.data

import android.app.Activity
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.kevin77688.jpflashcard.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Google sign-in through Credential Manager, then Firebase Auth with the Google ID token. */
class AuthRepository {
    private val auth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    val userFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(activity: Activity): Result<FirebaseUser> = runCatching {
        // The web client (OAuth type 3) is what Firebase Auth expects as the token audience.
        val serverClientId = activity.getString(R.string.default_web_client_id)
        val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = CredentialManager.create(activity).getCredential(activity, request)
        val credential = response.credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
        ) { "Unexpected credential type: ${credential.type}" }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        requireNotNull(result.user)
    }.onFailure { Log.w(TAG, "Sign-in failed", it) }

    suspend fun signOut(activity: Activity) {
        auth.signOut()
        runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
