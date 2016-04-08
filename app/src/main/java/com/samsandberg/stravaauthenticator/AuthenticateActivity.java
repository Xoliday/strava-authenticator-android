package com.samsandberg.stravaauthenticator;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.wuman.android.auth.AuthorizationDialogController;
import com.wuman.android.auth.AuthorizationFlow;
import com.wuman.android.auth.DialogFragmentController;
import com.wuman.android.auth.OAuthManager;
import com.wuman.android.auth.oauth2.store.SharedPreferencesCredentialStore;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

public class AuthenticateActivity extends FragmentActivity {
    private static final Logger LOGGER = Logger.getLogger(StravaConstants.TAG);

    public static final String EXTRA_ACCESS_TOKEN = "access_token";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);

        FragmentManager fm = getSupportFragmentManager();

        if (fm.findFragmentById(android.R.id.content) == null) {
            OAuthFragment list = new OAuthFragment();
            fm.beginTransaction().add(android.R.id.content, list).commit();
        }
    }

    @Override
    protected void onDestroy() {
        //Crouton.cancelAllCroutons();
        super.onDestroy();
    }

    public static class OAuthFragment extends Fragment implements
            LoaderManager.LoaderCallbacks<AsyncResourceLoader.Result<Credential>> {

        private static final int LOADER_GET_TOKEN = 0;
        private static final int LOADER_DELETE_TOKEN = 1;

        private OAuthManager oauth;

        private Button button;
        private TextView message;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            super.onCreateOptionsMenu(menu, inflater);
            inflater.inflate(R.menu.delete_cookies_menu, menu);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.delete_cookies: {
                    CookieSyncManager.createInstance(getActivity());
                    CookieManager cookieManager = CookieManager.getInstance();
                    cookieManager.removeAllCookie();
                    return true;
                }
                default: {
                    return super.onOptionsItemSelected(item);
                }
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            return inflater.inflate(R.layout.oauth_login, container, false);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            button = (Button) view.findViewById(android.R.id.button1);
            setButtonText(R.string.get_token);
            message = (TextView) view.findViewById(android.R.id.text1);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.getTag().equals(R.string.get_token)) {
                        if (getLoaderManager().getLoader(LOADER_GET_TOKEN) == null) {
                            getLoaderManager().initLoader(LOADER_GET_TOKEN, null,
                                    OAuthFragment.this);
                        } else {
                            getLoaderManager().restartLoader(LOADER_GET_TOKEN, null,
                                    OAuthFragment.this);
                        }
                    } else { // R.string.delete_token
                        if (getLoaderManager().getLoader(LOADER_DELETE_TOKEN) == null) {
                            getLoaderManager().initLoader(LOADER_DELETE_TOKEN, null,
                                    OAuthFragment.this);
                        } else {
                            getLoaderManager().restartLoader(LOADER_DELETE_TOKEN, null,
                                    OAuthFragment.this);
                        }
                    }
                }
            });
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            boolean fullScreen = true;
            String clientId = getString(R.string.strava_client_id);
            String clientSecret = getString(R.string.strava_client_secret);
            // setup credential store
            SharedPreferencesCredentialStore credentialStore =
                    new SharedPreferencesCredentialStore(getActivity(),
                            StravaConstants.CREDENTIALS_STORE_PREF_FILE, OAuth.JSON_FACTORY);
            // setup authorization flow
            AuthorizationFlow flow = new AuthorizationFlow.Builder(
                    BearerToken.queryParameterAccessMethod(),
                    OAuth.HTTP_TRANSPORT,
                    OAuth.JSON_FACTORY,
                    new GenericUrl(StravaConstants.URL_TOKEN),
                    new ClientParametersAuthentication(clientId, clientSecret),
                    clientId,
                    StravaConstants.URL_AUTHORIZE)
                    .setScopes(Arrays.asList(StravaScopes.SCOPE_PUBLIC))
                    .setCredentialStore(credentialStore)
                    .setRequestInitializer(new HttpRequestInitializer() {
                        @Override
                        public void initialize(HttpRequest request) throws IOException {}
                    })
                    .build();
            // setup UI controller
            AuthorizationDialogController controller =
                    new DialogFragmentController(getFragmentManager(), fullScreen) {
                        @Override
                        public String getRedirectUri() throws IOException {
                            return StravaConstants.URL_REDIRECT;
                        }

                        @Override
                        public boolean isJavascriptEnabledForWebView() {
                            return true;
                        }

                        @Override
                        public boolean disableWebViewCache() {
                            return false;
                        }

                        @Override
                        public boolean removePreviousCookie() {
                            return false;
                        }

                    };
            // instantiate an OAuthManager instance
            oauth = new OAuthManager(flow, controller);
        }

        @Override
        public Loader<AsyncResourceLoader.Result<Credential>> onCreateLoader(int id, Bundle args) {
            getActivity().setProgressBarIndeterminateVisibility(true);
            button.setEnabled(false);
            message.setText("");
            if (id == LOADER_GET_TOKEN) {
                return new GetTokenLoader(getActivity(), oauth);
            } else {
                return new DeleteTokenLoader(getActivity(), oauth);
            }
        }

        @Override
        public void onLoadFinished(Loader<AsyncResourceLoader.Result<Credential>> loader,
                                   AsyncResourceLoader.Result<Credential> result) {
            if (loader.getId() == LOADER_GET_TOKEN) {
                message.setText(result.success ? result.data.getAccessToken() : "");
            } else {
                message.setText("");
            }
            if (result.success) {
                if (loader.getId() == LOADER_GET_TOKEN) {
                    setButtonText(R.string.delete_token);
                } else {
                    setButtonText(R.string.get_token);
                }
            } else {
                setButtonText(R.string.get_token);
                //Crouton.makeText(getActivity(), result.errorMessage, Style.ALERT).show();
                // Toast instead of Crouton dependency
                Toast.makeText(getActivity(), result.errorMessage, Toast.LENGTH_SHORT).show();
            }
            getActivity().setProgressBarIndeterminateVisibility(false);
            button.setEnabled(true);

            if (result.success) {
                Intent intent = new Intent(getActivity(), MainActivity.class);
                intent.putExtra(EXTRA_ACCESS_TOKEN, result.data.getAccessToken());
                startActivity(intent);
            }
        }

        @Override
        public void onLoaderReset(Loader<AsyncResourceLoader.Result<Credential>> loader) {
            message.setText("");
            getActivity().setProgressBarIndeterminateVisibility(false);
            button.setEnabled(true);
        }

        @Override
        public void onDestroy() {
            getLoaderManager().destroyLoader(LOADER_GET_TOKEN);
            getLoaderManager().destroyLoader(LOADER_DELETE_TOKEN);
            super.onDestroy();
        }

        private void setButtonText(int action) {
            button.setText(action);
            button.setTag(action);
        }

        private static class GetTokenLoader extends AsyncResourceLoader<Credential> {

            private final OAuthManager oauth;

            public GetTokenLoader(Context context, OAuthManager oauth) {
                super(context);
                this.oauth = oauth;
            }

            @Override
            public Credential loadResourceInBackground() throws Exception {
                Credential credential =
                        oauth.authorizeExplicitly(getContext().getString(R.string.token_strava),
                                null, null).getResult();
                LOGGER.info("token: " + credential.getAccessToken());
                return credential;
            }

            @Override
            public void updateErrorStateIfApplicable(AsyncResourceLoader.Result<Credential> result) {
                Credential data = result.data;
                result.success = !TextUtils.isEmpty(data.getAccessToken());
                result.errorMessage = result.success ? null : "error";
            }

        }

        private static class DeleteTokenLoader extends AsyncResourceLoader<Credential> {

            private final OAuthManager oauth;
            private boolean success;

            public DeleteTokenLoader(Context context, OAuthManager oauth) {
                super(context);
                this.oauth = oauth;
            }

            @Override
            public Credential loadResourceInBackground() throws Exception {
                success = oauth.deleteCredential(getContext().getString(R.string.token_strava), null,
                        null).getResult();
                LOGGER.info("token deleted: " + success);
                return null;
            }

            @Override
            public void updateErrorStateIfApplicable(Result<Credential> result) {
                result.success = success;
                result.errorMessage = result.success ? null : "error";
            }

        }

    }

}