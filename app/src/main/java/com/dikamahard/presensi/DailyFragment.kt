package com.dikamahard.presensi

import android.app.DatePickerDialog
import android.graphics.Typeface
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.text.Layout
import android.util.Log
import android.view.Gravity
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import android.widget.HorizontalScrollView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.disklrucache.DiskLruCache.Value
import com.dikamahard.presensi.databinding.FragmentDailyBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.math.log

class DailyFragment : Fragment() {

    private lateinit var viewModel: DailyViewModel
    private lateinit var binding: FragmentDailyBinding
    private var database = Firebase.database
    private val calendar = Calendar.getInstance()



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentDailyBinding.inflate(inflater, container, false)
        return binding.root
    }
/*
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(DailyViewModel::class.java)
        // TODO: Use the ViewModel
    }

 */

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[DailyViewModel::class.java]
        // TODO: Use the ViewModel

        //val table = binding.tblReport
        val dateNow = "05-31-2024"      //DD-MM-YYYY

        lateinit var userId: String
        lateinit var timeIn: String
        lateinit var timeOut: String
        //lateinit var dateTest: String


        val time = Calendar.getInstance().time
        var formatter = SimpleDateFormat("MM-dd-yyyy")
        val currentDate = formatter.format(time)

        val mappedUser = mutableMapOf<String, String>()

        val currentDateReference = database.reference.child("dates").child(dateNow)

        binding.btnDatePicker.setOnClickListener {
            showDatePicker(viewModel)
            Log.d("TAG", "how to get value here")
        }

        val tableLayout = TableLayout(requireContext()).apply {
            layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
            isStretchAllColumns = true
        }

////////////////// experiment start


        // Header Row
        /*
        val headerRow = TableRow(requireContext()).apply {
            setBackgroundColor(resources.getColor(R.color.green))
            addView(TextView(requireContext()).apply {
                layoutParams = TableRow.LayoutParams(100, TableRow.LayoutParams.WRAP_CONTENT)
                text = "Name"
                textSize = 16f
                //setTextAppearance(android.R.style.TextAppearance_Material_Widget_Button_Borderless_Colored)
                setTypeface(null, Typeface.BOLD)
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(15, 5, 5,  5)
            })
            addView(TextView(context).apply {
                layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT).apply {
                    span = 2
                }
                text = "09-05-2024"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(5, 5, 5,  5)
            })
        }
        tableLayout.addView(headerRow)

         */

        // TODO: Test UI LOOK WITH THIS CODE
        /*
                val dataRow = TableRow(requireContext()).apply {
                    setBackgroundColor(resources.getColor(R.color.plain))
                    setPadding(5, 5, 5, 5)
                    addView(TextView(context).apply {
                        layoutParams = TableRow.LayoutParams(100, TableRow.LayoutParams.WRAP_CONTENT)
                        text = "Rizky Mahardika"
                    })
                    addView(TextView(context).apply {
                        layoutParams = TableRow.LayoutParams(50, TableRow.LayoutParams.WRAP_CONTENT)
                        text = "08.00"
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                    })
                    addView(TextView(context).apply {
                        layoutParams = TableRow.LayoutParams(50, TableRow.LayoutParams.WRAP_CONTENT)
                        text = "__.__"
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                    })
                }

                val dataRow2 = TableRow(requireContext()).apply {
                    setBackgroundColor(resources.getColor(R.color.plain))
                    setPadding(5, 5, 5, 5)
                    addView(TextView(context).apply {
                        layoutParams = TableRow.LayoutParams(100, TableRow.LayoutParams.WRAP_CONTENT)
                        text = "Rizky Hariyanto"
                    })
                    addView(TextView(context).apply {
                        layoutParams = TableRow.LayoutParams(50, TableRow.LayoutParams.WRAP_CONTENT)
                        text = "08.00"
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                    })
                    addView(TextView(context).apply {
                        layoutParams = TableRow.LayoutParams(50, TableRow.LayoutParams.WRAP_CONTENT)
                        text = "09:34"
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                    })
                }
                tableLayout.addView(headerRow)
                tableLayout.addView(dataRow)
                tableLayout.addView(dataRow2)
                binding.viewHorizontal.addView(tableLayout)

         */

