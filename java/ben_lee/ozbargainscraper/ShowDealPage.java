package ben_lee.ozbargainscraper;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class ShowDealPage extends AppCompatActivity {

    //This activity is started from MainActivity. It will start when a user clicks on a deal.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.show_deal_page);
        Toolbar toolbar = (Toolbar) findViewById(R.id.web_view_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        Intent starter = getIntent();
        String url = starter.getStringExtra(MainActivity.URL_STRING);
        showPage(url);
    }

    //Show the provided url in a webview (feature light in app browser)
    private void showPage(String url) {
        WebView page = findViewById(R.id.deal_page_view);
        //Make any interactions with links in the webview stay in the webview.
        page.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return false;
            }
        });
        page.loadUrl(url);
    }
}
