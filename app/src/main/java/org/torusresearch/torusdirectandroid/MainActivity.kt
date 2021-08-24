package org.torusresearch.torusdirectandroid

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import org.torusresearch.torusdirect.TorusDirectSdk
import org.torusresearch.torusdirect.types.DirectSdkArgs
import org.torusresearch.torusdirect.types.TorusNetwork

class MainActivity : AppCompatActivity() {
    companion object {
        const val RC_SIGN_IN = 1
    }

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var torusDirectClient: TorusDirectSdk

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val signInButton = findViewById<SignInButton>(R.id.sign_in_button)
        signInButton.setSize(SignInButton.SIZE_WIDE)
        signInButton.setOnClickListener { onClickSignIn() }

        val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestId()
            .requestEmail()
            .requestProfile()
            .requestIdToken(getString(R.string.google_client_id))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions)
    }

    override fun onStart() {
        super.onStart()

        // Always do fresh login to avoid duplicated token
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            googleSignInClient.signOut()
                .addOnCompleteListener {
                    Log.d(javaClass.name, "sign_out:success")
                }
        }

        // Initialize Torus Direct
        val torusDirectOptions = DirectSdkArgs(
            "https://scripts.toruswallet.io/redirect.html",
            TorusNetwork.TESTNET
        )
        torusDirectClient = TorusDirectSdk(torusDirectOptions, this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            RC_SIGN_IN -> {
                val task: Task<GoogleSignInAccount> =
                    GoogleSignIn.getSignedInAccountFromIntent(data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    onChangeAccount(account)
                } catch (error: ApiException) {
                    Log.w(javaClass.name, "sign_in:failure=${error}")
                    onChangeAccount(null)
                }
            }
        }
    }

    private fun onClickSignIn() {
        startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
    }

    private fun onChangeAccount(account: GoogleSignInAccount?) {
        if (account == null) return

        Log.d(javaClass.name, "sign_in:success=${account.email}")

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                javaClass.canonicalName,
                account.idToken
            )
        )
        Toast.makeText(this, "Signed in as ${account.displayName}", Toast.LENGTH_SHORT).show()

        torusDirectClient.getTorusKey(
            getString(R.string.torus_verifier_name),
            account.email,
            hashMapOf("verifier_id" to account.email),
            account.idToken
        ).whenComplete { keypair, error ->
            if (error != null) {
                Log.w(javaClass.name, "get_key:failure=${error}")
            } else {
                Log.d(javaClass.name, "get_key:success=${keypair.publicAddress}")
            }
        }
    }
}