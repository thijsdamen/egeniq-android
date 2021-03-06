package io.msgs.gcm;

import io.msgs.gcm.Subscription.Time;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import ch.boye.httpclientandroidlib.HttpEntity;
import ch.boye.httpclientandroidlib.NameValuePair;
import ch.boye.httpclientandroidlib.client.entity.UrlEncodedFormEntity;
import ch.boye.httpclientandroidlib.message.BasicNameValuePair;

import com.egeniq.BuildConfig;
import com.egeniq.utils.api.APIClient;
import com.egeniq.utils.api.APIException;
import com.egeniq.utils.api.APIUtils;

/**
 * Notification manager.
 * 
 * All methods are executed synchronously. You are yourself responsible for
 * wrapping the calls in an AsyncTask or something similar.
 */
public class NotificationManager {
    private final static String TAG = NotificationManager.class.getSimpleName();
    private final static boolean DEBUG = BuildConfig.DEBUG;
    
    private final static String NOTIFICATION_TOKEN_KEY = "notificationToken";
    private final static String DEVICE_FAMILY = "gcm";

    private final static SimpleDateFormat DATE_FORMAT;
    static {
        DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private final Context _context;
    private final String _serviceBaseURL;
    private final String _appId;

    private APIClient _apiClient;

    /**
     * Constructor.
     * 
     * @param context
     * @param serviceBaseURL
     */
    public NotificationManager(Context context, String serviceBaseURL, String appId) {
        _context = context;
        _serviceBaseURL = serviceBaseURL;
        _appId = appId;
    }

    /**
     * Returns the API client.
     * 
     * @return API client.
     */
    protected APIClient _getAPIClient() {
        if (_apiClient == null) {
            _apiClient = new APIClient(_serviceBaseURL);
        }

        return _apiClient;
    }
    
    /**
     * Is registered?
     * 
     * @return Is registered?
     */
    public boolean isRegistered() {
        return getNotificationToken() != null;
    }
    
    /**
     * Returns the current notification token.
     * 
     * @return Notification token.
     */
    public String getNotificationToken() {
        return _context.getSharedPreferences(TAG, Context.MODE_PRIVATE).getString(_appId + "." + NOTIFICATION_TOKEN_KEY, null);
    }
    
    /**
     * Register device.
     * 
     * @param registrationId
     */
    public void registerDevice(final String registrationId) throws APIException {
        registerDevice(registrationId, null);
    }

    /**
     * Register device and subscribe to the given channel.
     * 
     * @param registrationId
     * @param channelId
     */
    public void registerDevice(final String registrationId, final String channelId) throws APIException {
        try {
            if (DEBUG) {
                Log.d(TAG, "Send device registration request for registration ID: " + registrationId + " app ID: " + _appId);
            }

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("appId", _appId));
            params.add(new BasicNameValuePair("deviceFamily", DEVICE_FAMILY));
            params.add(new BasicNameValuePair("deviceToken", registrationId));
            
            if (channelId != null) {
                params.add(new BasicNameValuePair("channelId", channelId));
            }

            String path = "subscribers";
            String notificationToken = getNotificationToken();
            if (notificationToken != null) {
                if (DEBUG) {
                    Log.d(TAG, "Using existing notification token: " + notificationToken);
                }
                
                path = "subscribers/;update";
                params.add(new BasicNameValuePair("notificationToken", notificationToken));
            }

            HttpEntity entity = new UrlEncodedFormEntity(params);
            JSONObject result = _getAPIClient().post(path, entity);
            
            if (DEBUG) {
                Log.d(TAG, "Registration request sent");
            }
            
            if (notificationToken == null && result != null) {
                notificationToken = APIUtils.getString(result, "notificationToken", null);
                _setNotificationToken(notificationToken);

                if (DEBUG) {
                    Log.d(TAG, "Notification token: " + notificationToken);
                }
            } else if (notificationToken == null) {
                if (DEBUG) {
                    Log.e(TAG, "No notification token returned");
                }
            } 
            
            if (DEBUG) {
                Log.d(TAG, "Registration request processed");
            }
        } catch (Exception e) {
            if (DEBUG) {
                Log.e(TAG, "Error registering device", e);
            }

            if (!(e instanceof APIException)) {
                e = new APIException(e);
            }

            throw (APIException)e;
        }
    }

