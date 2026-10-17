package com.example.edusafe;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

public class ProfileFragment extends Fragment {

    private TextView tvName;
    private TextView tvInitials;

    private SharedPreferences profile;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_profile,
                container,
                false
        );

        profile = requireContext().getSharedPreferences(
                "profile",
                0
        );

        tvName = view.findViewById(R.id.tvName);
        tvInitials = view.findViewById(R.id.tvInitials);

        // EDIT PROFILE
        view.findViewById(R.id.btnEditProfile)
                .setOnClickListener(v -> editProfile());

        // MY INCIDENT REPORTS
        view.findViewById(R.id.btnReports)
                .setOnClickListener(v ->
                        open(new IncidentHistoryFragment()));

        // TRUSTED CONTACTS
        view.findViewById(R.id.btnContacts)
                .setOnClickListener(v ->
                        open(new TrustedContactsFragment()));

        // HELP AND SAFETY
        view.findViewById(R.id.btnHelp)
                .setOnClickListener(v ->
                        open(new HelpSafetyFragment()));

        // SETTINGS
        view.findViewById(R.id.btnSettings)
                .setOnClickListener(v ->
                        open(new SettingsFragment()));

        renderProfile();

        return view;
    }

    // DISPLAY USER'S NAME AND INITIALS
    private void renderProfile() {

        String firstName = profile.getString(
                "first_name", "");

        String surname = profile.getString(
                "surname", "");

        String fullName =
                (firstName + " " + surname).trim();

        // Support names saved by the previous version
        if (fullName.isEmpty()) {
            fullName = profile.getString(
                    "display_name",
                    "EduSafe user"
            );
        }

        if (fullName == null ||
                fullName.trim().isEmpty()) {
            fullName = "EduSafe user";
        }

        tvName.setText(fullName);

        StringBuilder initials = new StringBuilder();

        for (String word : fullName.trim().split("\\s+")) {
            if (!word.isEmpty() && initials.length() < 2) {
                initials.append(
                        Character.toUpperCase(word.charAt(0))
                );
            }
        }

        if (fullName.equals("EduSafe user")) {
            tvInitials.setText("E");
        } else {
            tvInitials.setText(
                    initials.length() > 0
                            ? initials.toString()
                            : "E"
            );
        }
    }

    // EDIT PROFILE DIALOG
    private void editProfile() {

        LinearLayout form = new LinearLayout(
                requireContext()
        );

        form.setOrientation(LinearLayout.VERTICAL);

        int padding = dp(20);

        form.setPadding(
                padding,
                dp(8),
                padding,
                dp(8)
        );

        EditText firstNameInput = createInput(
                "First name",
                profile.getString("first_name", ""),
                InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );

        EditText surnameInput = createInput(
                "Surname",
                profile.getString("surname", ""),
                InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );

        // Pre-fill from previous display name if needed
        String oldName = profile.getString(
                "display_name", "");

        if (firstNameInput.getText().length() == 0 &&
                oldName != null &&
                !oldName.equals("EduSafe user")) {

            String[] parts = oldName.trim().split("\\s+", 2);

            firstNameInput.setText(parts[0]);

            if (parts.length > 1 &&
                    surnameInput.getText().length() == 0) {
                surnameInput.setText(parts[1]);
            }
        }

        EditText emailInput = createInput(
                "Email address",
                profile.getString("email", ""),
                InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        EditText phoneInput = createInput(
                "Contact number",
                profile.getString("phone", ""),
                InputType.TYPE_CLASS_PHONE
        );

        form.addView(firstNameInput);
        form.addView(surnameInput);
        form.addView(emailInput);
        form.addView(phoneInput);

        AlertDialog dialog = new AlertDialog.Builder(
                requireContext()
        )
                .setTitle("Edit Profile")
                .setMessage(
                        "Update your personal details. " +
                                "Changes are saved on this device only. " +
                                "Email and phone changes are not verified yet."
                )
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(ignored -> {

            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(button -> {

                String firstName = firstNameInput
                        .getText().toString().trim();

                String surname = surnameInput
                        .getText().toString().trim();

                String email = emailInput
                        .getText().toString().trim();

                String phone = phoneInput
                        .getText().toString().trim();

                // VALIDATION
                if (firstName.isEmpty() ||
                        firstName.length() > 60) {

                    firstNameInput.setError(
                            "Enter a valid first name."
                    );
                    return;
                }

                if (surname.isEmpty() ||
                        surname.length() > 60) {

                    surnameInput.setError(
                            "Enter a valid surname."
                    );
                    return;
                }

                if (!email.isEmpty() &&
                        !android.util.Patterns
                                .EMAIL_ADDRESS
                                .matcher(email)
                                .matches()) {

                    emailInput.setError(
                            "Enter a valid email address."
                    );
                    return;
                }

                if (!phone.isEmpty() &&
                        !phone.matches("\\+?[0-9 ]{7,20}")) {

                    phoneInput.setError(
                            "Enter a valid contact number."
                    );
                    return;
                }

                String fullName =
                        firstName + " " + surname;

                // SAVE LOCALLY
                profile.edit()
                        .putString("first_name", firstName)
                        .putString("surname", surname)
                        .putString("display_name", fullName)
                        .putString("email", email)
                        .putString("phone", phone)
                        .apply();

                renderProfile();

                Toast.makeText(
                        requireContext(),
                        "Profile saved on this device",
                        Toast.LENGTH_SHORT
                ).show();

                dialog.dismiss();
            });
        });

        dialog.show();
    }

    // CREATE AN EDITABLE FIELD
    private EditText createInput(
            String hint,
            String value,
            int inputType) {

        EditText input = new EditText(
                requireContext()
        );

        input.setHint(hint);
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setText(value);
        input.setTextSize(15);
        input.setPadding(
                dp(8),
                dp(12),
                dp(8),
                dp(12)
        );

        return input;
    }

    // OPEN ANOTHER FRAGMENT
    private void open(@NonNull Fragment fragment) {

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private int dp(int value) {
        return (int) (
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }

    @Override
    public void onDestroyView() {
        tvName = null;
        tvInitials = null;
        super.onDestroyView();
    }
}

