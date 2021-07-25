package it.polito.mad.carpoolingapp

import android.annotation.SuppressLint
import android.content.Context
import android.media.Image
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.lifecycle.withCreated
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.*
import org.mapsforge.map.controller.MapViewController
import org.osmdroid.api.IGeoPoint
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.OverlayItem
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.schedule

class TripRoute : Fragment() {
    private var points= ArrayList<IGeoPoint>()
    private var overlays = ArrayList<OverlayItem>()
    private lateinit var map: MapView
    private lateinit var tvHeader : TextView
    private lateinit var rvInstr : RecyclerView
    private var job : Job? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        arguments?.let {
            overlays = it.get("geoPoints") as ArrayList<OverlayItem>
            points = overlays.map { it.point } as ArrayList<IGeoPoint>
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.edit_show_profile_menu, menu)
        menu.removeItem(R.id.itemEdit)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.itemInfo -> {
                fragmentManager?.let { TutorialDialogFragment(R.string.routeDialog, "showRouteTutorial").show(it, "RouteTutorialDialog") }
                true
            } else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(R.layout.fragment_trip_route, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pref = activity?.getPreferences(Context.MODE_PRIVATE)
        if(pref != null){
            val show = pref.getBoolean("showRouteTutorial", true)
            if(show)
                fragmentManager?.let { TutorialDialogFragment(R.string.routeDialog, "showRouteTutorial").show(it, "RouteTutorialDialog") }
        }
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        map = view.findViewById(R.id.routeMap)
        job = MainScope().launch {
            withContext(Dispatchers.IO){
                updateRoad(requireContext(), map, points)
            }
        }

        map.setTileSource(TileSourceFactory.MAPNIK)
        val rotationGestureOverlay = RotationGestureOverlay(context, map)
        rotationGestureOverlay.isEnabled
        map.setMultiTouchControls(true)
        map.overlays.add(rotationGestureOverlay)
        overlays.forEach { addMarker(map, it, requireContext()) }

        tvHeader = view.findViewById<TextView>(R.id.tvHeader)
        rvInstr = view.findViewById<RecyclerView>(R.id.rvInstr)
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun getMapIcon(type: Int): Int{
        when(type){
            0, 2, 23, 35, 36, 37, 38, 39 -> return -1
            1, 11, 19, 22 -> return R.drawable.up_arrow
            3, 5, 17, 20 -> return R.drawable.left_curved
            6, 8, 18, 21 -> return R.drawable.right_curved
            4, 15 -> return R.drawable.left
            7, 16 -> return R.drawable.right
            12, 13, 14 -> return R.drawable.u_turn
            24, 25, 26 -> return R.drawable.arrival_marker
            27, 28, 29, 30, 31, 32, 33, 34 -> return R.drawable.roundabout
            9 -> return R.drawable.diagonal_left
            10 -> return R.drawable.diagonal_right
            40 -> return R.drawable.stop_marker
        }
        return -1
    }

    private fun updateRoad(ctx: Context, map: MapView, points: ArrayList<IGeoPoint>){

        val rm = OSRMRoadManager(ctx, "")

        val result = rm.getRoads( points.fold(ArrayList<GeoPoint>()){ acc, item->
                acc.add(GeoPoint(item.latitude, item.longitude))
                acc
            } )[0]


        MainScope().launch {
            if(result?.mStatus == Road.STATUS_OK ) {
                val overlay = OSRMRoadManager.buildRoadOverlay(result)
                map.overlays.add(overlay)
                map.invalidate()
                fixZoom(map.controller, points, map)
                tvHeader.text = result.getLengthDurationText(requireContext(), -1)
                rvInstr.layoutManager = LinearLayoutManager(requireContext())
                result.mNodes.removeAt(0)
                rvInstr.adapter = InstructionAdapter(result.mNodes.foldIndexed(ArrayList<Instruction>()) { i, acc, item ->
                    val isStop = (item.mManeuverType == 24 || item.mManeuverType == 25 || item.mManeuverType == 26)
                    if(!(item.mManeuverType == 0 || ( isStop && item.mDuration==0.0 && item.mLength == 0.0 && i!=result.mNodes.size-1))){
                        acc.add(Instruction(getMapIcon(if (isStop && i!=result.mNodes.size-1) 40 else item.mManeuverType ),
                            "${item.mInstructions} \n ${Road.getLengthDurationText(requireContext(), item.mLength, item.mDuration)}",
                            item.mLocation))
                    }
                    acc
                })
            }
        }

    }

    private data class Instruction(val icon: Int, val instr: String, val point: GeoPoint)

    private inner class InstructionAdapter(val data: ArrayList<Instruction>): RecyclerView.Adapter<InstructionAdapter.InstructionViewHolder>(){
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): InstructionAdapter.InstructionViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.instruction_layout, parent, false)

            return InstructionViewHolder(v)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(
            holder: InstructionAdapter.InstructionViewHolder,
            position: Int
        ) {
            val i = data[position]
            holder.itemView.setOnTouchListener { v, event ->
                if(event.actionMasked == MotionEvent.ACTION_UP) {
                    map.controller.animateTo(i.point, 19.5, 2000)
                    val nodeMarker = Marker(map)
                    nodeMarker.position = i.point
                    nodeMarker.icon =
                        ContextCompat.getDrawable(requireContext(), R.drawable.trip_origin_icon)
                    map.overlays.add(nodeMarker)
                    Timer("", true).schedule(2500) {
                        map.overlays.remove(nodeMarker)
                    }
                }
                true
            }
            holder.bind(i, position)
        }

        override fun getItemCount(): Int {
            return data.size
        }

        inner class InstructionViewHolder(view: View): RecyclerView.ViewHolder(view){
            private val ivIcon = view.findViewById<ImageView>(R.id.ivIcon)
            private val tvInstr = view.findViewById<TextView>(R.id.tvInstr)

            fun bind(item: Instruction, pos: Int){
                if(item.icon != -1)
                    ivIcon.setImageBitmap(ContextCompat.getDrawable(requireContext(), item.icon)?.toBitmap())
                tvInstr.text = item.instr
            }
        }
    }

}