    /**
     * Returns a list of subscriptions for this device.
     * 
     * @return Subscriptions.
     * 
     * @throws APIException
     */
    public Subscription[] getSubscriptions() throws APIException {
        try {
            String notificationToken = getNotificationToken();
            if (notificationToken == null) {
                throw new APIException("not_registered", "Device is not registered");
            }

            JSONArray rawSubscriptions = _getAPIClient().getArray("subscriptions/" + _appId + "/" + notificationToken);

            ArrayList<Subscription> subscriptions = new ArrayList<Subscription>();
            for (int i = 0; i < rawSubscriptions.length(); i++) {
                JSONObject rawSubscription = rawSubscriptions.getJSONObject(i);

                Subscription subscription = new Subscription();
                subscription.setId(APIUtils.getInt(rawSubscription, "id", 0));
                subscription.setChannelId(APIUtils.getString(rawSubscription, "channelId", ""));

                String rawStartDate = APIUtils.getString(rawSubscription, "dateStart", null);
                Date startDate = rawStartDate == null ? null : DATE_FORMAT.parse(rawStartDate);
                String rawEndDate = APIUtils.getString(rawSubscription, "dateEnd", null);
                Date endDate = rawEndDate == null ? null : DATE_FORMAT.parse(rawEndDate);
                subscription.setDatePeriod(startDate, endDate);

                String rawStartTime = APIUtils.getString(rawSubscription, "timeStart", null);
                Time startTime = rawStartTime == null ? null : new Time(Integer.parseInt(rawStartTime.split(":")[0]), Integer.parseInt(rawStartTime.split(":")[1]));
                String rawEndTime = APIUtils.getString(rawSubscription, "timeEnd", null);
                Time endTime = rawEndTime == null ? null : new Time(Integer.parseInt(rawEndTime.split(":")[0]), Integer.parseInt(rawEndTime.split(":")[1]));
                subscription.setTimePeriod(startTime, endTime);

                int weekdays = 0;
                String rawDowSet = APIUtils.getString(rawSubscription, "dowSet", "");
                String[] rawDays = rawDowSet.split(",");
                for (String rawDay : rawDays) {
                    if (rawDay.equals("1")) {
                        weekdays &= Subscription.SUNDAY;
                    } else if (rawDay.equals("2")) {
                        weekdays &= Subscription.MONDAY;
                    } else if (rawDay.equals("3")) {
                        weekdays &= Subscription.TUESDAY;
                    } else if (rawDay.equals("4")) {
                        weekdays &= Subscription.WEDNESDAY;
                    } else if (rawDay.equals("5")) {
                        weekdays &= Subscription.THURSDAY;
                    } else if (rawDay.equals("6")) {
                        weekdays &= Subscription.FRIDAY;
                    } else if (rawDay.equals("7")) {
                        weekdays &= Subscription.SATURDAY;
                    }
                }
                subscription.setWeekdays(weekdays);

                subscriptions.add(subscription);
            }

            return subscriptions.toArray(new Subscription[0]);
        } catch (Exception e) {
            if (DEBUG) {
                Log.e(TAG, "Error adding subscription", e);
            }

            if (!(e instanceof APIException)) {
                e = new APIException(e);
            }

            throw (APIException)e;
        }
    }
    
    /**
     * Subscribe.
     * 
     * @param channelId Channel ID.
     * 
     * @throws APIException
     */
    public void subscribe(String channelId) throws APIException {
        subscribe(new Subscription().setChannelId(channelId));
    }

