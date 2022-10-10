package com.example.flutter_braintree;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.braintreepayments.api.DropInClient;
import com.braintreepayments.api.DropInListener;
import com.braintreepayments.api.DropInRequest;

public class BraintreeDropInFragment extends Fragment {
    private DropInClient dropInClient;
    private DropInRequest request;
    private String token;
    private DropInListener listener;

    BraintreeDropInFragment(DropInRequest request, String token, DropInListener listener) {
        super();

        this.request = request;
        this.token = token;
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dropInClient = new DropInClient(this, token);
        dropInClient.setListener(listener);
    }

    public void launch() {
        dropInClient.launchDropIn(request);
    }
}
