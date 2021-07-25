package it.polito.mad.carpoolingapp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import java.text.DateFormatSymbols
import java.util.*
class SearchFragment : Fragment() {
    private var day: Int = 1
    private var month: Int = 1
    private var year: Int = 1970
    private lateinit var etDate: TextInputEditText
    private lateinit var etDepTimeMin: TextInputEditText
    private lateinit var etDepTimeMax: TextInputEditText
    private lateinit var etArrTimeMin: TextInputEditText
    private lateinit var etArrTimeMax: TextInputEditText
    private lateinit var etDepLocation: TextInputEditText
    private lateinit var etArrLocation: TextInputEditText
    private lateinit var etMinPrice: TextInputEditText
    private lateinit var etMaxPrice: TextInputEditText
    private lateinit var searchButton: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //Setup arrival/departure views
        etDepLocation = view.findViewById(R.id.etSearchDepLoc)
        etArrLocation = view.findViewById(R.id.etSearchArrLoc)

        //Setup date picker
        etDate = view.findViewById(R.id.etSearchDate)
        etDate.setOnClickListener{
            showDatePickerDialog(etDate)
        }

        //Setup minimum departure time picker
        etDepTimeMin = view.findViewById(R.id.etSearchDepTimeMin)
        etDepTimeMin.setOnClickListener{
            showTimePickerDialog(etDepTimeMin)
        }
        etDepTimeMin.setOnFocusChangeListener { v, hasFocus ->
            if(hasFocus && v is TextInputEditText && v.text.toString()==""){
                showTimePickerDialog(v)
            }
        }

        //Setup maximum departure time picker
        etDepTimeMax = view.findViewById(R.id.etSearchDepTimeMax)
        etDepTimeMax.setOnClickListener{
            showTimePickerDialog(etDepTimeMax)
        }
        etDepTimeMax.setOnFocusChangeListener { v, hasFocus ->
            if(hasFocus && v is TextInputEditText && v.text.toString()==""){
                showTimePickerDialog(v)
            }
        }

        //Setup minimum arrival time picker
        etArrTimeMin = view.findViewById(R.id.etSearchArrTimeMin)
        etArrTimeMin.setOnClickListener{
            showTimePickerDialog(etArrTimeMin)
        }
        etArrTimeMin.setOnFocusChangeListener { v, hasFocus ->
            if(hasFocus && v is TextInputEditText && v.text.toString()==""){
                showTimePickerDialog(v)
            }
        }

        //Setup maximum arrival time picker
        etArrTimeMax = view.findViewById(R.id.etSearchArrTimeMax)
        etArrTimeMax.setOnClickListener{
            showTimePickerDialog(etArrTimeMax)
        }
        etArrTimeMax.setOnFocusChangeListener { v, hasFocus ->
            if(hasFocus && v is TextInputEditText && v.text.toString()==""){
                showTimePickerDialog(v)
            }
        }

        //Setup min/max price views
        etMinPrice = view.findViewById(R.id.etSearchPriceMin)
        etMaxPrice = view.findViewById(R.id.etSearchPriceMax)

        //Setup search submit button
        searchButton = view.findViewById(R.id.searchSubmitButton)
        searchButton.setOnClickListener {
            val bundle = Bundle().apply {
                putString("depLoc", etDepLocation.text.toString())
                putString("arrLoc", etArrLocation.text.toString())
                putString("depDate", etDate.text.toString())
                putString("depTimeMin", etDepTimeMin.text.toString())
                putString("depTimeMax", etDepTimeMax.text.toString())
                putString("arrTimeMin", etArrTimeMin.text.toString())
                putString("arrTimeMax", etArrTimeMax.text.toString())
                putString("priceMin", etMinPrice.text.toString())
                putString("priceMax", etMaxPrice.text.toString())

            }
            findNavController().navigate(R.id.action_searchFragment_to_nav_others_list, bundle)
        }
    }

    private fun showDatePickerDialog(editText: TextInputEditText) {
        val c: Calendar = Calendar.getInstance()
        val cday = c.get(Calendar.DAY_OF_MONTH)
        val cmonth = c.get(Calendar.MONTH)
        val cyear = c.get(Calendar.YEAR)

        val datePicker = DatePickerDialog(activity as Context, R.style.SpinnerDatePickerDialog, { _: DatePicker?, selectedYear: Int, selectedMonth: Int, selectedDay: Int ->
            editText.setText(getString(R.string.dateFormat, selectedDay, DateFormatSymbols().months[selectedMonth], selectedYear))
            day = selectedDay
            month = selectedMonth
            year = selectedYear
        }, cyear, cmonth, cday)

        datePicker.setTitle(getString(R.string.pickDate))
        datePicker.show()
    }

    private fun showTimePickerDialog(editText: TextInputEditText) {
        val prevTime = TimeT.parseTimeT(editText.text.toString())
        val c: Calendar = Calendar.getInstance()
        val hour = if(editText.text.toString() != "") prevTime.hours else c.get(Calendar.HOUR_OF_DAY)
        val minute = if(editText.text.toString() != "") prevTime.minutes else c.get(Calendar.MINUTE)

        val timePicker = TimePickerDialog(activity as Context, R.style.SpinnerTimePicker,
            { _: TimePicker?, selectedHour: Int, selectedMinute: Int ->
                editText.setText(getString(R.string.timeFormat, selectedHour, selectedMinute))
            }, hour, minute, true)

        timePicker.setTitle(getString(R.string.pickTime))
        timePicker.show()
    }
}