    /**
     * Subscribe.
     * 
     * @param subscription
     * 
     * @throws APIException
     */
    public void subscribe(Subscription subscription) throws APIException {
        try {
            String notificationToken = getNotificationToken();
            if (notificationToken == null) {
                throw new APIException("not_registered", "Device is not registered");
            }

            if (DEBUG) {
                Log.d(TAG, "Add subscription for channel " + subscription.getChannelId());
            }

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("appId", _appId));
            params.add(new BasicNameValuePair("notificationToken", notificationToken));
            params.add(new BasicNameValuePair("channelId", subscription.getChannelId()));

            if (subscription.getStartDate() != null) {
                params.add(new BasicNameValuePair("dateStart", DATE_FORMAT.format(subscription.getStartDate())));
            }

            if (subscription.getEndDate() != null) {
                params.add(new BasicNameValuePair("dateEnd", DATE_FORMAT.format(subscription.getEndDate())));
            }

            if (subscription.getStartTime() != null) {
                params.add(new BasicNameValuePair("timeStart", String.format("%02d:%02d", subscription.getStartTime().getHours(), subscription.getStartTime().getMinutes())));
            }

            if (subscription.getEndTime() != null) {
                params.add(new BasicNameValuePair("timeEnd", String.format("%02d:%02d", subscription.getEndTime().getHours(), subscription.getEndTime().getMinutes())));
            }

            if (subscription.getWeekdays() > 0) {
                String dowSet = "";
                if (subscription.hasWeekday(Subscription.SUNDAY)) {
                    dowSet += (dowSet.length() > 0 ? "," : "") + "1";
                }
                if (subscription.hasWeekday(Subscription.MONDAY)) {
                    dowSet += (dowSet.length() > 0 ? "," : "") + "2";
                }
                if (subscription.hasWeekday(Subscription.TUESDAY)) {
                    dowSet += (dowSet.length() > 0 ? "," : "") + "3";
                }
                if (subscription.hasWeekday(Subscription.WEDNESDAY)) {
                    dowSet += (dowSet.length() > 0 ? "," : "") + "4";
                }
                if (subscription.hasWeekday(Subscription.THURSDAY)) {
                    dowSet += (dowSet.length() > 0 ? "," : "") + "4";
                }
                if (subscription.hasWeekday(Subscription.FRIDAY)) {
                    dowSet += (dowSet.length() > 0 ? "," : "") + "6";
                }
                if (subscription.hasWeekday(Subscription.SATURDAY)) {
                    dowSet += (dowSet.length() > 0 ? "," : "") + "7";
                }

                params.add(new BasicNameValuePair("dowSet", dowSet));
            }

            HttpEntity entity = new UrlEncodedFormEntity(params);
            JSONObject result = _getAPIClient().post("subscriptions", entity);
            int id = APIUtils.getInt(result, "id", 0);
            subscription.setId(id);

            if (DEBUG) {
                Log.d(TAG, "Subscription ID: " + id);
            }
        } catch (Exception e) {
            if (DEBUG) {
                Log.e(TAG, "Error adding subscription", e);
            }

            if (!(e instanceof APIException)) {
                e = new APIException(e);
            }

            throw (APIException)e;
        }
    }

    /**
     * Unsubscribe from all subscriptions for the given channel.
     * 
     * @param channelId Channel ID.
     */
    public void unsubscribe(String channelId) throws APIException {
        String notificationToken = getNotificationToken();
        if (notificationToken == null) {
            throw new APIException("not_registered", "Device is not registered");
        }

        try {
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("appId", _appId));
            params.add(new BasicNameValuePair("notificationToken", notificationToken));
            params.add(new BasicNameValuePair("channelId", channelId));

            HttpEntity entity = new UrlEncodedFormEntity(params);
            _getAPIClient().post("subscriptions/;delete", entity);
        } catch (Exception e) {
            if (DEBUG) {
                Log.e(TAG, "Error adding subscription", e);
            }

            if (!(e instanceof APIException)) {
                e = new APIException(e);
            }

            throw (APIException)e;
        }
    }

    /**
     * Unsubscribe using the given subscription ID.
     * 
     * @param subscriptionId Subscription ID.
     */
    public void unsubscribe(int subscriptionId) throws APIException {
        String notificationToken = getNotificationToken();
        if (notificationToken == null) {
            throw new APIException("not_registered", "Device is not registered");
        }

        try {
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("appId", _appId));
            params.add(new BasicNameValuePair("notificationToken", notificationToken));
            params.add(new BasicNameValuePair("subscriptionId", String.valueOf(subscriptionId)));

            HttpEntity entity = new UrlEncodedFormEntity(params);
            _getAPIClient().post("subscriptions/;delete", entity);
        } catch (Exception e) {
            if (DEBUG) {
                Log.e(TAG, "Error adding subscription", e);
            }

            if (!(e instanceof APIException)) {
                e = new APIException(e);
            }

            throw (APIException)e;
        }
    }

    /**
     * Unsubscribe the given subscription (used the subscription ID).
     * 
     * @param subscription Subscription ID.
     */
    public void unsubscribe(Subscription subscription) throws APIException {
        if (subscription.getId() != null) {
            unsubscribe(subscription.getId());
        }
    }

    /**
     * Sets the new notificaction token.
     * 
     * @param notificationToken
     */
    private void _setNotificationToken(String notificationToken) {
        SharedPreferences.Editor editor = _context.getSharedPreferences(TAG, Context.MODE_PRIVATE).edit();
        editor.putString(_appId + "." + NOTIFICATION_TOKEN_KEY, notificationToken);
        editor.commit();
    }
}