//        binding.btnDatePicker.setOnClickListener {
//            while (tableLayout.childCount > 1) {
//                tableLayout.removeView(tableLayout.getChildAt(tableLayout.childCount - 1))
//            }
//        }
////////////////////// end experiment



        var mappingNames = true
        lifecycleScope.launch(Dispatchers.Default) {
            val snapshotUser = database.reference.child("users").get().await()

            for(user in snapshotUser.children) {
                for (name in user.children) {
                    mappedUser[user.key.toString()] = name.value.toString()
                }
            }

            Log.d("TAG", "map: $mappedUser")
            mappingNames = false
        }

        viewModel.selectedDate.observe(viewLifecycleOwner) {selectedDate ->

            binding.viewHorizontal.removeView(tableLayout)

            val headerRow = TableRow(requireContext()).apply {
                setBackgroundColor(resources.getColor(R.color.green))
                addView(TextView(requireContext()).apply {
                    layoutParams = TableRow.LayoutParams(100, TableRow.LayoutParams.WRAP_CONTENT)
                    text = "Name"
                    textSize = 16f
                    //setTextAppearance(android.R.style.TextAppearance_Material_Widget_Button_Borderless_Colored)
                    setTypeface(null, Typeface.BOLD)
                    textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                    setPadding(15, 5, 5,  5)
                })
                addView(TextView(context).apply {
                    layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT).apply {
                        span = 2
                    }
                    text = selectedDate
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                    setPadding(5, 5, 5,  5)
                })
            }
            tableLayout.addView(headerRow)
            binding.viewHorizontal.addView(tableLayout)

            lifecycleScope.launch(Dispatchers.Default) {
                while (mappingNames) {
                    // TODO: loading
                    Log.d("TAG", "Loading")
                }
                Log.d("TAG", "Loading done")

                while (tableLayout.childCount > 1) {
                    tableLayout.removeView(tableLayout.getChildAt(tableLayout.childCount - 1))
                }

                for (name in mappedUser) {
                    val userTime = database.reference.child("dates").child(selectedDate).child(name.key).get().await()
                    Log.d("TAG", "${name.key}: ${userTime.childrenCount} ${userTime.value}")
                    if (userTime.childrenCount >= 2) {
                        timeIn = userTime.children.first().key.toString().substring(0,5)
                        timeOut = userTime.children.last().key.toString().substring(0,5)
                    }else if (userTime.value == null){
                        timeIn = "__.__"
                        timeOut = "__.__"
                    }else {
                        timeIn = userTime.children.first().key.toString().substring(0,5)
                        timeOut = "__.__"
                    }

                    val dataRow = TableRow(requireContext()).apply {
                        setBackgroundColor(resources.getColor(R.color.plain))
                        setPadding(5, 5, 5, 5)
                        addView(TextView(context).apply {
                            layoutParams = TableRow.LayoutParams(100, TableRow.LayoutParams.WRAP_CONTENT)
                            text = name.value
                        })

                        addView(TextView(context).apply {
                            layoutParams = TableRow.LayoutParams(50, TableRow.LayoutParams.WRAP_CONTENT)
                            text = timeIn
                            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                        })
                        addView(TextView(context).apply {
                            layoutParams = TableRow.LayoutParams(50, TableRow.LayoutParams.WRAP_CONTENT)
                            text = timeOut
                            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                        })
                    }

//                    val tableRow = TableRow(requireContext()).apply {
//                        setBackgroundResource(R.color.white)
//                    }
//                    val nameId = TextView(requireContext()).apply {
//                        layoutParams = TableRow.LayoutParams(100, TableRow.LayoutParams.WRAP_CONTENT)
//                        text = name.value
//                    }
//
//
//                    if (userTime.value !== null) {
//                        timeIn = userTime.children.first().key.toString()
//                        timeOut = userTime.children.last().key.toString()
//                        tvTimeIn = TextView(requireContext()).apply {
//                            layoutParams = TableRow.LayoutParams(50, TableRow.LayoutParams.WRAP_CONTENT)
//                            gravity = Gravity.CENTER
//                            text = timeIn.substring(0,5)
//                            textAlignment = View.TEXT_ALIGNMENT_CENTER
//                        }
//                        tvTimeOut = TextView(requireContext()).apply {
//                            layoutParams = TableRow.LayoutParams(50, TableRow.LayoutParams.WRAP_CONTENT)
//                            gravity = Gravity.CENTER
//                            text = timeOut.substring(0,5)
//                            textAlignment = View.TEXT_ALIGNMENT_CENTER
//                        }
//                    }else {
//                        tvTimeIn = TextView(requireContext()).apply {
//                            layoutParams = TableRow.LayoutParams(50, TableRow.LayoutParams.WRAP_CONTENT)
//                            gravity = Gravity.CENTER
//                            text = "__.__"
//                            textAlignment = View.TEXT_ALIGNMENT_CENTER
//                        }
//                        tvTimeOut = TextView(requireContext()).apply {
//                            layoutParams = TableRow.LayoutParams(50, TableRow.LayoutParams.WRAP_CONTENT)
//                            gravity = Gravity.CENTER
//                            text = "__.__"
//                            textAlignment = View.TEXT_ALIGNMENT_CENTER
//                        }
//                    }

                    withContext(Dispatchers.Main) {
                        tableLayout.addView(dataRow)
                        //table.addView(tableRow)
                    }

                }
            }
        }





