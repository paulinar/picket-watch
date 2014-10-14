package com.example.mari.picketwatch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.googlecode.flickrjandroid.Flickr;
import com.googlecode.flickrjandroid.FlickrException;
import com.googlecode.flickrjandroid.REST;
import com.googlecode.flickrjandroid.photos.Photo;
import com.googlecode.flickrjandroid.photos.PhotoList;
import com.googlecode.flickrjandroid.photos.PhotosInterface;
import com.googlecode.flickrjandroid.photos.SearchParameters;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.Constants;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.DeckOfCardsEventListener;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.card.Card;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.card.ListCard;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.card.NotificationTextCard;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.card.SimpleTextCard;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.DeckOfCardsManager;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.RemoteDeckOfCards;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.RemoteDeckOfCardsException;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.RemoteResourceStore;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.RemoteToqNotification;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.resource.CardImage;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.resource.DeckOfCardsLauncherIcon;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.util.ParcelableUtil;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.UUID;

import javax.xml.parsers.ParserConfigurationException;

import flickr.FlickrHelper;
import flickr.FlickrjActivity;


public class ToqActivity extends Activity {

    private final static String PREFS_FILE = "prefs_file";
    private final static String DECK_OF_CARDS_KEY = "deck_of_cards_key";
    private final static String DECK_OF_CARDS_VERSION_KEY = "deck_of_cards_version_key";

    private DeckOfCardsManager mDeckOfCardsManager;
    private RemoteDeckOfCards mRemoteDeckOfCards;
    private RemoteResourceStore mRemoteResourceStore;
    private CardImage[] mCardImages;
    private ToqBroadcastReceiver toqReceiver;

