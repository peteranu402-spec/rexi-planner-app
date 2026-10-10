package com.rexi.planner;

import android.app.Activity;
import android.os.CancellationSignal;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;


/** "Continue with Google" using Android's Credential Manager. Returns a Google ID token for Firebase. */
final class GoogleSignIn {
    interface Result {
        void ok(String idToken);
        void fail(String code); // cancel | none | network | error
    }

    private GoogleSignIn() {}

    static void start(Activity activity, String webClientId, Result result) {
        try {
            GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(webClientId).build();
            GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();
            CredentialManager cm = CredentialManager.create(activity);
            cm.getCredentialAsync(activity, request, new CancellationSignal(), Runnable::run,
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse response) {
                            Credential c = response.getCredential();
                            if (c instanceof CustomCredential
                                    && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                                try {
                                    result.ok(GoogleIdTokenCredential.createFrom(c.getData()).getIdToken());
                                } catch (Exception e) {
                                    result.fail("error");
                                }
                            } else {
                                result.fail("error");
                            }
                        }

                        @Override
                        public void onError(GetCredentialException e) {
                            if (e instanceof GetCredentialCancellationException) result.fail("cancel");
                            else if (e instanceof NoCredentialException) result.fail("none");
                            else if (String.valueOf(e.getType()).toLowerCase().contains("interrupted")
                                    || String.valueOf(e.getMessage()).toLowerCase().contains("network")) result.fail("network");
                            else result.fail("error");
                        }
                    });
        } catch (Exception e) {
            result.fail("error");
        }
    }
}
