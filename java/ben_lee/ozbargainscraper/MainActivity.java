package ben_lee.ozbargainscraper;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private static final String ROOT_SITE = "https://www.ozbargain.com.au";
    private static final String NEW_DEALS_STRING_ADDITION = "/deals";
    private static final String PAGE_PARAMETER = "?page=";
    private static final int NUM_CATS = 20;
    private static final int NUM_DEALS_PER_PAGE = 20;
    private static final String DEFAULT_CAT = "Travel";
    public static final String URL_STRING = "ozbargain_url";
    private static final Spanned EXPIRED_BANNER = Html.fromHtml("<span style='color:#ffffff background-color:red'>Expired</span>");

    private String currentCat;
    //If this option is set to true, then expired deals will also be shown
    private boolean showExpired;
    //If this option is set to true, new deals will be shown instead of top deals
    private boolean showNewDeals;
    private int currentPage;

    //Maps the name of the category (e.g. travel) to the subdirectory on the deals site (/cat/travel)
    private HashMap<String, String> categoryNameLinkMap;

    private List<Spanned> deals;
    private ListView dealsList;
    private ArrayAdapter<Spanned> dealsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initVarsAndViews();
        updateDealsView();
    }

    private void initVarsAndViews() {
        showExpired = true;
        showNewDeals = false;
        currentPage = 0;
        currentCat = DEFAULT_CAT;
        categoryNameLinkMap = new HashMap<>(NUM_CATS);
        dealsList = findViewById(R.id.deals);
        deals = new ArrayList<>(NUM_DEALS_PER_PAGE);
        dealsAdapter = new ArrayAdapter<>(this, R.layout.deal_view, deals);
        dealsList.setAdapter(dealsAdapter);
        getCategories();
        setSpinner();
        addSelectNewTopListeners();
        addFooter();
        addPageButtonListeners();
    }

    //Set up the dropdown list that allows a user to choose which category of deals to view
    private void setSpinner() {
        Spinner catChoices = findViewById(R.id.cat_select);
        //Create a list of all of the names of categories available
        final List<String> categories = new ArrayList<>(categoryNameLinkMap.keySet());
        Collections.sort(categories);
        //Create the adapter that the spinner will use from the list of categories
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        catChoices.setAdapter(adapter);
        catChoices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            /*
            When a new category is selected, set the page number to zero and get a list of
            the deals in that category from the website and update the view to show these deals
             */
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String newCat = categories.get((int)l);
                if (currentCat.equals(newCat)) {
                    return;
                }
                currentCat = newCat;
                currentPage = 0;
                updateDealsView();
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        //Set the default category to be currentCat
        catChoices.setSelection(categories.indexOf(DEFAULT_CAT));
    }

    /*
    Add OnClickListeners to the "Top" and "New" textviews so users can select between seeing
    new deals and top deals
     */
    private void addSelectNewTopListeners() {
        final TextView topDealsTextView = findViewById(R.id.select_top);
        final TextView newDealsTextView = findViewById(R.id.select_new);
        //The default selection is to see top deals so bold the text in the "Top" textview
        topDealsTextView.setTypeface(topDealsTextView.getTypeface(), Typeface.BOLD);
        topDealsTextView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                //If the current selection is already top, don't do anything
                if (!showNewDeals) {
                    return;
                }
                makeSelectedBold(topDealsTextView, newDealsTextView);
                currentPage = 0;
                showNewDeals = false;
                updateDealsView();
            }
        });
        newDealsTextView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (showNewDeals) {
                    return;
                }
                makeSelectedBold(newDealsTextView, topDealsTextView);
                currentPage = 0;
                showNewDeals = true;
                updateDealsView();
            }
        });
    }

    /*
    This method is called when either the "Top" or "New" textviews are clicked. If the user
    is changing between top and new, this method will change the typeface in the textviews
    to bold for the selected option and normal for the unselected option.
     */
    private void makeSelectedBold(TextView makeBold, TextView makeUnbold) {
        makeBold.setTypeface(makeBold.getTypeface(), Typeface.BOLD);
        makeUnbold.setTypeface(null, Typeface.NORMAL);
    }

    //Add OnClickListeners to the page navigation textviews in the footer below the deals.
    private void addPageButtonListeners() {
        final TextView nextPage = findViewById(R.id.next_page_view);
        final TextView prevPage = findViewById(R.id.prev_page_view);
        prevPage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentPage == 0) {
                    return;
                }
                currentPage--;
                updateDealsView();
            }
        });
        nextPage.setOnClickListener(new OnClickListener() {
            /*
            Does not check if there is a "last page". If the "next" button is pressed and there
            are no more deals - an exception is caught and nothing happens.
             */
            @Override
            public void onClick(View view) {
                currentPage++;
                updateDealsView();
            }
        });
    }

    /*
    Add a footer below the listview that shows the deals. This footer contains the textviews
    that allow the user to navigate between pages of deals.
     */
    private void addFooter() {
        View footer = ((LayoutInflater) getBaseContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.navigation_footer, null, false);
        dealsList.addFooterView(footer);
    }

    /*
    Get new deals from the website and display them on the screen. The actionbar that is on the
    top of the screen will update to reflect what kind of deals are being displayed.
     */
    private void updateDealsView() {
        deals = getDeals();
        //Update the action bar.
        String title = currentCat + " - ";
        title += showNewDeals ? "New" : "Top";
        setTitle(title);
        //Empty the list of deals currently shown and populate it with the new ones from getDeals()
        dealsAdapter.clear();
        dealsAdapter.addAll(deals);
        //Call the adapter to reflect the change in deals in the display.
        dealsAdapter.notifyDataSetChanged();
        scrollListviewToTop();
    }

    /*
    When a deal is clicked, open the deal in a WebView.
    See the ShowDealPage class.
     */
    public void dealClicked(View view) {
        String htmlString = getHtmlFromTextview((TextView) view);
        Intent showPage = new Intent(this, ShowDealPage.class);
        showPage.putExtra(URL_STRING, getUrlFromHtml(htmlString));
        startActivity(showPage);
    }

    //Get the raw string (i.e. one that contains html tags) from a textview and return it as a string.
    private String getHtmlFromTextview(TextView view) {
        Spanned textViewSpan = (Spanned) view.getText();
        return Html.toHtml(textViewSpan);
    }

    /*
    From a piece of formatted text containing a href anchor, extract the link from the href
    and return it as a string.
     */
    private String getUrlFromHtml(String html) {
        Pattern extractUrl = Pattern.compile("<a href=\"([^\"]*)\">");
        Matcher urlMatch = extractUrl.matcher(html);
        if (urlMatch.find()) {
            return urlMatch.group(1);
        }
        return null;
    }

    private void scrollListviewToTop() {
        dealsList.setSelectionAfterHeaderView();
    }

    /*
    Send a request to the website for deals. The response from the website is parsed and all
    of the deals in the response are extracted and placed in a list that is returned.
    The list contains formatted html that is of the form <a href='deal_url'>deal_name</a>
     */
    private List<Spanned> getDeals() {
        final List<Spanned> deals = new ArrayList<>(NUM_DEALS_PER_PAGE);
        Thread newThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Document page = null;
                //Build the url string to send the request to
                String categoryLink = categoryNameLinkMap.get(currentCat);
                String connectLink = ROOT_SITE + categoryLink;
                if (showNewDeals) {
                    connectLink += NEW_DEALS_STRING_ADDITION;
                }
                if (currentPage > 0) {
                    connectLink += PAGE_PARAMETER + String.valueOf(currentPage);
                }
                try {
                    page = Jsoup.connect(connectLink).get();
                } catch (org.jsoup.HttpStatusException httpError) {
                    //If there is a 404 error, catch it and don't do anything
                    //TODO: maybe make a popup telling user there are no more deals?
                } catch (IOException e) {
                    Log.d("CONNECT_ERROR", e.toString());
                }
                //This pattern extracts the url of a deal and the deal name from an element
                Pattern dealDetailsPattern = Pattern.compile("a href=\"([^\"]*)\">(.*)</a>");
                if (page != null) {
                    Elements links;
                    if (showExpired) {
                        links = page.select("h2[class=title]");
                    } else {
                        //Expired deals contain a span in the element so they can be skipped by getting elements without spans
                        links = page.select("h2[class=title]:not(:has(span))");
                    }
                    for (Element link : links) {
                        boolean expired = link.toString().contains("expired");
                        //Get the anchor element (It should contain only one)
                        link = link.selectFirst("a");
                        Matcher match = dealDetailsPattern.matcher(link.toString());
                        if (match.find()) {
                            String dealLink = match.group(1);
                            //Remove any html for dollar signs
                            String dealName = match.group(2).replaceAll("<em class=\"dollar\">|</em>", "");
                            String dealString = "<a href=\"" + ROOT_SITE + dealLink + "\">" + dealName + "</a>";
                            Spannable deal = (Spannable) Html.fromHtml(dealString);
                            formatUrlText(deal);
                            Spanned spannedDeal = (Spanned) deal;
                            if (expired) {
                                //If a deal is expired, mark it as such with the string "Expired" in white text on a red background
                                spannedDeal = (Spanned) TextUtils.concat(EXPIRED_BANNER, deal);
                            }
                            deals.add(spannedDeal);
                        }

                    }
                }
            }
        });
        newThread.start();
        try {
            //Wait until all of the deals are added before returning
            newThread.join();
        } catch (InterruptedException e) {
            //Ignore any exceptions
        }
        return deals;
    }

    private void formatUrlText(Spannable text) {
        for (URLSpan u : text.getSpans(0, text.length(), URLSpan.class)) {
            text.setSpan(new UnderlineSpan() {
                public void updateDrawState(TextPaint tp) {
                    tp.setUnderlineText(false);
                    tp.setColor(getResources().getColor(R.color.colorPrimaryDark));
                }
            }, text.getSpanStart(u), text.getSpanEnd(u), 0);
        }
    }

    /*
    Set the list of categories of deals available to the user to select in the dropdown. The list
    of categories are available in a dropdown on the root page but to populate the popup, a request
    is sent.
     */
    private void getCategories() {
        Thread newThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Document page = null;
                //Send a request to the home page to get the link where the popup is.
                try {
                    page = Jsoup.connect(ROOT_SITE).get();
                } catch (IOException e) {
                    Log.d("CONNECT_ERROR", e.toString());
                }
                Pattern dealsMatch = Pattern.compile("data-popupurl=\"([^\"]*)\"");
                if (page != null) {
                    //Get the subdirectory of the popup element containing the categories.
                    Element menuLink = page.getElementById("menu-deals");
                    //Extract that subdirectory and send a request to it.
                    Matcher match = dealsMatch.matcher(menuLink.toString());
                    if (match.find()) {
                        try {
                            page = Jsoup.connect(ROOT_SITE + match.group(1)).get();
                        } catch (IOException e) {
                            Log.d("CONNECT_ERROR", e.toString());
                        }
                    }
                }
                //Get all the categories from the response object.
                Pattern catMatch = Pattern.compile("a href=\"([^\"]*)\">(.*)</a>");
                if (page != null) {
                    Elements links = page.select("a[href~=/cat/.*]");
                    for (Element link : links) {
                        String catString = link.toString();
                        Matcher match = catMatch.matcher(catString);
                        if (match.find()) {
                            String categoryName = match.group(2).replaceAll("&amp;", "&");
                            String categoryLink = match.group(1);
                            //Map the name of the category to the subdirectory of the site.
                            categoryNameLinkMap.put(categoryName, categoryLink);
                        }
                    }
                }
            }
        });
        newThread.start();
        try {
            //Wait for the thread to complete before finishing.
            newThread.join();
        } catch (InterruptedException e) {
            //Ignore any exceptions
        }
    }
}