    // Trigger callbacks when a card is clicked
    private DeckOfCardsEventListener deckOfCardsEventListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toq);
        mDeckOfCardsManager = DeckOfCardsManager.getInstance(getApplicationContext());
        toqReceiver = new ToqBroadcastReceiver();
        init();

        // *** create the listener ***
        deckOfCardsEventListener = new DeckOfCardsEventListenerImpl();

        install();

        // *** for handling location ***

        // 1. acquire a reference to the system Location Manager
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // 2. define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();

                double targetLatitude = 37.86965;
                double targetLongitude = -122.25914;

                float[] result = new float[1];

                // distanceBetween (double startLatitude, double startLongitude, double endLatitude, double endLongitude, float[] results)
                Location.distanceBetween(latitude, longitude, targetLatitude, targetLongitude, result);

                // http://stackoverflow.com/questions/5936912/how-to-find-the-distance-between-two-geopoints
                if (result[0]*0.000621371192f <= 50) { // if user is within 50m of Sproul
                    sendNotification();
                }

                // Toast.makeText(getApplicationContext(), "Your Location is - \nLat: " + latitude + "\nLong: " + longitude, Toast.LENGTH_SHORT).show();
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        // 3. register the listener with the Location Manager to receive location updates
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            // Get update every 120 seconds
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 120000, 0, locationListener);
        }

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            // Get update every 120 seconds
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 120000, 0, locationListener);
        }

    }

    // *** FOR FLICKR STUFF ***
    File fileUri;

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 102) {

            if (resultCode == Activity.RESULT_OK) {
                Uri tmp_fileUri = data.getData();

                String selectedImagePath = getPath(tmp_fileUri);
                fileUri = new File(selectedImagePath);

                Intent uploadIntent = new Intent(getApplicationContext(),
                        FlickrjActivity.class);
                uploadIntent.putExtra("flickImagePath", fileUri.getAbsolutePath());

                startActivity(uploadIntent);
            }

        }
    };
    public String getPath(Uri uri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        @SuppressWarnings("deprecation")
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        int column_index = cursor
                .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }


    public void onResume() {
        super.onResume();

        Intent intent = getIntent();

        try {

            if (intent.getExtras().containsKey("userClickedUpload")) {

                showImage();
                addSimpleTextCard();

                Intent galleryIntent = new Intent();
                galleryIntent.setType("image/*");
                galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
                galleryIntent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                startActivityForResult(galleryIntent, 102);

                intent.removeExtra("userClickedUpload");

            }

            intent.removeExtra("userClickedUpload");

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("No extras in intent to get!");
            return;
        }
    }


    private void showImage() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    String svr="www.flickr.com";

                    REST rest=new REST();
                    rest.setHost(svr);

                    //initialize Flickr object with key and rest
                    Flickr flickr=new Flickr(FlickrHelper.API_KEY,rest);

                    //initialize SearchParameter object, this object stores the search keyword
                    SearchParameters searchParams=new SearchParameters();
                    searchParams.setSort(SearchParameters.INTERESTINGNESS_DESC);

                    //Create tag keyword array
                    String[] tags=new String[]{"cs160fsm"};
                    searchParams.setTags(tags);

                    //Initialize PhotosInterface object
                    PhotosInterface photosInterface=flickr.getPhotosInterface();
                    //Execute search with entered tags
                    PhotoList photoList=photosInterface.search(searchParams,20,1);

                    //get search result >> fetch the photo object >> get small square image's url
                    if(photoList!=null){
                        //Get search result and check the size of photo result
                        Random random = new Random();
                        int seed = random.nextInt(photoList.size());
                        //get photo object
                        Photo photo=(Photo)photoList.get(seed);

                        //Get small square url photo
                        InputStream is = photo.getMediumAsStream();
                        Bitmap bm = BitmapFactory.decodeStream(is);
                        Bitmap scaledBM = Bitmap.createScaledBitmap(bm, 250, 288, false);

                        try {
                            mCardImages[6] = new CardImage("wildcard.image", scaledBM);
//                            mCardImages[6] = new CardImage("wildcard.image", getBitmap("test_wildcard.png"));
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("Can't get wildcard image!");
                            return;
                        }
                    }
                } catch (ParserConfigurationException e) {
                    e.printStackTrace();
                } catch (FlickrException e) {
                    e.printStackTrace();
                } catch (IOException e ) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        };

        thread.start();
    }

    /**
     * @see android.app.Activity#onStart()
     * This is called after onCreate(Bundle) or after onRestart() if the activity has been stopped
     */
    protected void onStart() {
        super.onStart();

        Log.d(Constants.TAG, "ToqApiDemo.onStart");
        // If not connected, try to connect
        if (!mDeckOfCardsManager.isConnected()) {
            try {
                mDeckOfCardsManager.connect();
            } catch (RemoteDeckOfCardsException e) {
                e.printStackTrace();
            }
        }

        // add listener
        mDeckOfCardsManager.addDeckOfCardsEventListener(deckOfCardsEventListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.toq, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void sendNotification() {
        String[] message = new String[1];
        message[0] = "\"I need you to draw a poster!\"";

        ArrayList<String> possibleTitles = new ArrayList<String>();
        possibleTitles.add("Mario Savio says:");
        possibleTitles.add("Jack Weinberg says:");
        possibleTitles.add("Joan Baez says:");
        possibleTitles.add("Jackie Goldberg says:");
        possibleTitles.add("Michael Rossmann says:");
        possibleTitles.add("Art Goldberg says:");

        // Randomize the notification
        Random random = new Random();
        int index = random.nextInt(6);

        // Create a NotificationTextCard
        NotificationTextCard notificationCard = new NotificationTextCard(System.currentTimeMillis(),
                possibleTitles.get(index), message);

        // Draw divider between lines of text
        notificationCard.setShowDivider(true);
        // Vibrate to alert user when showing the notification
        notificationCard.setVibeAlert(true);
        // Create a notification with the NotificationTextCard we made
        RemoteToqNotification notification = new RemoteToqNotification(this, notificationCard);

        try {
            // Send the notification
            mDeckOfCardsManager.sendNotification(notification);
            Toast.makeText(this, "Sent Notification", Toast.LENGTH_SHORT).show();
        } catch (RemoteDeckOfCardsException e) {
            e.printStackTrace();
            // Toast.makeText(this, "Failed to send Notification", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Installs applet to Toq watch if app is not yet installed
     */
    private void install() {
        boolean isInstalled = true;

        try {
            isInstalled = mDeckOfCardsManager.isInstalled();
        } catch (RemoteDeckOfCardsException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: Can't determine if app is installed", Toast.LENGTH_SHORT).show();
        }

        if (!isInstalled) {
            try {
                mDeckOfCardsManager.installDeckOfCards(mRemoteDeckOfCards, mRemoteResourceStore);
            } catch (RemoteDeckOfCardsException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error: Cannot install application", Toast.LENGTH_SHORT).show();
            }
        }

        try {
            storeDeckOfCards();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a deck of cards to the applet
     */
    private void addSimpleTextCard() {
        ListCard listCard = mRemoteDeckOfCards.getListCard();

        // add card with new image drawn by another user (from Flickr)
        String randomImageName = UUID.randomUUID().toString();
        SimpleTextCard simpleTextCard = new SimpleTextCard(randomImageName);

        simpleTextCard.setHeaderText("*WILDCARD*");
        simpleTextCard.setTitleText("#cs160fsm");
        simpleTextCard.setReceivingEvents(false);
        simpleTextCard.setShowDivider(true);
        simpleTextCard.setCardImage(mRemoteResourceStore, mCardImages[6]);

        listCard.add(simpleTextCard);

        try {
            mDeckOfCardsManager.updateDeckOfCards(mRemoteDeckOfCards, mRemoteResourceStore);
        } catch (RemoteDeckOfCardsException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to Create SimpleTextCard", Toast.LENGTH_SHORT).show();
        }

    }


    private void removeDeckOfCards() {
        ListCard listCard = mRemoteDeckOfCards.getListCard();
        if (listCard.size() == 0) {
            return;
        }

        listCard.remove(0);

        try {
            mDeckOfCardsManager.updateDeckOfCards(mRemoteDeckOfCards);
        } catch (RemoteDeckOfCardsException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to delete Card from ListCard", Toast.LENGTH_SHORT).show();
        }

    }

    // Initialise
    private void init() {

        // Create the resource store for icons and images
        mRemoteResourceStore = new RemoteResourceStore();

        DeckOfCardsLauncherIcon whiteIcon = null;
        DeckOfCardsLauncherIcon colorIcon = null;

        // Get the launcher icons
        try {
            whiteIcon = new DeckOfCardsLauncherIcon("white.launcher.icon", getBitmap("toq_icon.png"), DeckOfCardsLauncherIcon.WHITE);
            colorIcon = new DeckOfCardsLauncherIcon("color.launcher.icon", getBitmap("toq_icon.png"), DeckOfCardsLauncherIcon.COLOR);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Can't get launcher icon");
            return;
        }

        mCardImages = new CardImage[7];

        try {
            mCardImages[0] = new CardImage("card.image.1", getBitmap("mario_savio_toq.png"));
            mCardImages[1] = new CardImage("card.image.2", getBitmap("jack_weinberg_toq.png"));
            mCardImages[2] = new CardImage("card.image.3", getBitmap("joan_baez_toq.png"));
            mCardImages[3] = new CardImage("card.image.4", getBitmap("jackie_goldberg_toq.png"));
            mCardImages[4] = new CardImage("card.image.5", getBitmap("michael_rossmann_toq.png"));
            mCardImages[5] = new CardImage("card.image.6", getBitmap("art_goldberg_toq.png"));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Can't get picture icon!");
            return;
        }

        // Try to retrieve a stored deck of cards
        try {
            // If there is no stored deck of cards or it is unusable, then create new and store
            if ((mRemoteDeckOfCards = getStoredDeckOfCards()) == null) {
                mRemoteDeckOfCards = createDeckOfCards();
                storeDeckOfCards();
            }
        } catch (Throwable th) {
            th.printStackTrace();
            mRemoteDeckOfCards = null; // Reset to force recreate
        }

        // Make sure in usable state
        if (mRemoteDeckOfCards == null) {
            mRemoteDeckOfCards = createDeckOfCards();
        }

        // Set the custom launcher icons, adding them to the resource store
        mRemoteDeckOfCards.setLauncherIcons(mRemoteResourceStore, new DeckOfCardsLauncherIcon[]{whiteIcon, colorIcon});

        // Re-populate the resource store with any card images being used by any of the cards
        for (Iterator<Card> it = mRemoteDeckOfCards.getListCard().iterator(); it.hasNext(); ) {

            String cardImageId = ((SimpleTextCard) it.next()).getCardImageId();

            if ((cardImageId != null) && !mRemoteResourceStore.containsId(cardImageId)) {

                if (cardImageId.equals("card.image.1")) {
                    mRemoteResourceStore.addResource(mCardImages[0]);

                } else if (cardImageId.equals("card.image.2")) {
                    mRemoteResourceStore.addResource(mCardImages[1]);

                } else if (cardImageId.equals("card.image.3")) {
                    mRemoteResourceStore.addResource(mCardImages[2]);

                } else if (cardImageId.equals("card.image.4")) {
                    mRemoteResourceStore.addResource(mCardImages[3]);

                } else if (cardImageId.equals("card.image.5")) {
                    mRemoteResourceStore.addResource(mCardImages[4]);

                } else if (cardImageId.equals("card.image.6")) {
                    mRemoteResourceStore.addResource(mCardImages[5]);

                } else {
                    mRemoteResourceStore.addResource(mCardImages[6]);
                }
            }

        }
    }

    // Read an image from assets and return as a bitmap
    private Bitmap getBitmap(String fileName) throws Exception {

        try {
            InputStream is = getAssets().open(fileName);
            return BitmapFactory.decodeStream(is);
        } catch (Exception e) {
            throw new Exception("An error occurred getting the bitmap: " + fileName, e);
        }
    }

    private RemoteDeckOfCards getStoredDeckOfCards() throws Exception {

        if (!isValidDeckOfCards()) {
            Log.w(Constants.TAG, "Stored deck of cards not valid for this version of the demo, recreating...");
            return null;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        String deckOfCardsStr = prefs.getString(DECK_OF_CARDS_KEY, null);

        if (deckOfCardsStr == null) {
            return null;
        } else {
            return ParcelableUtil.unmarshall(deckOfCardsStr, RemoteDeckOfCards.CREATOR);
        }

    }

    /**
     * Uses SharedPreferences to store the deck of cards
     * This is mainly used to
     */
    private void storeDeckOfCards() throws Exception {
        // Retrieve and hold the contents of PREFS_FILE, or create one when you retrieve an editor (SharedPreferences.edit())
        SharedPreferences prefs = getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        // Create new editor with preferences above
        SharedPreferences.Editor editor = prefs.edit();
        // Store an encoded string of the deck of cards with key DECK_OF_CARDS_KEY
        editor.putString(DECK_OF_CARDS_KEY, ParcelableUtil.marshall(mRemoteDeckOfCards));
        // Store the version code with key DECK_OF_CARDS_VERSION_KEY
        editor.putInt(DECK_OF_CARDS_VERSION_KEY, Constants.VERSION_CODE);
        // Commit these changes
        editor.commit();
    }

    // Check if the stored deck of cards is valid for this version of the demo
    private boolean isValidDeckOfCards() {

        SharedPreferences prefs = getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        // Return 0 if DECK_OF_CARDS_VERSION_KEY isn't found
        int deckOfCardsVersion = prefs.getInt(DECK_OF_CARDS_VERSION_KEY, 0);

        return deckOfCardsVersion >= Constants.VERSION_CODE;
    }

    // Create some cards with example content
    private RemoteDeckOfCards createDeckOfCards() {

        ListCard listCard = new ListCard();

        SimpleTextCard marioSavio = new SimpleTextCard("marioSavio");
        marioSavio.setHeaderText("Mario Savio");
        marioSavio.setTitleText("Express your own view of free speech in an image");
        marioSavio.setReceivingEvents(true);
        marioSavio.setShowDivider(true);
        marioSavio.setCardImage(mRemoteResourceStore, mCardImages[0]);

        listCard.add(marioSavio);

        // Jack Weinberg
        SimpleTextCard jackWeinberg = new SimpleTextCard("jackWeinberg");
        jackWeinberg.setHeaderText("Jack Weinberg");
        jackWeinberg.setTitleText("Draw Text: FSM");
        jackWeinberg.setReceivingEvents(true);
        jackWeinberg.setShowDivider(true);
        jackWeinberg.setCardImage(mRemoteResourceStore, mCardImages[1]);

        listCard.add(jackWeinberg);

        // Joan Baez
        SimpleTextCard joanBaez = new SimpleTextCard("joanBaez");
        joanBaez.setHeaderText("Joan Baez");
        joanBaez.setTitleText("Draw Image of: A Megaphone");
        joanBaez.setReceivingEvents(true);
        joanBaez.setShowDivider(true);
        joanBaez.setCardImage(mRemoteResourceStore, mCardImages[2]);

        listCard.add(joanBaez);

        // Jackie Goldberg
        SimpleTextCard jackieGoldberg = new SimpleTextCard("jackieGoldberg");
        jackieGoldberg.setHeaderText("Jackie Goldberg");
        jackieGoldberg.setTitleText("Draw Text: SLATE");
        jackieGoldberg.setReceivingEvents(true);
        jackieGoldberg.setShowDivider(true);
        jackieGoldberg.setCardImage(mRemoteResourceStore, mCardImages[3]);

        listCard.add(jackieGoldberg);

        // Michael Rossman
        SimpleTextCard michaelRossmann = new SimpleTextCard("michaelRossman");
        michaelRossmann.setHeaderText("Michael Rossman");
        michaelRossmann.setTitleText("Draw Text: Free Speech");
        michaelRossmann.setReceivingEvents(true);
        michaelRossmann.setShowDivider(true);
        michaelRossmann.setCardImage(mRemoteResourceStore, mCardImages[4]);

        listCard.add(michaelRossmann);

        // Art Goldberg
        SimpleTextCard artGoldberg = new SimpleTextCard("artGoldberg");
        artGoldberg.setHeaderText("Art Goldberg");
        artGoldberg.setTitleText("Draw Text: Now");
        artGoldberg.setReceivingEvents(true);
        artGoldberg.setShowDivider(true);
        artGoldberg.setCardImage(mRemoteResourceStore, mCardImages[5]);

        listCard.add(artGoldberg);

        return new RemoteDeckOfCards(this, listCard);
    }


    // *** FOR THE CALLBACK ***
    // Handle card events triggered by the user interacting with a card in the installed deck of cards
    private class DeckOfCardsEventListenerImpl implements DeckOfCardsEventListener {

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.DeckOfCardsEventListener#onCardOpen(String)
         */
        public void onCardOpen(final String cardId) {
            runOnUiThread(new Runnable() {
                public void run() {
                    // Toast.makeText(ToqActivity.this, getString(R.string.event_card_open) + cardId, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(ToqActivity.this, MainActivity.class);
                    startActivity(intent);
                }
            });
        }

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.DeckOfCardsEventListener#onCardVisible(String)
         */
        public void onCardVisible(final String cardId) {
            runOnUiThread(new Runnable() {
                public void run() {
                    // Toast.makeText(ToqActivity.this, getString(R.string.event_card_visible) + cardId, Toast.LENGTH_SHORT).show();
                }
            });
        }

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.DeckOfCardsEventListener#onCardInvisible(String)
         */
        public void onCardInvisible(final String cardId) {
            runOnUiThread(new Runnable() {
                public void run() {
                    // Toast.makeText(ToqActivity.this, getString(R.string.event_card_invisible) + cardId, Toast.LENGTH_SHORT).show();
                }
            });
        }

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.DeckOfCardsEventListener#onCardClosed(String)
         */
        public void onCardClosed(final String cardId) {
            runOnUiThread(new Runnable() {
                public void run() {
                    // Toast.makeText(ToqActivity.this, getString(R.string.event_card_closed) + cardId, Toast.LENGTH_SHORT).show();
                }
            });
        }

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.DeckOfCardsEventListener#onMenuOptionSelected(String, String)
         */
        public void onMenuOptionSelected(final String cardId, final String menuOption) {
            runOnUiThread(new Runnable() {
                public void run() {
                    // Toast.makeText(ToqActivity.this, getString(R.string.event_menu_option_selected) + cardId + " [" + menuOption + "]", Toast.LENGTH_SHORT).show();
                }
            });
        }

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.DeckOfCardsEventListener#onMenuOptionSelected(String, String, String)
         */
        public void onMenuOptionSelected(final String cardId, final String menuOption, final String quickReplyOption) {
            runOnUiThread(new Runnable() {
                public void run() {
//                    Toast.makeText(ToqActivity.this, getString(R.string.event_menu_option_selected) + cardId + " [" + menuOption + ":" + quickReplyOption +
//                            "]", Toast.LENGTH_SHORT).show();
                }
            });
        }

    }
}