//        val currentDateListener = object : ValueEventListener {
//            override fun onDataChange(snapshot: DataSnapshot) {
//                Log.d("TAG", "onDataChange: $snapshot")
//                Log.d("TAG", "snap value: ${snapshot.value}")
//                Log.d("TAG", "snap key: ${snapshot.key}")
//
//                while (mappingNames) {
//                    // TODO: loading
//                }
//
//
//
//                for(userTime in snapshot.children){
//                    Log.d("TAG", "child count: ${userTime.childrenCount}")
//                    Log.d("TAG", "user time: ${userTime.value}")
//                    Log.d("TAG", "first key: ${userTime.children.first().key}")
//                    Log.d("TAG", "last key: ${userTime.children.last().key}")
//                    timeIn = userTime.children.first().key.toString()
//                    timeOut = if (userTime.childrenCount <= 1) "__.__" else userTime.children.last().key.toString()
//                    userId = userTime.key.toString()
//
//
//                    val tableRow = TableRow(requireContext()).apply {
//                        setBackgroundResource(R.color.white)
//
//                    }
//                    val nameId = TextView(requireContext()).apply {
//                        layoutParams = TableRow.LayoutParams(100, TableRow.LayoutParams.WRAP_CONTENT)
//                        text = "$userId"
//                    }
//                    val tvTimeIn = TextView(requireContext()).apply {
//                        layoutParams = TableRow.LayoutParams(50, TableRow.LayoutParams.WRAP_CONTENT)
//                        gravity = Gravity.CENTER
//                        text = timeIn.substring(0,5)
//                        textAlignment = View.TEXT_ALIGNMENT_CENTER
//                    }
//                    val tvTimeOut = TextView(requireContext()).apply {
//                        layoutParams = TableRow.LayoutParams(50, TableRow.LayoutParams.WRAP_CONTENT)
//                        gravity = Gravity.CENTER
//                        text = timeOut.substring(0,5)
//                        textAlignment = View.TEXT_ALIGNMENT_CENTER
//                    }
//
//                    tableRow.addView(nameId)
//                    tableRow.addView(tvTimeIn)
//                    tableRow.addView(tvTimeOut)
//                    table.addView(tableRow)
//
//                }
//
//            }
//
//            override fun onCancelled(error: DatabaseError) {
//                TODO("Not yet implemented")
//            }
//
//        }
//
//        currentDateReference.addValueEventListener(currentDateListener)

    }

    private fun showDatePicker(viewModel: DailyViewModel) {
        val datePikcerDialog = DatePickerDialog(
            requireContext(), { _, year: Int, month: Int, date: Int ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(year, month, date)
            val dateFormat = SimpleDateFormat("MM-dd-yyyy")
            val formattedDate = dateFormat.format(selectedDate.time)
            Log.d("TAG", "showDatePicker: $formattedDate")
            viewModel.updateDate(formattedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePikcerDialog.show()
    }

}

