package net.mobilewebprint.nan;


import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_128;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.Uri;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.IdentityChangedListener;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareNetworkInfo;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.net.wifi.aware.WifiAwareSession;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceManager;

import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.*;
import java.lang.Thread;
import java.lang.Runnable;
import java.lang.reflect.Method;
import java.net.SocketException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;


/**
 * MainActivity - Wi-Fi Aware (NAN) Discovery and File Transfer Application.
 *
 * <p>This application demonstrates Wi-Fi Aware peer discovery and data path establishment
 * between two Android devices. It supports:</p>
 * <ul>
 *   <li>Publishing and subscribing to Wi-Fi Aware services</li>
 *   <li>Peer discovery with MAC address and IP exchange</li>
 *   <li>IPv6-based network data path (NDP) establishment</li>
 *   <li>Encrypted or open data path communication</li>
 *   <li>File transfer between peers</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <ol>
 *   <li>On Device #1: Run the app and click PUBLISH</li>
 *   <li>On Device #2: Run the app and click SUBSCRIBE</li>
 *   <li>Wait until both devices show 2 MAC addresses</li>
 *   <li>Devices will automatically establish network connection</li>
 *   <li>Use "Send File" to transfer files between devices</li>
 * </ol>
 *
 * <p>Note: Location permission and potentially nearby Wi-Fi device permission are required
 * for Wi-Fi Aware functionality on Android.</p>
 */

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

  private static final String APP_LABEL = "myNanR3";
  private static final String TAG_NAN = "myNanR3.NAN";
  private static final String TAG_FILE = "myNanR3.File";
  private static final String TAG_PREFS = "myNanR3.Prefs";
  private static final int TRANSFER_BUFFER_SIZE = 64 * 1024;
  private static final int SOCKET_CONNECT_TIMEOUT_MS = 12_000;
  private static final int SOCKET_READ_TIMEOUT_MS = 30_000;
  private static final int SEND_CONNECT_MAX_ATTEMPTS = 3;
  private static final int SEND_RETRY_DELAY_MS = 600;
  private static final long PROGRESS_LOG_BYTES = 1024 * 1024;
  private final int MAC_ADDRESS_MESSAGE = 55;
  private static final int MY_PERMISSION_FINE_LOCATION_REQUEST_CODE = 88;
  private static final int MY_PERMISSION_BACKGROUND_LOCATION_REQUEST_CODE = 66;
//  private final String              SERVICE_NAME                         = "org.wifi.nan.test";

  private BroadcastReceiver broadcastReceiver;
  private WifiAwareManager wifiAwareManager;
  private ConnectivityManager connectivityManager;
  private WifiAwareSession wifiAwareSession;
  private NetworkSpecifier networkSpecifier;

  private PublishDiscoverySession publishDiscoverySession;
  private SubscribeDiscoverySession subscribeDiscoverySession;
  private PeerHandle peerHandle;
  private byte[] myMac;
  private byte[] otherMac;

  private int pubType;
  private int subType;
  private String EncryptType;
  private String SERVICE_NAME;
  private byte[] serviceInfo;
  private byte[] pmk;
  private byte[] pmkid = new byte[16];
  private String psk;

  private final int IP_ADDRESS_MESSAGE = 33;
  private final int MESSAGE = 7;
  private static final int MY_PERMISSION_EXTERNAL_REQUEST_CODE = 99;
  private static final int MY_PERMISSION_EXTERNAL_READ_REQUEST_CODE = 98;
  private static final int MY_PERMISSION_NEARBY_WIFI_DEV = 86;
  private static final int REQUEST_PICK_FILE_TO_SEND = 120;
  private Inet6Address ipv6;
  private volatile ServerSocket serverSocket;
  private volatile Network activeAwareNetwork;
  private Inet6Address peerIpv6;
  private int peerPort;
  //  private final byte[]              serviceInfo            = "android".getBytes();
