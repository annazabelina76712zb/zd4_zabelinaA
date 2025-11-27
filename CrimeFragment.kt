package com.example.criminalintent_1

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class CrimeFragment : Fragment() {

    private lateinit var crime: Crime
    private lateinit var titleField: EditText
    private lateinit var dateButton: Button
    private lateinit var solvedCheckBox: CheckBox
    private lateinit var suspectButton: Button
    private lateinit var reportButton: Button
    private lateinit var callButton: Button

    companion object {
        private const val REQUEST_CONTACT = 1
        private const val REQUEST_READ_CONTACTS = 100
        private const val DATE_FORMAT = "EEE, MMM, dd"
        private const val TAG = "CrimeFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        crime = Crime()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_crime, container, false)

        titleField = view.findViewById(R.id.crime_title)
        dateButton = view.findViewById(R.id.crime_date)
        solvedCheckBox = view.findViewById(R.id.crime_solved)
        suspectButton = view.findViewById(R.id.crime_suspect)
        reportButton = view.findViewById(R.id.crime_report)
        callButton = view.findViewById(R.id.crime_call)

        dateButton.apply {
            text = crime.date.toString()
            isEnabled = false
        }

        return view
    }

    override fun onStart() {
        super.onStart()

        val titleWatcher = object : TextWatcher {
            override fun beforeTextChanged(sequence: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(sequence: CharSequence?, start: Int, before: Int, count: Int) {
                crime.title = sequence.toString()
            }
            override fun afterTextChanged(sequence: Editable?) {}
        }

        titleField.addTextChangedListener(titleWatcher)
        solvedCheckBox.setOnCheckedChangeListener { _, isChecked ->
            crime.isSolved = isChecked
        }


        reportButton.setOnClickListener {
            val reportText = getCrimeReport()
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, reportText)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crime_report_subject))
            }.also { intent ->
                val chooserIntent = Intent.createChooser(intent, getString(R.string.send_report))
                startActivity(chooserIntent)
            }
        }


        suspectButton.setOnClickListener {
            if (hasContactsPermission()) {
                openContacts()
            } else {
                requestContactsPermission()
            }
        }


        callButton.setOnClickListener {
            if (crime.suspectPhoneNumber.isNotBlank()) {
                val callIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${crime.suspectPhoneNumber}")
                }
                try {
                    startActivity(callIntent)
                    Toast.makeText(requireContext(), "Calling: ${crime.suspectPhoneNumber}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Cannot make call", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "No phone number available", Toast.LENGTH_SHORT).show()
            }
        }

        updateUI()
    }


    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestContactsPermission() {
        val permissions = arrayOf(Manifest.permission.READ_CONTACTS)

        if (ActivityCompat.shouldShowRequestPermissionRationale(
                requireActivity(),
                Manifest.permission.READ_CONTACTS
            )
        ) {
            Toast.makeText(
                requireContext(),
                "App needs contacts permission to select suspects",
                Toast.LENGTH_LONG
            ).show()
        }

        requestPermissions(permissions, REQUEST_READ_CONTACTS)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_READ_CONTACTS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(requireContext(), "Permission granted", Toast.LENGTH_SHORT).show()
                    openContacts()
                } else {
                    Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private fun openContacts() {
        val pickContactIntent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        try {
            startActivityForResult(pickContactIntent, REQUEST_CONTACT)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No contacts app found", Toast.LENGTH_LONG).show()
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            REQUEST_CONTACT -> {
                data?.data?.let { contactUri ->
                    handleSelectedContact(contactUri)
                }
            }
        }
    }


    private fun handleSelectedContact(contactUri: Uri) {
        try {

            val cursor = requireActivity().contentResolver.query(
                contactUri,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                null, null, null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val suspectName = it.getString(0)
                    crime.suspect = suspectName
                    crime.title = "Crime by $suspectName"


                    getPhoneNumber(contactUri)

                    updateUI()
                    Toast.makeText(requireContext(), "Suspect selected: $suspectName", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading contact", e)
            Toast.makeText(requireContext(), "Error reading contact", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getPhoneNumber(contactUri: Uri) {
        try {

            val cursor = requireActivity().contentResolver.query(
                contactUri,
                arrayOf(ContactsContract.Contacts._ID),
                null, null, null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val contactId = it.getString(0)

                    val phoneCursor = requireActivity().contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null
                    )

                    phoneCursor?.use { phone ->
                        if (phone.moveToFirst()) {
                            val phoneNumber = phone.getString(0)
                            if (phoneNumber != null && phoneNumber.isNotBlank()) {
                                crime.suspectPhoneNumber = phoneNumber
                                return
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting phone number", e)
        }


        crime.suspectPhoneNumber = "1234567890"
    }


    private fun updateUI() {
        titleField.setText(crime.title)
        dateButton.text = crime.date.toString()
        solvedCheckBox.isChecked = crime.isSolved


        suspectButton.text = if (crime.suspect.isNotBlank()) {
            crime.suspect
        } else {
            getString(R.string.crime_suspect_text)
        }

        callButton.isEnabled = crime.suspectPhoneNumber.isNotBlank()
    }


    private fun getCrimeReport(): String {
        val solvedString = if (crime.isSolved) {
            getString(R.string.crime_report_solved)
        } else {
            getString(R.string.crime_report_unsolved)
        }

        val dateString = DateFormat.format(DATE_FORMAT, crime.date).toString()

        val suspectText = if (crime.suspect.isBlank()) {
            getString(R.string.crime_report_no_suspect)
        } else {
            getString(R.string.crime_report_suspect, crime.suspect)
        }

        return getString(R.string.crime_report, crime.title, dateString, solvedString, suspectText)
    }
}