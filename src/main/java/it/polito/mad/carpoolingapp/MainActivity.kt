package it.polito.mad.carpoolingapp

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceManager
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.view.get
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.bumptech.glide.signature.ObjectKey
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONObject
import org.osmdroid.config.Configuration.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private var account: GoogleSignInAccount? = null
    private var auth: FirebaseAuth? = null
    private var user: FirebaseUser? = null
    private var googleSignInClient: GoogleSignInClient? = null
    private lateinit var headerView: View
    private lateinit var profile: String
    @ExperimentalCoroutinesApi
    private lateinit var viewModel : UserProfileViewModel

    @ExperimentalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)


        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(setOf(
                R.id.nav_trip_list, R.id.nav_show_profile, R.id.nav_others_list, R.id.nav_interesting_list, R.id.nav_bought_list), drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        //Authentication init
        auth = FirebaseAuth.getInstance()
        user = auth?.currentUser
        profile = auth?.currentUser?.uid!!
        googleSignInClient = GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN)

        setUpNavView(navView)

    }

    override fun onStart() {
        super.onStart()
        account = GoogleSignIn.getLastSignedInAccount(this)
        user = auth?.currentUser
        if (account == null) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    //Setup navigation header to show user information
    @ExperimentalCoroutinesApi
    private fun setUpNavView(navView: NavigationView) {
        headerView = navView.getHeaderView(0)
        headerView.findViewById<ImageButton>(R.id.logout).setOnClickListener {
            AlertDialog.Builder(this)
                .setPositiveButton(R.string.yesConfirm) { dialogInterface: DialogInterface, i: Int ->
                    googleSignInClient?.signOut()?.addOnCompleteListener {
                        if (it.isSuccessful) {
                            auth?.signOut()
                            val intent = Intent(this, LoginActivity::class.java)
                            startActivity(intent)
                        }
                    }
                }
                .setNegativeButton(R.string.noConfirm) { dialogInterface: DialogInterface, i: Int ->
                    dialogInterface.dismiss()
                }
                .setMessage(R.string.logoutConfirm).show()
        }
        val factory = UserViewModelFactory(profile)
        viewModel = ViewModelProviders.of(this, factory).get(UserProfileViewModel::class.java)
        viewModel.user.observe(this){
            if(it!=null && it.toString()!="{}")
                populate(it, navView)
            else
                findNavController(R.id.nav_host_fragment).navigate(R.id.action_nav_others_list_to_nav_edit_profile)
        }
    }

    private fun populate(json: JSONObject, view: NavigationView): Unit {
        headerView.findViewById<TextView>(R.id.userName).text = if(json.has("name")) json.getString("name").orEmpty() else ""
        headerView.findViewById<TextView>(R.id.userMail).text = if(json.has("email")) json.getString("email").orEmpty() else ""

        val storageReference = Firebase.storage.reference.child("/profileImages/${profile}.jpg")
        storageReference.metadata.addOnSuccessListener {
            GlideApp.with(this)
                .load(storageReference)
                .signature(ObjectKey(it.updatedTimeMillis.toString()))
                .into(view.findViewById(R.id.carImage))
        }.addOnFailureListener{
            Log.d("dbg", "Profile picture download error")
        }

    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}