//  private final byte[]              pmk            = "123456789abcdef0123456789abcdef0".getBytes();
  private byte[] portOnSystem;
  private int portToUse;
  private byte[] myIP;
  private byte[] otherIP;
  private byte[] msgtosend;
  private Inet6Address pendingFileServerIp;
  private int pendingFileServerPort;
  private volatile boolean fileServerStarted;
  private volatile boolean responderNdpRequested;
  private volatile boolean initiatorNdpRequested;
  private volatile boolean shuttingDown;


  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (key.equals(getString(R.string.service_name))) {
      SERVICE_NAME = sharedPreferences.getString(getResources().getString(R.string.service_name), "org.wifi.nan.test");
      Log.d(TAG_PREFS, "service Name updated to: " + SERVICE_NAME);
    } else if (key.equals(getString(R.string.service_specific_info))) {
      serviceInfo = sharedPreferences.getString(getResources().getString(R.string.service_specific_info), "android").getBytes();
      Log.d(TAG_PREFS, "service info updated to: " + new String(serviceInfo));
    } else if (key.equals(getString(R.string.encryptType))) {
      EncryptType = sharedPreferences.getString(getResources().getString(R.string.encryptType), "open");
    } else if (key.equals(getString(R.string.pubType))) {
      String type = sharedPreferences.getString(getResources().getString(R.string.pubType), "unsolicited");
      if (type.equals("unsolicited")) {
        pubType = PublishConfig.PUBLISH_TYPE_UNSOLICITED;
        Log.d(TAG_PREFS, "updated pubtype : " + type);
      } else {
        pubType = PublishConfig.PUBLISH_TYPE_SOLICITED;
        Log.d(TAG_PREFS, "updated pubtype : " + type);
      }

    } else if (key.equals(getString(R.string.subType))) {
      String type = sharedPreferences.getString(getResources().getString(R.string.subType), "passive");
      if (type.equals("passive")) {
        subType = SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE;
        Log.d(TAG_PREFS, "updated subtype: " + type);
      } else {
        subType = SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE;
        Log.d(TAG_PREFS, "updated subtype: " + type);
      }
    }
    try {
      if (EncryptType.equals("pmk")) {
        pmk = sharedPreferences.getString(getResources().getString(R.string.security_pass), "123456789abcdef0123456789abcdef0").getBytes();
        Log.d(TAG_PREFS, "pmk " + new String(pmk));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          new WifiAwareDataPathSecurityConfig.Builder(WIFI_AWARE_CIPHER_SUITE_NCS_PK_128)
                  .setPmk(pmk)
                  .setPmkId(pmkid)
                  .build();
        }
      } else if (EncryptType.equals("psk")) {
        psk = sharedPreferences.getString(getResources().getString(R.string.security_pass), "12345678");
        Log.d(TAG_PREFS, "psk " + psk);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          new WifiAwareDataPathSecurityConfig.Builder(WIFI_AWARE_CIPHER_SUITE_NCS_PK_128)
                  .setPskPassphrase(psk)
                  .build();
        }
      }
    } catch (NullPointerException e) {
      Log.e(TAG_PREFS, e.toString());
    }

  }

  private void setupSharedPreferences() {
    // Get all of the values from shared preferences to set it up
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    SERVICE_NAME = sharedPreferences.getString(getString(R.string.service_name), "org.wifi.nan.test");
    serviceInfo = sharedPreferences.getString(getString(R.string.service_specific_info), "android").getBytes();
    EncryptType = sharedPreferences.getString(getString(R.string.encryptType), "open");
    pubType = PublishConfig.PUBLISH_TYPE_UNSOLICITED;
    subType = SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE;
    pmk = sharedPreferences.getString(getString(R.string.security_pass), "123456789abcdef0123456789abcdef0").getBytes();
    psk = sharedPreferences.getString(getString(R.string.security_pass), "12345678");
    Map<String, ?> keys = sharedPreferences.getAll();

    for (Map.Entry<String, ?> entry : keys.entrySet()) {
      Log.d("map values", entry.getKey() + ": " +
              entry.getValue().toString());
      if (entry.getKey().equals(getResources().getString(R.string.service_name))) {
        SERVICE_NAME = entry.getValue().toString();
        Log.d(TAG_PREFS, "service Name set " + entry.getValue().toString());
      }
      if (entry.getKey().equals(getResources().getString(R.string.service_specific_info))) {
        serviceInfo = entry.getValue().toString().getBytes();
        Log.d(TAG_PREFS, "service info set " + entry.getValue().toString());
      }
      if (entry.getKey().equals(getResources().getString(R.string.encryptType))) {
        EncryptType = entry.getValue().toString();
      }
      if (entry.getKey().equals(getResources().getString(R.string.pubType))) {
        if (entry.getValue().toString().equals("unsolicited")) {
          pubType = PublishConfig.PUBLISH_TYPE_UNSOLICITED;
          Log.d(TAG_PREFS, "pubtype unsolict: " + pubType);
        } else {
          pubType = PublishConfig.PUBLISH_TYPE_SOLICITED;
          Log.d(TAG_PREFS, "pubtype solicit: " + pubType);
        }
      }
      if (entry.getKey().equals(getResources().getString(R.string.subType))) {
        if (entry.getValue().toString().equals("passive")) {
          subType = SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE;
          Log.d(TAG_PREFS, "updated subtype: " + subType);
        } else {
          subType = SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE;
          Log.d(TAG_PREFS, "updated subtype: " + subType);
        }
      }
    }
    try {
      if (EncryptType.equals("pmk")) {
        pmk = sharedPreferences.getString(getResources().getString(R.string.security_pass), "123456789abcdef0123456789abcdef0").getBytes();
        Log.d(TAG_PREFS, "pmk " + new String(pmk));
      } else if (EncryptType.equals("psk")) {
        psk = sharedPreferences.getString(getResources().getString(R.string.security_pass), "12345678");
        Log.d(TAG_PREFS, "psk " + psk);
      }
    } catch (java.lang.NullPointerException e) {
      Log.e(TAG_PREFS, e.toString());
    }


    // Register the listener
    sharedPreferences.registerOnSharedPreferenceChangeListener(this);
  }

  /**
   * Handles initialization (creation) of the activity.
   *
   * @param savedInstanceState
   */
  @Override
  @TargetApi(26)
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    wifiAwareManager = null;
    wifiAwareSession = null;
    connectivityManager = null;
    networkSpecifier = null;
    publishDiscoverySession = null;
    subscribeDiscoverySession = null;
    peerHandle = null;

    //Log.d("myTag","Supported Aware: " + getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE));
    setContentView(R.layout.activity_main);
    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    setupSharedPreferences();
    setupPermissions();

    //------------------------------------------------------------------------------------------------------
    FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.sendmsgfab);        /* +++++ */
    fab.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        String msg = "messageToBeSent: ";
        EditText editText = (EditText) findViewById(R.id.msgtext);
        msg += editText.getText().toString();
        msgtosend = msg.getBytes();
        if (publishDiscoverySession != null && peerHandle != null) {
          publishDiscoverySession.sendMessage(peerHandle, MESSAGE, msgtosend);
        } else if (subscribeDiscoverySession != null && peerHandle != null) {
          subscribeDiscoverySession.sendMessage(peerHandle, MESSAGE, msgtosend);
        }
      }
    });                                                                                   /* ----- */
    //------------------------------------------------------------------------------------------------------

    //------------------------------------------------------------------------------------------------------
    Button statusButton = (Button) findViewById(R.id.statusbtn);                             /* +++++ */
    statusButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        showWifiAwareCapabilitiesDialog();
      }
    });                                                                                   /* ----- */
    //------------------------------------------------------------------------------------------------------

    Button publishButton = (Button) findViewById(R.id.publishButton);
    publishButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        responderNdpRequested = false;
        publishService();
      }
    });

    Button subscribeButton = (Button) findViewById(R.id.subscribeButton);
    subscribeButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        initiatorNdpRequested = false;
        subscribeToService();
      }
    });

    Button initiatorButton = (Button) findViewById(R.id.initiatorButton);
    initiatorButton.setOnClickListener(new View.OnClickListener() {
      @RequiresApi(api = Build.VERSION_CODES.Q)
      @Override
      public void onClick(View v) {
        startInitiatorNdpIfReady("manual initiator button");
      }
    });

    //-------------------------------------------------------------------------------------------- +++++
    Button responderButton = (Button) findViewById(R.id.responderButton);
    responderButton.setOnClickListener(new View.OnClickListener() {
      @RequiresApi(api = Build.VERSION_CODES.Q)
      @Override
      public void onClick(View v) {
        startResponderNdpIfReady("manual responder button");
      }
    });
    //-------------------------------------------------------------------------------------------- -----

    //-------------------------------------------------------------------------------------------- +++++
    Button sendFileButton = (Button) findViewById(R.id.sendbtn);
    sendFileButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        chooseFileForSend();
      }
    });
    //-------------------------------------------------------------------------------------------- -----

    connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

    setHaveSession(false);

    setStatus("Checking Wi-Fi Aware support...");

    String status = null;
    boolean hasNan = false;

    PackageManager packageManager = getPackageManager();
    if (packageManager == null) {
      status = "Cannot get PackageManager";
    } else {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        hasNan = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
      }
    }

    if (!hasNan) {
      status = "Wi-Fi Aware is not supported on this device";
    } else {

      wifiAwareManager = (WifiAwareManager) getSystemService(Context.WIFI_AWARE_SERVICE);

      if (wifiAwareManager == null) {
        status = "Cannot get WifiAwareManager";
      }
    }

    setStatus(status == null ? "Wi-Fi Aware service is ready" : status);
  }

  /**
   * App Permissions for Fine Location
   **/
  private void setupPermissions() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return;
    }

    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_FINE_LOCATION_REQUEST_CODE);
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.NEARBY_WIFI_DEVICES}, MY_PERMISSION_NEARBY_WIFI_DEV);
      return;
    }

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSION_EXTERNAL_REQUEST_CODE);
      return;
    }

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSION_EXTERNAL_READ_REQUEST_CODE);
    }
  }

  private boolean hasWifiAwarePermissions() {
    boolean hasLocation = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    boolean hasNearbyWifi = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            || ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
    return hasLocation && hasNearbyWifi;
  }

  /**
   * Validates the peer addressing state and opens Android's document picker.
   *
   * <p>Input comes from the current NDP/discovery state: {@link #peerIpv6} supplies the
   * scoped Wi-Fi Aware interface, {@link #otherIP} supplies the peer link-local address,
   * and {@link #portToUse}/{@link #peerPort} supply the receiver port. The method stores
   * a resolved, scoped destination in {@link #pendingFileServerIp} and
   * {@link #pendingFileServerPort}; the actual file URI is returned later through
   * {@link #onActivityResult(int, int, Intent)}.</p>
   *
   * <p>Failures are reported to the user instead of launching the picker, because picking
   * a file before NDP/port exchange is complete would almost always lead to a failed send.</p>
   */
  private void chooseFileForSend() {
    Log.d(TAG_FILE, "Send File clicked. peerIpv6=" + peerIpv6
            + ", otherIP=" + (otherIP == null ? "null" : Inet6AddressBytesToString(otherIP))
            + ", portToUse=" + portToUse
            + ", peerPort=" + peerPort
            + ", encryptType=" + EncryptType);
    if (peerIpv6 == null || otherIP == null) {
      Log.d(TAG_FILE, "Cannot open picker: peer address is not ready.");
      Toast.makeText(this, "Connect to a peer before choosing a file.", Toast.LENGTH_LONG).show();
      setStatus("Connect to a peer before choosing a file.");
      return;
    }

    // Open data paths exchange the file server port as a discovery message. Secured data
    // paths expose the negotiated port through WifiAwareNetworkInfo.
    int serverPort = EncryptType.equals("open") ? portToUse : peerPort;
    if (serverPort <= 0) {
      Log.d(TAG_FILE, "Cannot open picker: peer file-transfer port is not ready.");
      Toast.makeText(this, "Peer file-transfer port is not ready yet.", Toast.LENGTH_LONG).show();
      setStatus("Peer file-transfer port is not ready yet.");
      return;
    }

    try {
      pendingFileServerIp = Inet6Address.getByAddress("WifiAwareHost", otherIP, peerIpv6.getScopedInterface());
      pendingFileServerPort = serverPort;
      Log.d(TAG_FILE, "Resolved pending file server. ip=" + pendingFileServerIp
              + ", port=" + pendingFileServerPort
              + ", scope=" + peerIpv6.getScopedInterface());
    } catch (UnknownHostException e) {
      Log.e(TAG_FILE, "Could not resolve peer address for file send.", e);
      Toast.makeText(this, "Cannot resolve peer address.", Toast.LENGTH_LONG).show();
      return;
    }

    Log.d(TAG_FILE, "Launching file picker.");
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    intent.setType("*/*");
    startActivityForResult(intent, REQUEST_PICK_FILE_TO_SEND);
  }

  /**
   * Starts the subscriber side of the Wi-Fi Aware NDP once discovery has produced a peer.
   *
   * @param reason diagnostic label identifying which async callback made the state ready
   */
  private void startInitiatorNdpIfReady(String reason) {
    Log.d(TAG_NAN, "startInitiatorNdpIfReady reason=" + reason
            + ", alreadyRequested=" + initiatorNdpRequested
            + ", sdk=" + Build.VERSION.SDK_INT
            + ", subscribeSession=" + subscribeDiscoverySession
            + ", peerHandle=" + peerHandle
            + ", encryptType=" + EncryptType);
    if (initiatorNdpRequested || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            || subscribeDiscoverySession == null || peerHandle == null) {
      return;
    }

    try {
      if (EncryptType.equals("open")) {
        networkSpecifier = new WifiAwareNetworkSpecifier.Builder(subscribeDiscoverySession, peerHandle)
                .build();
      } else if (EncryptType.equals("pmk")) {
        networkSpecifier = new WifiAwareNetworkSpecifier.Builder(subscribeDiscoverySession, peerHandle)
                .setPmk(pmk)
                .build();
      } else if (EncryptType.equals("psk")) {
        networkSpecifier = new WifiAwareNetworkSpecifier.Builder(subscribeDiscoverySession, peerHandle)
                .setPskPassphrase(psk)
                .build();
      } else {
        Log.e(TAG_NAN, "Unknown encryption type for initiator NDP: " + EncryptType);
        return;
      }
      initiatorNdpRequested = true;
      Log.d(TAG_NAN, "Initiator NDP NetworkSpecifier created automatically.");
      setStatus("Subscriber found peer. Starting NDP automatically...");
      requestNetwork();
    } catch (Exception e) {
      Log.e(TAG_NAN, "Failed to start initiator NDP.", e);
      setStatus("Failed to start subscriber NDP: " + e.getMessage());
    }
  }

  /**
   * Starts the publisher side of the Wi-Fi Aware NDP once the peer and receiver server are ready.
   *
   * @param reason diagnostic label identifying which async callback made the state ready
   */
  private void startResponderNdpIfReady(String reason) {
    Log.d(TAG_NAN, "startResponderNdpIfReady reason=" + reason
            + ", alreadyRequested=" + responderNdpRequested
            + ", sdk=" + Build.VERSION.SDK_INT
            + ", publishSession=" + publishDiscoverySession
            + ", peerHandle=" + peerHandle
            + ", serverSocket=" + serverSocket
            + ", portToUse=" + portToUse
            + ", encryptType=" + EncryptType);
    if (responderNdpRequested || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            || publishDiscoverySession == null || peerHandle == null) {
      return;
    }

    if (serverSocket == null || serverSocket.isClosed() || serverSocket.getLocalPort() <= 0) {
      Log.d(TAG_NAN, "Responder NDP waiting for file receiver server port.");
      return;
    }

    try {
      portToUse = serverSocket.getLocalPort();
      if (EncryptType.equals("open")) {
        networkSpecifier = new WifiAwareNetworkSpecifier.Builder(publishDiscoverySession, peerHandle)
                .build();
        sendServerPortToPeer();
      } else if (EncryptType.equals("pmk")) {
        networkSpecifier = new WifiAwareNetworkSpecifier.Builder(publishDiscoverySession, peerHandle)
                .setPmk(pmk)
                .setPort(portToUse)
                .build();
      } else if (EncryptType.equals("psk")) {
        networkSpecifier = new WifiAwareNetworkSpecifier.Builder(publishDiscoverySession, peerHandle)
                .setPskPassphrase(psk)
                .setPort(portToUse)
                .build();
      } else {
        Log.e(TAG_NAN, "Unknown encryption type for responder NDP: " + EncryptType);
        return;
      }
      responderNdpRequested = true;
      Log.d(TAG_NAN, "Responder NDP NetworkSpecifier created automatically. localFilePort=" + portToUse);
      setStatus("Publisher found peer. Starting NDP automatically...");
      requestNetwork();
    } catch (Exception e) {
      Log.e(TAG_NAN, "Failed to start responder NDP.", e);
      setStatus("Failed to start publisher NDP: " + e.getMessage());
    }
  }

  /**
   * Sends this device's receiver server port over the discovery session.
   *
   * <p>The receiver listens on an ephemeral local port. This lightweight message lets the peer
   * connect over the NDP after both IPv6 addresses are exchanged. If discovery has not completed
   * yet, the method logs and returns; later callbacks call it again.</p>
   */
  private void sendServerPortToPeer() {
    if (serverSocket == null || serverSocket.isClosed() || serverSocket.getLocalPort() <= 0) {
      Log.d(TAG_FILE, "Cannot send server port: server socket is not ready.");
      return;
    }

    portOnSystem = portToBytes(serverSocket.getLocalPort());
    if (publishDiscoverySession != null && peerHandle != null) {
      publishDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, portOnSystem);
      Log.d(TAG_FILE, "Sent file receiver port via publish session. port=" + serverSocket.getLocalPort());
    } else if (subscribeDiscoverySession != null && peerHandle != null) {
      subscribeDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, portOnSystem);
      Log.d(TAG_FILE, "Sent file receiver port via subscribe session. port=" + serverSocket.getLocalPort());
    } else {
      Log.d(TAG_FILE, "Cannot send server port: no discovery session/peer.");
    }
  }

  private void showWifiAwareCapabilitiesDialog() {
    String capabilities = buildWifiAwareCapabilitiesText();
    Log.d(TAG_NAN, "Wi-Fi Aware capabilities:\n" + capabilities);
    new AlertDialog.Builder(this)
            .setTitle("Wi-Fi Aware Capabilities")
            .setMessage(capabilities)
            .setPositiveButton(android.R.string.ok, null)
            .show();
  }

  private String buildWifiAwareCapabilitiesText() {
    StringBuilder builder = new StringBuilder();
    builder.append("SDK: ").append(Build.VERSION.SDK_INT).append('\n');
    builder.append("Hardware feature: ")
            .append(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE))
            .append('\n');
    builder.append("Manager available: ").append(wifiAwareManager != null).append('\n');
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || wifiAwareManager == null) {
      return builder.append("\nWi-Fi Aware manager is unavailable.").toString();
    }

    try {
      builder.append("Aware available now: ").append(wifiAwareManager.isAvailable()).append("\n\n");
      Characteristics characteristics = wifiAwareManager.getCharacteristics();
      appendCapability(builder, characteristics, "getNumberOfSupportedDataPaths", "Max NDP sessions");
      appendCapability(builder, characteristics, "getNumberOfSupportedDataInterfaces", "Data interfaces");
      appendCapability(builder, characteristics, "getNumberOfSupportedPublishSessions", "Publish sessions");
      appendCapability(builder, characteristics, "getNumberOfSupportedSubscribeSessions", "Subscribe sessions");
      appendCapability(builder, characteristics, "getSupportedCipherSuites", "Cipher suites");
      appendCapability(builder, characteristics, "isAwarePairingSupported", "Pairing supported");
      appendCapability(builder, characteristics, "isSuspensionSupported", "Suspension supported");
      builder.append("\nAll reported no-arg capabilities:\n");
      for (Method method : Characteristics.class.getMethods()) {
        if (method.getParameterTypes().length == 0
                && (method.getName().startsWith("get") || method.getName().startsWith("is"))
                && !method.getName().equals("getClass")) {
          try {
            builder.append(method.getName()).append(": ")
                    .append(String.valueOf(method.invoke(characteristics))).append('\n');
          } catch (Exception e) {
            Log.d(TAG_NAN, "Could not read capability method " + method.getName() + ": " + e);
          }
        }
      }
    } catch (Exception e) {
      Log.e(TAG_NAN, "Could not build Wi-Fi Aware capabilities text.", e);
      builder.append("Error reading capabilities: ").append(e.getMessage());
    }
    return builder.toString();
  }

  private void appendCapability(StringBuilder builder, Characteristics characteristics, String methodName, String label) {
    try {
      Method method = Characteristics.class.getMethod(methodName);
      Object value = method.invoke(characteristics);
      builder.append(label).append(": ").append(String.valueOf(value)).append('\n');
    } catch (Exception e) {
      builder.append(label).append(": unavailable on this SDK/device").append('\n');
      Log.d(TAG_NAN, "Capability unavailable: " + methodName + " (" + e + ")");
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    Log.d(TAG_FILE, "onActivityResult requestCode=" + requestCode + ", resultCode=" + resultCode + ", hasData=" + (data != null));
    if (requestCode == REQUEST_PICK_FILE_TO_SEND && resultCode == RESULT_OK && data != null && data.getData() != null) {
      Uri fileUri = data.getData();
      Log.d(TAG_FILE, "File selected. uri=" + fileUri);
      try {
        getContentResolver().takePersistableUriPermission(fileUri, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch (SecurityException e) {
        Log.d(TAG_FILE, "Persistable file permission unavailable: " + e.toString());
      }
      Toast.makeText(this, "Sending selected file...", Toast.LENGTH_SHORT).show();
      clientSendFile(fileUri, pendingFileServerIp, pendingFileServerPort);
    } else if (requestCode == REQUEST_PICK_FILE_TO_SEND) {
      Log.d(TAG_FILE, "File picker finished without a selected file.");
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         @NonNull String permissions[], @NonNull int[] grantResults) {
    switch (requestCode) {
      case MY_PERMISSION_FINE_LOCATION_REQUEST_CODE: {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          setupPermissions();
          return;

        } else {
          Toast.makeText(this, "Location permission is required for Wi-Fi Aware discovery.", Toast.LENGTH_LONG).show();
          setStatus("Location permission is required for Wi-Fi Aware discovery.");
        }
        break;
      }
      case MY_PERMISSION_NEARBY_WIFI_DEV: {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          setupPermissions();
          return;

        } else {
          Toast.makeText(this, "Nearby Wi-Fi permission is required on Android 13+.", Toast.LENGTH_LONG).show();
          setStatus("Nearby Wi-Fi permission is required on Android 13+.");
        }
        break;
      }
/*          case MY_PERMISSION_BACKGROUND_LOCATION_REQUEST_CODE: {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
              return;

            } else {
              Toast.makeText(this, "Permission for background location not granted.", Toast.LENGTH_LONG).show();
              // and then close the app.
            }
          }*/
      //-------------------------------------------------------------------------------------------- +++++
      case MY_PERMISSION_EXTERNAL_REQUEST_CODE: {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          setupPermissions();
          return;

        } else {
          Toast.makeText(this, "no sd card access", Toast.LENGTH_LONG).show();
        }
        break;
      }
      case MY_PERMISSION_EXTERNAL_READ_REQUEST_CODE: {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          return;

        } else {
          Toast.makeText(this, "no sd card access", Toast.LENGTH_LONG).show();
        }
        break;
      }
      //-------------------------------------------------------------------------------------------- -----
      // Other permissions could go down here

    }
  }

  @TargetApi(26)
  private void requestNetwork() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return;
    }

    if (networkSpecifier == null) {
      Log.d(TAG_NAN, "No NetworkSpecifier Created ");
      return;
    }
    Log.d(TAG_NAN, "building network interface");
    Log.d(TAG_NAN, "using networkspecifier: " + networkSpecifier.toString());
    NetworkRequest networkRequest = new NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build();

    Log.d(TAG_NAN, "finish building network interface");
    connectivityManager.requestNetwork(networkRequest, new NetworkCallback() {
      @Override
      public void onAvailable(Network network) {
        super.onAvailable(network);
        activeAwareNetwork = network;
        Log.d(TAG_NAN, "Network available: " + network.toString());
      }

      @Override
      public void onLosing(Network network, int maxMsToLive) {
        super.onLosing(network, maxMsToLive);
        Log.d(TAG_NAN, "Network losing: " + network + ", maxMsToLive=" + maxMsToLive);
      }

      @Override
      public void onLost(Network network) {
        super.onLost(network);
        if (network.equals(activeAwareNetwork)) {
          activeAwareNetwork = null;
        }
        Toast.makeText(MainActivity.this, "lost network", Toast.LENGTH_LONG).show();
        Log.d(TAG_NAN, "Network lost: " + network);
      }

      @Override
      public void onUnavailable() {
        super.onUnavailable();
        Toast.makeText(MainActivity.this, "onUnavailable", Toast.LENGTH_SHORT).show();
        Log.d(TAG_NAN, "Network request unavailable.");
      }

      @RequiresApi(api = Build.VERSION_CODES.Q)
      @Override
      public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
        super.onCapabilitiesChanged(network, networkCapabilities);
        Toast.makeText(MainActivity.this, "onCapabilitiesChanged", Toast.LENGTH_SHORT).show();
        WifiAwareNetworkInfo peerAwareInfo = (WifiAwareNetworkInfo) networkCapabilities.getTransportInfo();
        peerIpv6 = peerAwareInfo.getPeerIpv6Addr();
        peerPort = peerAwareInfo.getPort();
        Log.d(TAG_NAN, "Capabilities changed. network=" + network
                + ", peerIpv6=" + peerIpv6
                + ", peerPort=" + peerPort
                + ", transportInfo=" + peerAwareInfo);
        setStatus("Ready for file transfer\nSend File when both IPv6 shown");
      }

      //-------------------------------------------------------------------------------------------- +++++
      @Override
      public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
        super.onLinkPropertiesChanged(network, linkProperties);
        //TODO: create socketServer on different thread to transfer files
        Toast.makeText(MainActivity.this, "onLinkPropertiesChanged", Toast.LENGTH_SHORT).show();
        Log.d(TAG_NAN, "Link properties changed. network=" + network
                + ", iface=" + linkProperties.getInterfaceName()
                + ", addresses=" + linkProperties.getLinkAddresses());
        try {
          //Log.d("myTag", "iface name: " + linkProperties.getInterfaceName());
          //Log.d("myTag", "iface link addr: " + linkProperties.getLinkAddresses());

          NetworkInterface awareNi = NetworkInterface.getByName(
                  linkProperties.getInterfaceName());
          /*Inet6Address ipv6 = null;
          Enumeration<NetworkInterface> ifcs = NetworkInterface.getNetworkInterfaces();
          while (ifcs.hasMoreElements()) {
            NetworkInterface iface = ifcs.nextElement();
            Log.d("myTag", "iface: " + iface.toString());
          }*/

          Enumeration<InetAddress> Addresses = awareNi.getInetAddresses();
          while (Addresses.hasMoreElements()) {
            InetAddress addr = Addresses.nextElement();
            if (addr instanceof Inet6Address) {
              Log.d(TAG_NAN, "netinterface ipv6 address: " + addr.toString());
              if (((Inet6Address) addr).isLinkLocalAddress()) {
                ipv6 = Inet6Address.getByAddress("WifiAware", addr.getAddress(), awareNi);
                myIP = addr.getAddress();
                Log.d(TAG_NAN, "Local Wi-Fi Aware IPv6 selected: " + ipv6
                        + ", raw=" + Inet6AddressBytesToString(myIP));
                if (publishDiscoverySession != null && peerHandle != null) {
                  publishDiscoverySession.sendMessage(peerHandle, IP_ADDRESS_MESSAGE, myIP);
                  Log.d(TAG_NAN, "Sent local IPv6 to peer via publish session.");
                } else if (subscribeDiscoverySession != null && peerHandle != null) {
                  subscribeDiscoverySession.sendMessage(peerHandle, IP_ADDRESS_MESSAGE, myIP);
                  Log.d(TAG_NAN, "Sent local IPv6 to peer via subscribe session.");
                }
                break;
              }
            }
          }
        } catch (SocketException e) {
          Log.d(TAG_NAN, "socket exception " + e.toString());
        } catch (Exception e) {
          //EXCEPTION!!! java.lang.NullPointerException: Attempt to invoke virtual method 'java.util.Enumeration java.net.NetworkInterface.getInetAddresses()' on a null object reference
          Log.d(TAG_NAN, "EXCEPTION!!! " + e.toString());
        }
        //startServer(0,3,ipv6);
        // should be done in a separate thread
        /*
        startServer
        ServerSocket ss = new ServerSocket(0, 5, ipv6);
        int port = ss.getLocalPort();    */
        //TODO: need to send this port via messages to other device to finish client conn info

        // should be done in a separate thread
        // obtain server IPv6 and port number out-of-band
        //TODO: Retrieve address:port IPv6 before this client thread can be created
        /*
        Socket cs = network.getSocketFactory().createSocket(serverIpv6, serverPort);  */
      }
      //-------------------------------------------------------------------------------------------- -----

    });
  }

  /**
   * Resuming activity
   *
   */
  @Override
  @TargetApi(26)
  protected void onResume() {
    super.onResume();

    String status = null;
    Log.d(TAG_NAN, "Current phone build" + Build.VERSION.SDK_INT + "\tMinimum:" + Build.VERSION_CODES.O);
    boolean hasNan = getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
    Log.d(TAG_NAN, "Supported Aware: " + hasNan);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasNan) {
      Log.d(TAG_NAN, "Entering OnResume is executed");
      if (wifiAwareManager != null) {
        IntentFilter filter = new IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED);
        broadcastReceiver = new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            String status = "";
            wifiAwareManager.getCharacteristics();
            boolean nanAvailable = wifiAwareManager.isAvailable();
            Log.d(TAG_NAN, "NAN is available");
            if (nanAvailable) {
              attachToNanSession();
              status = "NAN has become Available";
              Log.d(TAG_NAN, "NAN attached");
            } else {
              status = "NAN has become Unavailable";
              Log.d(TAG_NAN, "NAN unavailable");
            }

            setStatus(status);
          }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          getApplicationContext().registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
          getApplicationContext().registerReceiver(broadcastReceiver, filter);
        }

        boolean nanAvailable = wifiAwareManager.isAvailable();
        if (nanAvailable) {
          attachToNanSession();
          status = "NAN is Available";
        } else {
          status = "NAN is Unavailable";
        }
      } else {
        status = "Cannot get WifiAwareManager";
      }
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      status = "NAN is only supported in O+";
    } else {
      status = "Device does not have NAN";
    }

    setStatus(status);
  }

  /**
   * Handles attaching to NAN session.
   *
   */
  @TargetApi(26)
  private void attachToNanSession() {

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return;
    }

    // Only once
    if (wifiAwareSession != null) {
      return;
    }

    if (wifiAwareManager == null || !wifiAwareManager.isAvailable()) {
      setStatus("NAN is Unavailable in attach");
      return;
    }

    if (!hasWifiAwarePermissions()) {
      setupPermissions();
      return;
    }
    Log.d(TAG_NAN, "Wi-Fi Aware is available, attaching to NAN cluster (bringing up NAN interface)...");
    wifiAwareManager.attach(new AttachCallback() {
      @Override
      public void onAttached(WifiAwareSession session) {
        super.onAttached(session);

        closeSession();
        wifiAwareSession = session;
        setHaveSession(true);
      }

      @Override
      public void onAttachFailed() {
        super.onAttachFailed();
        setHaveSession(false);
        setStatus("attach() failed.");
      }

    }, new IdentityChangedListener() {
      @Override
      public void onIdentityChanged(byte[] mac) {
        super.onIdentityChanged(mac);
        setMacAddress(mac);
      }
    }, null);
  }

  @TargetApi(26)
  private void publishService() {
    PublishConfig config;
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return;
    }

    if (wifiAwareSession == null) {
      Log.e(TAG_NAN, "Cannot publish: Wi-Fi Aware session is not available");
      setStatus("Wi-Fi Aware is not available. Check device support.");
      Toast.makeText(this, "Wi-Fi Aware is not available on this device", Toast.LENGTH_LONG).show();
      return;
    }

    Log.d(TAG_NAN, "building publish session " + SERVICE_NAME);

    if (pubType == PublishConfig.PUBLISH_TYPE_UNSOLICITED)
      Log.d(TAG_NAN, "publish unsolicited " + pubType);
    else if (pubType == PublishConfig.PUBLISH_TYPE_SOLICITED)
      Log.d(TAG_NAN, "publish solicited " + pubType);
    if (!EncryptType.equals("open") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      WifiAwareDataPathSecurityConfig secConfig = null;
      if (EncryptType.equals("pmk")) {
        Log.d(TAG_PREFS, "pmk " + new String(pmk));
        secConfig = new WifiAwareDataPathSecurityConfig.Builder(WIFI_AWARE_CIPHER_SUITE_NCS_SK_128)
                .setPmk(pmk)
          //      .setPmkId(pmkid)
                .build();
      } else if (EncryptType.equals("psk")) {
        Log.d(TAG_PREFS, "psk " + psk);
        secConfig = new WifiAwareDataPathSecurityConfig.Builder(WIFI_AWARE_CIPHER_SUITE_NCS_SK_128)
                .setPskPassphrase(psk)
                .build();
      }
       config = new PublishConfig.Builder()
              .setServiceName(SERVICE_NAME)
              .setServiceSpecificInfo(serviceInfo)
              .setDataPathSecurityConfig(secConfig)
              .setPublishType(pubType)
              .build();
    } else {
        config = new PublishConfig.Builder()
                .setServiceName(SERVICE_NAME)
                .setServiceSpecificInfo(serviceInfo)
                .setPublishType(pubType)
                .build();
    }


    //-------------------------------------------------------------------------------------------- +++++
    Log.d(TAG_NAN, "build finish");
    if (!hasWifiAwarePermissions()) {
      setupPermissions();
      return;
    }
    wifiAwareSession.publish(config, new DiscoverySessionCallback() {
      @Override
      public void onPublishStarted(@NonNull PublishDiscoverySession session) {
        super.onPublishStarted(session);

        publishDiscoverySession = session;
        startServer(0, 3);
        setStatus("Publisher started. Waiting for peer, then NDP starts automatically.");
        Button sendBtn = (Button) findViewById(R.id.sendbtn);
        sendBtn.setEnabled(true);
        Button responderButton = (Button) findViewById(R.id.responderButton);
        responderButton.setEnabled(true);
        startResponderNdpIfReady("publish started");
        if (publishDiscoverySession != null && peerHandle != null) {
          publishDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, myMac);
          sendServerPortToPeer();
          Log.d(TAG_NAN, "onPublishStarted sending mac");

          Button initiatorButton = (Button) findViewById(R.id.initiatorButton);
          initiatorButton.setEnabled(false);

        }
      }

      @Override
      public void onServiceDiscovered(PeerHandle peerHandle_, byte[] serviceSpecificInfo, List<byte[]> matchFilter) {
        super.onServiceDiscovered(peerHandle, serviceSpecificInfo, matchFilter);

        peerHandle = peerHandle_;
        Log.d(TAG_NAN, "onServiceDiscovered found peerHandle");
        startResponderNdpIfReady("publisher discovered peer");
      }

      @Override
      public void onMessageReceived(PeerHandle peerHandle_, byte[] message) {
        super.onMessageReceived(peerHandle, message);
        Log.d(TAG_NAN, "Publisher received discovery message. length=" + message.length);
        if (message.length == 2) {
          portToUse = byteToPortInt(message);
          Log.d(TAG_FILE, "Publisher received peer server port: " + portToUse);
        } else if (message.length == 6) {
          Log.d(TAG_NAN, "Publisher received peer MAC.");
          setOtherMacAddress(message);
          //Toast.makeText(MainActivity.this, "mac received", Toast.LENGTH_SHORT).show();
        } else if (message.length == 16) {
          Log.d(TAG_NAN, "Publisher received peer IPv6: " + Inet6AddressBytesToString(message));
          setOtherIPAddress(message);
          //Toast.makeText(MainActivity.this, "ip received", Toast.LENGTH_SHORT).show();
        } else if (message.length > 16) {
          Log.d(TAG_NAN, "Publisher received app message payload.");
          setMessage(message);
          //Toast.makeText(MainActivity.this, "message received", Toast.LENGTH_SHORT).show();
        }

        peerHandle = peerHandle_;

        if (publishDiscoverySession != null && peerHandle != null) {
          publishDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, myMac);
          sendServerPortToPeer();
          Log.d(TAG_NAN, "onMessageReceived sending mac");
          Button responderButton = (Button) findViewById(R.id.responderButton);
          Button initiatorButton = (Button) findViewById(R.id.initiatorButton);
          initiatorButton.setEnabled(false);
          responderButton.setEnabled(true);
          startResponderNdpIfReady("publisher received peer message");
        }
      }
    }, null);
    //-------------------------------------------------------------------------------------------- -----
  }

  //-------------------------------------------------------------------------------------------- +++++
  @TargetApi(26)
  private void subscribeToService() {

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return;
    }

    if (wifiAwareSession == null) {
      Log.e(TAG_NAN, "Cannot subscribe: Wi-Fi Aware session is not available");
      setStatus("Wi-Fi Aware is not available. Check device support.");
      Toast.makeText(this, "Wi-Fi Aware is not available on this device", Toast.LENGTH_LONG).show();
      return;
    }

    Log.d(TAG_NAN, "building subscribe session");

    if (subType == SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)
      Log.d(TAG_NAN, "subscribe active ");
    else if (subType == SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)
      Log.d(TAG_NAN, "subscribe passive ");

    SubscribeConfig config = new SubscribeConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .setServiceSpecificInfo(serviceInfo)
            .setSubscribeType(subType)
            .build();
    Log.d(TAG_NAN, "build finish");
    if (!hasWifiAwarePermissions()) {
      setupPermissions();
      return;
    }
    wifiAwareSession.subscribe(config, new DiscoverySessionCallback() {

      @Override
      public void onServiceDiscovered(PeerHandle peerHandle_, byte[] serviceSpecificInfo, List<byte[]> matchFilter) {
        super.onServiceDiscovered(peerHandle, serviceSpecificInfo, matchFilter);

        peerHandle = peerHandle_;

        if (subscribeDiscoverySession != null && peerHandle != null) {
          subscribeDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, myMac);
          sendServerPortToPeer();
          Log.d(TAG_NAN, "onServiceDiscovered send mac");
          Button responderButton = (Button) findViewById(R.id.responderButton);
          Button initiatorButton = (Button) findViewById(R.id.initiatorButton);
          initiatorButton.setEnabled(true);
          responderButton.setEnabled(false);
          startInitiatorNdpIfReady("subscriber discovered peer");
        }
      }

      @Override
      public void onSubscribeStarted(@NonNull SubscribeDiscoverySession session) {
        super.onSubscribeStarted(session);

        subscribeDiscoverySession = session;
        startServer(0, 3);
        setStatus("Subscriber started. Waiting for peer, then NDP starts automatically.");

        if (subscribeDiscoverySession != null && peerHandle != null) {
          subscribeDiscoverySession.sendMessage(peerHandle, MAC_ADDRESS_MESSAGE, myMac);
          sendServerPortToPeer();
          Log.d(TAG_NAN, "onServiceStarted send mac");
          Button responderButton = (Button) findViewById(R.id.responderButton);
          Button initiatorButton = (Button) findViewById(R.id.initiatorButton);
          initiatorButton.setEnabled(true);
          responderButton.setEnabled(false);
          startInitiatorNdpIfReady("subscribe started with existing peer");
        }
      }

      @Override
      public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
        super.onMessageReceived(peerHandle, message);
        Log.d(TAG_NAN, "Subscriber received discovery message. length=" + message.length);
        //Toast.makeText(MainActivity.this, "received", Toast.LENGTH_LONG).show();
        if (message.length == 2) {
          portToUse = byteToPortInt(message);
          Log.d(TAG_FILE, "Subscriber received peer server port: " + portToUse);
        } else if (message.length == 6) {
          Log.d(TAG_NAN, "Subscriber received peer MAC.");
          setOtherMacAddress(message);
          //Toast.makeText(MainActivity.this, "mac received", Toast.LENGTH_SHORT).show();
        } else if (message.length == 16) {
          Log.d(TAG_NAN, "Subscriber received peer IPv6: " + Inet6AddressBytesToString(message));
          setOtherIPAddress(message);
          //Toast.makeText(MainActivity.this, "ip received", Toast.LENGTH_SHORT).show();
        } else if (message.length > 16) {
          Log.d(TAG_NAN, "Subscriber received app message payload.");
          setMessage(message);
          //Toast.makeText(MainActivity.this, "message received", Toast.LENGTH_SHORT).show();
        }
        startInitiatorNdpIfReady("subscriber received peer message");
      }
    }, null);
  }
  //-------------------------------------------------------------------------------------------- -----

  /**
   * Handles cleanup of the activity.
   *
   */
  @Override
  protected void onPause() {
    super.onPause();
    if (broadcastReceiver != null) {
      try {
        getApplicationContext().unregisterReceiver(broadcastReceiver);
      } catch (IllegalArgumentException e) {
        // Receiver was not registered
        Log.d(TAG_NAN, "Receiver was not registered");
      }
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    closeSession();
  }

  private void closeSession() {
    shuttingDown = true;

    if (publishDiscoverySession != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        publishDiscoverySession.close();
      }
      publishDiscoverySession = null;
    }

    if (subscribeDiscoverySession != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        subscribeDiscoverySession.close();
      }
      subscribeDiscoverySession = null;
    }

    if (wifiAwareSession != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        wifiAwareSession.close();
      }
      wifiAwareSession = null;
    }
    if (serverSocket != null) {
      try {
        serverSocket.close();
      } catch (IOException e) {
        Log.d(TAG_FILE, "Error closing file receiver server socket: " + e);
      }
      serverSocket = null;
    }
    responderNdpRequested = false;
    initiatorNdpRequested = false;
    fileServerStarted = false;
    activeAwareNetwork = null;
  }

  /**
   * Handles creating the options menu.
   *
   * @param menu
   * @return
   */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }

  /**
   * Handles when an option is selected from the menu.
   *
   * @param item
   * @return
   */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();
    //-------------------------------------------------------------------------------------------- +++++

    //noinspection SimplifiableIfStatement
    if (id == R.id.action_settings) {
      Intent startSettingsActivity = new Intent(this, SettingsActivity.class);
      startActivity(startSettingsActivity);
      return true;
    }
    if (id == R.id.close) {
      closeSession();
      finish();
      return true;
    }

    return super.onOptionsItemSelected(item);
    //-------------------------------------------------------------------------------------------- -----
  }

  /**
   * Helper to set the status field.
   *
   * @param status
   */
  private void setStatus(String status) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          setStatus(status);
        }
      });
      return;
    }
    TextView textView = (TextView)findViewById(R.id.status);
    if (textView != null) {
      textView.setText(status);
    }
  }

  private void setHaveSession(boolean haveSession) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          setHaveSession(haveSession);
        }
      });
      return;
    }
    CheckBox cbHaveSession = (CheckBox)findViewById(R.id.haveSession);
    if (cbHaveSession != null) {
      cbHaveSession.setChecked(haveSession);
    }
  }

  private void setMacAddress(byte[] mac) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          setMacAddress(mac);
        }
      });
      return;
    }
    myMac = mac;
    String macAddress = String.format("%02x:%02x:%02x:%02x:%02x:%02x", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
    EditText editText = (EditText)findViewById(R.id.macAddress);
    if (editText != null) {
      editText.setText(macAddress);
    }
  }

  private void setOtherMacAddress(byte[] mac) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          setOtherMacAddress(mac);
        }
      });
      return;
    }
    otherMac = mac;
    String macAddress = String.format("%02x:%02x:%02x:%02x:%02x:%02x", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
    EditText editText = (EditText)findViewById(R.id.otherMac);
    if (editText != null) {
      editText.setText(macAddress);
    }
  }

  //-------------------------------------------------------------------------------------------- +++++
  private void setOtherIPAddress(byte[] ip) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          setOtherIPAddress(ip);
        }
      });
      return;
    }
    otherIP = ip;
    try {
      String ipAddr = Inet6Address.getByAddress(otherIP).toString();
      EditText editText = (EditText) findViewById(R.id.IPv6text);
      if (editText != null) {
        editText.setText(ipAddr);
      }
    } catch (UnknownHostException e) {
      Log.d(TAG_NAN, "socket exception " + e.toString());
    }
  }
  private void setMessage(byte[] msg) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          setMessage(msg);
        }
      });
      return;
    }
    String outmsg = new String(msg).replace("messageToBeSent: ","");
    EditText editText = (EditText) findViewById(R.id.msgtext);
    if (editText != null) {
      editText.setText(outmsg);
    }
  }

  public int byteToPortInt(byte[] bytes){
    return ((bytes[1] & 0xFF) << 8 | (bytes[0] & 0xFF));
  }

  public byte[] portToBytes(int port){
    byte[] data = new byte [2];
    data[0] = (byte) (port & 0xFF);
    data[1] = (byte) ((port >> 8) & 0xFF);
    return data;
  }

  private String Inet6AddressBytesToString(byte[] ip) {
    try {
      return Inet6Address.getByAddress(ip).toString();
    } catch (UnknownHostException e) {
      return "invalid-ip-bytes";
    }
  }

  @TargetApi(26)
  public void startServer(final int port, final int backlog) {
    if (fileServerStarted && serverSocket != null && !serverSocket.isClosed()) {
      Log.d(TAG_FILE, "Receiver server already running. localPort=" + serverSocket.getLocalPort());
      return;
    }
    shuttingDown = false;
    fileServerStarted = true;
    Log.d(TAG_FILE, "Starting file receiver server. requestedPort=" + port + ", backlog=" + backlog);
    Runnable serverTask = new Runnable() {
      @Override
      public void run() {
        try{
          Log.d(TAG_FILE, "Receiver server thread running.");
          serverSocket = new ServerSocket(port, backlog);
          portToUse = serverSocket.getLocalPort();
          Log.d(TAG_FILE, "Receiver server bound. localPort=" + portToUse
                  + ", socket=" + serverSocket
                  + ", encryptType=" + EncryptType);
          sendServerPortToPeer();
          startResponderNdpIfReady("file receiver server bound");
          //ServerSocket serverSocket = new ServerSocket();
          while (true) {
            portToUse = serverSocket.getLocalPort();
            if (EncryptType.equals("open")) {
              portOnSystem = portToBytes(serverSocket.getLocalPort());
            }

            Log.d(TAG_FILE, "Receiver waiting for incoming socket. localPort=" + portToUse);
            Socket clientSocket = serverSocket.accept();
            clientSocket.setKeepAlive(true);
            clientSocket.setSoTimeout(SOCKET_READ_TIMEOUT_MS);
            Log.d(TAG_FILE, "Receiver accepted connection. remote=" + clientSocket.getRemoteSocketAddress()
                    + ", local=" + clientSocket.getLocalSocketAddress());
            try {
              receiveSingleFile(clientSocket);
            } catch (IOException e) {
              // Keep the receiver alive after a single failed transfer so the peer can retry
              // without forcing both devices to restart discovery and NDP.
              Log.e(TAG_FILE, "Receiver rejected one incoming transfer but server remains active.", e);
            }

          }
        } catch (IOException e) {
          fileServerStarted = false;
          if (shuttingDown) {
            Log.d(TAG_FILE, "Receiver server stopped during shutdown.");
          } else {
            Log.e(TAG_FILE, "Receiver server socket/file exception.", e);
            setStatus("File receiver stopped: " + e.getMessage());
          }
        }
      }
    };
    Thread serverThread = new Thread(serverTask);
    serverThread.start();

  }

  /**
   * Receives one file from an already accepted socket.
   *
   * <p>Input is a socket whose stream contains the protocol header written by
   * {@link #clientSendFile(Uri, Inet6Address, int)}: UTF file name, long file size, UTF MIME type,
   * followed by the raw file bytes. Output is a saved file in Downloads/NanR3. If the sender closes
   * early or storage fails, the method throws after marking/removing the partial MediaStore entry.</p>
   *
   * @throws IOException when the socket stream, header, byte count, or destination file fails
   */
  private void receiveSingleFile(Socket clientSocket) throws IOException {
    String fileName = null;
    IncomingFileTarget target = null;
    long totalRead = 0;

    try (Socket socket = clientSocket;
         DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), TRANSFER_BUFFER_SIZE))) {
      fileName = sanitizeFileName(in.readUTF());
      long fileSizeInBytes = in.readLong();
      String mimeType = in.readUTF();
      if (mimeType == null || mimeType.length() == 0) {
        mimeType = "application/octet-stream";
      }

      Log.d(TAG_FILE, "Receiver header read. fileName=" + fileName
              + ", fileSize=" + fileSizeInBytes
              + ", mimeType=" + mimeType);
      target = openIncomingFileTarget(fileName, mimeType);
      if (target == null || target.outputStream == null) {
        throw new IOException("Could not open output file for " + fileName);
      }

      byte[] buffer = new byte[TRANSFER_BUFFER_SIZE];
      Log.d(TAG_FILE, "Receiver body transfer started. fileName=" + fileName
              + ", expectedBytes=" + fileSizeInBytes);
      setStatus("Receiving " + fileName + "...");

      if (fileSizeInBytes >= 0) {
        totalRead = copyKnownLength(in, target.outputStream, buffer, fileName, fileSizeInBytes);
      } else {
        totalRead = copyUntilEof(in, target.outputStream, buffer, fileName);
      }
      target.outputStream.flush();
      target.markCompleted(getContentResolver());
      Log.d(TAG_FILE, "Receiver body transfer finished. fileName=" + fileName
              + ", totalBytes=" + totalRead
              + ", expectedBytes=" + fileSizeInBytes);
      setStatus("Received file: " + fileName);
    } catch (IOException e) {
      if (target != null) {
        target.abort(getContentResolver());
      }
      Log.e(TAG_FILE, "Receiver failed while saving " + fileName + ". bytesRead=" + totalRead, e);
      setStatus("Receive failed: " + e.getMessage());
      throw e;
    } finally {
      if (target != null) {
        target.closeQuietly();
      }
    }
  }

  private long copyKnownLength(DataInputStream in, OutputStream out, byte[] buffer,
                               String fileName, long fileSizeInBytes) throws IOException {
    long totalRead = 0;
    while (totalRead < fileSizeInBytes) {
      int maxRead = (int) Math.min(buffer.length, fileSizeInBytes - totalRead);
      int read = in.read(buffer, 0, maxRead);
      if (read < 0) {
        throw new EOFException("Transfer ended early: " + totalRead + "/" + fileSizeInBytes + " bytes");
      }
      out.write(buffer, 0, read);
      totalRead += read;
      logTransferProgress("Receiver", "Receiving", fileName, totalRead, fileSizeInBytes);
    }
    return totalRead;
  }

  private long copyUntilEof(InputStream in, OutputStream out, byte[] buffer, String fileName) throws IOException {
    long totalRead = 0;
    int read;
    while ((read = in.read(buffer)) > 0) {
      out.write(buffer, 0, read);
      totalRead += read;
      if (totalRead % PROGRESS_LOG_BYTES < read) {
        Log.d(TAG_FILE, "Receiver progress. fileName=" + fileName + ", bytes=" + totalRead);
        setStatus("Receiving " + fileName + ": " + totalRead + " bytes");
      }
    }
    return totalRead;
  }

  private IncomingFileTarget openIncomingFileTarget(String fileName, String mimeType) throws IOException {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ContentValues values = new ContentValues();
      values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
      values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
      values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + APP_LABEL);
      values.put(MediaStore.MediaColumns.IS_PENDING, 1);
      Log.d(TAG_FILE, "Creating MediaStore download entry. displayName=" + fileName
              + ", mimeType=" + mimeType
              + ", relativePath=" + Environment.DIRECTORY_DOWNLOADS + "/" + APP_LABEL);
      Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
      if (uri == null) {
        Log.e(TAG_FILE, "MediaStore insert returned null for incoming file.");
        return null;
      }
      Log.d(TAG_FILE, "MediaStore download entry created. uri=" + uri);
      return new IncomingFileTarget(getContentResolver().openOutputStream(uri), uri);
    }

    File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), APP_LABEL);
    if (!directory.exists() && !directory.mkdirs()) {
      Log.e(TAG_FILE, "Could not create legacy download directory: " + directory.getAbsolutePath());
      return null;
    }
    File outputFile = new File(directory, fileName);
    Log.d(TAG_FILE, "Creating legacy download file: " + outputFile.getAbsolutePath());
    return new IncomingFileTarget(new FileOutputStream(outputFile), null);
  }

  /**
   * Holds the destination stream plus optional MediaStore Uri for one incoming file.
   *
   * <p>On Android 10+ files are created with {@code IS_PENDING=1}. A successful transfer calls
   * {@link #markCompleted(ContentResolver)} so other apps can see the file; a failed transfer calls
   * {@link #abort(ContentResolver)} to remove a partial entry.</p>
   */
  private static class IncomingFileTarget {
    final OutputStream outputStream;
    final Uri uri;

    IncomingFileTarget(OutputStream outputStream, Uri uri) {
      this.outputStream = outputStream;
      this.uri = uri;
    }

    void markCompleted(ContentResolver resolver) {
      if (uri == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return;
      }
      ContentValues values = new ContentValues();
      values.put(MediaStore.MediaColumns.IS_PENDING, 0);
      resolver.update(uri, values, null, null);
    }

    void abort(ContentResolver resolver) {
      closeQuietly();
      if (uri != null) {
        resolver.delete(uri, null, null);
      }
    }

    void closeQuietly() {
      if (outputStream == null) {
        return;
      }
      try {
        outputStream.close();
      } catch (IOException e) {
        Log.d(TAG_FILE, "Error closing incoming file stream: " + e);
      }
    }
  }

  private String sanitizeFileName(String fileName) {
    if (fileName == null || fileName.trim().length() == 0) {
      return APP_LABEL + "-file";
    }
    return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
  }

  /**
   * Logs and displays progress at coarse byte boundaries to keep UI updates inexpensive.
   *
   * @param logPrefix "Sender" or "Receiver" for logcat readability
   * @param statusVerb user-facing verb, for example "Sending" or "Receiving"
   * @param fileName sanitized display name
   * @param transferredBytes bytes copied so far
   * @param totalBytes expected file size from the protocol header
   */
  private void logTransferProgress(String logPrefix, String statusVerb, String fileName,
                                   long transferredBytes, long totalBytes) {
    if (totalBytes <= 0 || transferredBytes % PROGRESS_LOG_BYTES >= TRANSFER_BUFFER_SIZE) {
      return;
    }
    float percent = (float) transferredBytes / totalBytes * 100;
    Log.d(TAG_FILE, logPrefix + " progress. fileName=" + fileName
            + ", bytes=" + transferredBytes
            + "/" + totalBytes
            + ", percent=" + String.format("%.1f", percent));
    setStatus(statusVerb + " " + fileName + ": " + String.format("%.1f", percent) + "%");
  }

  private String getDisplayName(Uri uri) {
    Log.d(TAG_FILE, "Resolving display name for uri=" + uri);
    Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
    if (cursor != null) {
      try {
        if (cursor.moveToFirst()) {
          int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
          if (index >= 0) {
            return cursor.getString(index);
          }
        }
      } finally {
        cursor.close();
      }
    }
    return APP_LABEL + "-file";
  }

  private long getFileSize(Uri uri) {
    Log.d(TAG_FILE, "Resolving file size for uri=" + uri);
    Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null);
    if (cursor != null) {
      try {
        if (cursor.moveToFirst()) {
          int index = cursor.getColumnIndex(OpenableColumns.SIZE);
          if (index >= 0 && !cursor.isNull(index)) {
            return cursor.getLong(index);
          }
        }
      } finally {
        cursor.close();
      }
    }
    return -1;
  }

  public void clientSendFile(final Uri fileUri, final Inet6Address serverIP, final int serverPort) {
    Log.d(TAG_FILE, "Starting file sender. uri=" + fileUri + ", serverIP=" + serverIP + ", serverPort=" + serverPort);
    Runnable clientTask = new Runnable() {
      @Override
      public void run() {
        Log.d(TAG_FILE, "Sender thread running. serverIP=" + serverIP.getHostAddress() + ", serverPort=" + serverPort);
        try {
          sendSingleFile(fileUri, serverIP, serverPort);
        } catch(FileNotFoundException e){
          Log.e(TAG_FILE, "Sender selected file not found.", e);
          setStatus("Selected file was not found.");
        } catch(IOException e){
          Log.e(TAG_FILE, "Sender file transfer failed.", e);
          setStatus("File send failed: " + e.getMessage());
        }

      }
    };
    Thread clientThread = new Thread(clientTask);
    clientThread.start();

  }

  /**
   * Sends one selected document to the peer file receiver.
   *
   * <p>Input is a SAF {@link Uri} plus the scoped IPv6/port resolved by
   * {@link #chooseFileForSend()}. Output is the on-the-wire protocol consumed by
   * {@link #receiveSingleFile(Socket)}. The method retries socket connection because NDP callbacks
   * and discovery messages can arrive slightly before the data path is fully routable.</p>
   *
   * @throws IOException when the file cannot be opened, the socket cannot connect, or the stream fails
   */
  private void sendSingleFile(Uri fileUri, Inet6Address serverIP, int serverPort) throws IOException {
    String fileName = sanitizeFileName(getDisplayName(fileUri));
    long fileSizeInBytes = getFileSize(fileUri);
    String mimeType = getContentResolver().getType(fileUri);
    if (mimeType == null) {
      mimeType = "application/octet-stream";
    }

    try (Socket clientSocket = openConnectedSocket(serverIP, serverPort);
         DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream(), TRANSFER_BUFFER_SIZE));
         InputStream in = new BufferedInputStream(openRequiredInputStream(fileUri), TRANSFER_BUFFER_SIZE)) {
      Log.d(TAG_FILE, "Sender header prepared. fileName=" + fileName
              + ", fileSize=" + fileSizeInBytes
              + ", mimeType=" + mimeType);
      dos.writeUTF(fileName);
      dos.writeLong(fileSizeInBytes);
      dos.writeUTF(mimeType);

      byte[] buffer = new byte[TRANSFER_BUFFER_SIZE];
      int count;
      long totalSent = 0;
      Log.d(TAG_FILE, "Sender body transfer started. fileName=" + fileName);
      setStatus("Sending " + fileName + "...");
      while ((count = in.read(buffer)) > 0) {
        dos.write(buffer, 0, count);
        totalSent += count;
        logTransferProgress("Sender", "Sending", fileName, totalSent, fileSizeInBytes);
      }
      dos.flush();
      Log.d(TAG_FILE, "Sender body transfer finished. fileName=" + fileName
              + ", totalBytes=" + totalSent
              + ", expectedBytes=" + fileSizeInBytes);
      setStatus("Finished sending file: " + fileName);
    }
  }

  private InputStream openRequiredInputStream(Uri fileUri) throws IOException {
    InputStream inputStream = getContentResolver().openInputStream(fileUri);
    if (inputStream == null) {
      throw new FileNotFoundException("Could not open selected file: " + fileUri);
    }
    return inputStream;
  }

  private Socket openConnectedSocket(Inet6Address serverIP, int serverPort) throws IOException {
    IOException lastException = null;
    for (int attempt = 1; attempt <= SEND_CONNECT_MAX_ATTEMPTS; attempt++) {
      Socket socket = null;
      try {
        socket = activeAwareNetwork != null
                ? activeAwareNetwork.getSocketFactory().createSocket()
                : new Socket();
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(SOCKET_READ_TIMEOUT_MS);
        socket.connect(new InetSocketAddress(serverIP, serverPort), SOCKET_CONNECT_TIMEOUT_MS);
        Log.d(TAG_FILE, "Sender socket connected. attempt=" + attempt
                + ", remote=" + socket.getRemoteSocketAddress()
                + ", local=" + socket.getLocalSocketAddress()
                + ", network=" + activeAwareNetwork);
        return socket;
      } catch (IOException e) {
        lastException = e;
        Log.e(TAG_FILE, "Sender socket connect failed. attempt=" + attempt
                + "/" + SEND_CONNECT_MAX_ATTEMPTS
                + ", serverIP=" + serverIP
                + ", serverPort=" + serverPort, e);
        if (socket != null) {
          try {
            socket.close();
          } catch (IOException closeException) {
            Log.d(TAG_FILE, "Error closing failed sender socket: " + closeException);
          }
        }
        if (attempt < SEND_CONNECT_MAX_ATTEMPTS) {
          try {
            Thread.sleep(SEND_RETRY_DELAY_MS);
          } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while retrying file sender connection", interruptedException);
          }
        }
      }
    }
    throw lastException == null ? new IOException("Sender socket connect failed") : lastException;
  }

  //-------------------------------------------------------------------------------------------- -----

}
